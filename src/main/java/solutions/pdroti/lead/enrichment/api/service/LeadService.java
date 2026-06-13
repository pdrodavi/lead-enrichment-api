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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orquestrador do pipeline de enriquecimento de leads.
 * <p>
 * Coordena as fontes de dados delegando para serviços especializados:
 * <ul>
 *   <li>{@link OpenSerpEnricher} — busca no Google pelo nome da pessoa</li>
 *   <li>{@link DomainEnricher} — DNS, RDAP, scraping, redes sociais</li>
 *   <li>{@link LeadDeletionService} — exclusão de registros</li>
 * </ul>
 * <p>
 * Otimização: OpenSERP e Domain enrichment executam em paralelo via
 * {@link CompletableFuture}, reduzindo o tempo total pela duração do mais lento.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leadRepository;
    private final OpenSerpEnricher openSerpEnricher;
    private final DomainEnricher domainEnricher;

    private static final int DATA_RETENTION_DAYS = 365;
    static final String DEFAULT_STATUS = "ACTIVE";

    /**
     * Enriquece um lead com dados públicos.
     *
     * @param email  e-mail do lead (obrigatório — identificador único)
     * @param domain domínio para enriquecimento (opcional, extraído do e-mail se ausente)
     * @param name   nome da pessoa (obrigatório)
     * @return lead persistido com dados enriquecidos
     */
    @Transactional
    public Lead enrich(String email, String domain, String name) {
        log.info("Enriquecendo lead: nome={} email={} domain={}", name, EmailUtils.mask(email), domain);

        if (domain == null) {
            domain = DataParser.extractDomainFromEmail(email);
            log.info("Domínio extraído do e-mail: {}", domain);
        }

        Lead existing = leadRepository.findByEmailHash(EmailUtils.hash(email)).orElse(null);
        if (existing != null) {
            log.info("Lead já existe, reenriquecendo: ID={}", existing.getId());
        }

        return performFullEnrichment(existing, email, domain, name);
    }

    /**
     * Retorna todos os leads com status ACTIVE.
     *
     * @return lista de leads ativos
     */
    public List<Lead> listAll() {
        return leadRepository.findByStatus(DEFAULT_STATUS);
    }

    /**
     * Retorna todos os leads ativos que pertencem ao domínio informado.
     *
     * @param domain domínio para filtrar (ex: "exemplo.com")
     * @return lista de leads do domínio, ou lista vazia se domain for inválido
     */
    public List<Lead> findByDomain(String domain) {
        if (!StringUtils.hasText(domain)) return List.of();
        return leadRepository.findByDomainAndStatus(domain, DEFAULT_STATUS);
    }

    /**
     * Busca um lead pelo ID.
     * Retorna apenas se o lead estiver ativo (status != DELETED).
     *
     * @param id identificador do lead em formato string
     * @return Optional com o lead encontrado, ou vazio se não existir ou estiver deletado
     */
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
    @Transactional
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
     * {@link OpenSerpEnricher} e {@link DomainEnricher}, e persiste o resultado.
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
        CompletableFuture<Void> openSerpFuture = CompletableFuture.runAsync(() ->
                openSerpEnricher.enrich(lead, name));

        CompletableFuture<Void> domainFuture = StringUtils.hasText(domain)
                ? CompletableFuture.runAsync(() -> domainEnricher.enrich(lead, domain, name))
                : CompletableFuture.completedFuture(null);

        // Aguarda ambos finalizarem
        CompletableFuture.allOf(openSerpFuture, domainFuture).join();

        lead.setUpdatedAt(LocalDateTime.now());

        Lead savedLead = leadRepository.save(lead);
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
