package solutions.pdroti.lead.enrichment.api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import solutions.pdroti.lead.enrichment.api.model.Lead;
import solutions.pdroti.lead.enrichment.api.repository.LeadRepository;
import solutions.pdroti.lead.enrichment.api.util.DataParser;
import solutions.pdroti.lead.enrichment.api.util.EmailUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orquestrador do pipeline de enriquecimento de leads.
 * <p>
 * Coordena as fontes de dados delegando para serviços especializados:
 * <ul>
 *   <li>{@link OpenSerpEnricherService} — busca no Google pelo nome da pessoa</li>
 *   <li>{@link DomainEnricherService} — DNS, RDAP, scraping, redes sociais</li>
 *   <li>{@link LeadDeletionService} — exclusão de registros</li>
 * </ul>
 * <p>
 * Otimização: OpenSERP e Domain enrichment executam em paralelo via
 * {@link CompletableFuture} com pool de Virtual Threads dedicado,
 * reduzindo o tempo total pela duração do mais lento.
 * <p>
 * <b>Merge seguro:</b> os campos compartilhados entre
 * {@link OpenSerpEnricherService} e {@link DomainEnricherService}
 * usam {@code LinkedHashSet} para evitar race conditions na escrita
 * paralela.
 * <p>
 * <b>Cache:</b>
 * <ul>
 *   <li>DNS, tecnologias, links sociais, RDAP, perfis sociais — Caffeine (1h)</li>
 *   <li>OpenSERP — Caffeine (30min) + Redis L2 (30min)</li>
 *   <li>Enrich endpoint — {@code @Cacheable} (24h)</li>
 * </ul>
 * <p>
 * <b>Domínios pessoais:</b> provedores como gmail.com, outlook.com
 * têm o enriquecimento de domínio pulado (apenas OpenSERP roda).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leadRepository;
    private final OpenSerpEnricherService openSerpEnricherService;
    private final DomainEnricherService domainEnricher;
    private final TransactionTemplate transactionTemplate;

    @Qualifier("enrichmentExecutor")
    private final Executor enrichmentExecutor;

    private static final int DATA_RETENTION_DAYS = 365;
    static final String DEFAULT_STATUS = "ACTIVE";

    /**
     * Resultado do enriquecimento com os leads do mesmo domínio,
     * evitando uma segunda consulta ao banco no controller.
     */
    public record EnrichResult(Lead enriched, List<Lead> domainLeads) {}

    /**
     * Enriquece um lead com dados públicos e retorna também os leads do mesmo domínio.
     *
     * @param email  e-mail do lead (obrigatório — identificador único)
     * @param domain domínio para enriquecimento (opcional, extraído do e-mail se ausente)
     * @param name   nome da pessoa (obrigatório)
     * @return {@link EnrichResult} com lead persistido + leads do mesmo domínio
     */
    public EnrichResult enrichWithDomainLeads(String email, String domain, String name) {
        Lead enriched = enrich(email, domain, name);
        String d = enriched.getDomain();
        List<Lead> domainLeads = (d != null && !d.isBlank())
                ? leadRepository.findByDomainAndStatus(d, DEFAULT_STATUS)
                : List.of();
        return new EnrichResult(enriched, domainLeads);
    }

    /**
     * Enriquece um lead com dados públicos.
     *
     * @param email  e-mail do lead (obrigatório — identificador único)
     * @param domain domínio para enriquecimento (opcional, extraído do e-mail se ausente)
     * @param name   nome da pessoa (obrigatório)
     * @return lead persistido com dados enriquecidos
     */
    public Lead enrich(String email, String domain, String name) {
        log.info("Enriquecendo lead: nome={} email={} domain={}", name, EmailUtils.mask(email), domain);

        if (domain == null) {
            domain = DataParser.extractDomainFromEmail(email);
            log.info("Domínio extraído do e-mail: {}", domain);
        }

        Lead existing = leadRepository.findByEmailHash(EmailUtils.hash(email)).orElse(null);
        if (existing != null) {
            log.debug("Lead já existe, reenriquecendo: ID={}", existing.getId());
        }

        return performFullEnrichment(existing, email, domain, name);
    }

    /**
     * Retorna todos os leads com status ACTIVE (paginado).
     *
     * @param pageable parâmetros de paginação (page, size, sort)
     * @return página de leads ativos
     */
    @Transactional(readOnly = true)
    public Page<Lead> listAll(Pageable pageable) {
        return leadRepository.findByStatus(DEFAULT_STATUS, pageable);
    }

    /**
     * Retorna todos os leads ativos que pertencem ao domínio informado (paginado).
     *
     * @param domain   domínio para filtrar (ex: "exemplo.com")
     * @param pageable parâmetros de paginação (page, size, sort)
     * @return página de leads do domínio, ou página vazia se domain for inválido
     */
    @Transactional(readOnly = true)
    public Page<Lead> findByDomain(String domain, Pageable pageable) {
        if (!StringUtils.hasText(domain)) return Page.empty();
        return leadRepository.findByDomainAndStatus(domain, DEFAULT_STATUS, pageable);
    }

    /**
     * Busca um lead pelo ID.
     * Retorna apenas se o lead estiver ativo (status != DELETED).
     *
     * @param id identificador do lead em formato string
     * @return Optional com o lead encontrado, ou vazio se não existir ou estiver deletado
     */
    @Transactional(readOnly = true)
    public Optional<Lead> findById(String id) {
        return LeadDeletionService.parseNumericId(id)
                .flatMap(leadRepository::findById)
                .filter(lead -> !LeadDeletionService.DELETED_STATUS.equals(lead.getStatus()));
    }

    /**
     * Atualiza os dados de um lead existente e reenriquece.
     *
     * @param id     ID do lead a ser atualizado
     * @param email  novo e-mail (pode ser null)
     * @param domain novo domínio (pode ser null, extraído do e-mail se ausente)
     * @param name   novo nome
     * @return lead atualizado e reenriquecido
     * @throws IllegalArgumentException se o ID não existir
     */
    public Lead update(String id, String email, String domain, String name) {
        Lead lead = findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lead não encontrado: " + id));

        lead.setName(name);
        lead.setEmail(email);
        lead.setDomain(domain != null ? domain : DataParser.extractDomainFromEmail(email));
        lead.setUpdatedAt(LocalDateTime.now());

        return performFullEnrichment(lead, email, lead.getDomain(), name);
    }

    // ==================== Métodos Privados ====================

    /**
     * Executa o pipeline completo de enrichment delegando para
     * {@link OpenSerpEnricherService} e {@link DomainEnricherService}, e persiste o resultado.
     */
    private Lead performFullEnrichment(Lead existing, String email, String domain, String name) {
        Lead lead = existing != null ? existing : createNewLead(email, domain, name);
        lead.setEmail(email);
        lead.setDomain(domain);
        lead.setName(name);
        if (lead.getCreatedAt() == null) {
            lead.setCreatedAt(LocalDateTime.now());
        }

        String logId = EmailUtils.mask(email);
        if (logId == null) logId = name;

        domainEnricher.resetEnrichmentData(lead);

        // OpenSERP e domínio executam em PARALELO via CompletableFuture
        // com pool de threads dedicado (enrichmentExecutor)
        CompletableFuture<Void> openSerpFuture = CompletableFuture.runAsync(() ->
                openSerpEnricherService.enrich(lead, name), enrichmentExecutor);

        // Para domínios de provedores pessoais (gmail.com, outlook.com, etc.),
        // o enriquecimento de domínio é pulado — não faz sentido consultar
        // DNS/RDAP/TechScraper do provedor, pois os dados seriam dele, não do lead.
        boolean isPersonalDomain = DataParser.isPersonalEmailDomain(domain);
        CompletableFuture<Void> domainFuture = StringUtils.hasText(domain) && !isPersonalDomain
                ? CompletableFuture.runAsync(() -> domainEnricher.enrich(lead, domain, name), enrichmentExecutor)
                : CompletableFuture.completedFuture(null);
        if (isPersonalDomain) {
            log.info("Domínio pessoal '{}' — pulando DomainEnricherService", domain);
        }

        // Aguarda ambos finalizarem com timeout de 2 minutos
        CompletableFuture.allOf(openSerpFuture, domainFuture)
                .orTimeout(2, TimeUnit.MINUTES)
                .join();

        lead.setUpdatedAt(LocalDateTime.now());

        // Transação curta — apenas o save, sem HTTP calls
        Lead savedLead = transactionTemplate.execute(status -> leadRepository.save(lead));
        log.info("Lead enriquecido: {}", logId);
        return savedLead;
    }

    private Lead createNewLead(String email, String domain, String name) {
        LocalDateTime now = LocalDateTime.now();
        return Lead.builder()
                .email(email).domain(domain).name(name)
                .consentGiven(true).consentDate(now)
                .dataRetentionUntil(now.plusDays(DATA_RETENTION_DAYS))
                .createdAt(now).status(DEFAULT_STATUS)
                .build();
    }

}
