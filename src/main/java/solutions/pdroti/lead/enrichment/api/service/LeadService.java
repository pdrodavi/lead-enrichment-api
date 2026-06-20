package solutions.pdroti.lead.enrichment.api.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import solutions.pdroti.lead.enrichment.api.model.Lead;
import solutions.pdroti.lead.enrichment.api.repository.LeadRepository;
import solutions.pdroti.lead.enrichment.api.util.DataParser;
import solutions.pdroti.lead.enrichment.api.util.EmailUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    private final DotComScrapingService dotComScrapingService;
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
            log.info("Domínio não informado — buscará redes sociais, telefones e e-mails em sites .com/.com.br via OpenSERP");
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

        // Verifica se o novo e-mail já pertence a OUTRO lead (evita ConstraintViolation no email_hash)
        if (email != null && !email.equals(lead.getEmail())) {
            String newHash = EmailUtils.hash(email);
            leadRepository.findByEmailHash(newHash)
                    .filter(existing -> !existing.getId().equals(lead.getId()))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException(
                                "E-mail já cadastrado para outro lead: ID=" + existing.getId());
                    });
        }

        lead.setName(name);
        lead.setEmail(email);
        lead.setDomain(domain);
        lead.setUpdatedAt(LocalDateTime.now());

        return performFullEnrichment(lead, email, domain, name);
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

        // Preserva dados antigos antes de resetar — se o reenriquecimento
        // retornar vazio (ex: CAPTCHA), os dados anteriores são mantidos
        var snapshot = EnrichmentSnapshotManager.takeSnapshot(lead);

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

        // Quando nenhum domínio foi informado, busca redes sociais, telefones
        // e e-mails nos sites .com/.com.br encontrados pelo OpenSERP
        if (!StringUtils.hasText(domain)) {
            dotComScrapingService.scrapeDotComSites(lead, name);
        }

        // Filtra socialLinks para manter apenas os que correspondem ao
        // nome exato ou e-mail exato da pessoa
        List<String> filteredSocialLinks = filterSocialLinksByPerson(
                lead.getSocialLinks(), lead.getName(), lead.getEmail());
        lead.setSocialLinks(filteredSocialLinks);

        // Se o reenriquecimento não encontrou dados novos (ex: CAPTCHA),
        // restaura os dados anteriores para não perder informação
        snapshot.restoreIfEmpty(lead);

        lead.setUpdatedAt(LocalDateTime.now());

        // Transação curta — apenas o save, sem HTTP calls
        Lead savedLead = transactionTemplate.execute(status -> leadRepository.save(lead));
        log.info("Lead enriquecido: {}", logId);
        return savedLead;
    }

    /**
     * Filtra a lista de links de redes sociais para manter apenas aqueles
     * cuja URL contenha o nome exato ou o e-mail exato da pessoa.
     * <p>
     * Critérios de correspondência (case-insensitive):
     * <ul>
     *   <li>Parte local do e-mail (ex: "joao.silva" de "joao.silva@exemplo.com")</li>
     *   <li>Cada palavra do nome com 3+ caracteres (ex: "joao", "silva")</li>
     *   <li>Nome completo (ex: "joaosilva", "joao-silva", "joão silva")</li>
     * </ul>
     *
     * @param socialLinks lista original de links sociais
     * @param name        nome completo da pessoa
     * @param email       e-mail completo da pessoa
     * @return lista filtrada contendo apenas links que correspondem à pessoa
     */
    static List<String> filterSocialLinksByPerson(List<String> socialLinks, String name, String email) {
        if (socialLinks == null || socialLinks.isEmpty()) return new ArrayList<>();
        if (name == null && email == null) return new ArrayList<>();

        // Monta termos de busca a partir do nome e email
        Set<String> searchTerms = new LinkedHashSet<>();
        String lowerName = name != null ? name.toLowerCase().strip() : "";
        String lowerEmail = email != null ? email.toLowerCase().strip() : "";

        // Parte local do e-mail (ex: "joao.silva" de "joao.silva@gmail.com")
        if (lowerEmail.contains("@")) {
            String localPart = lowerEmail.substring(0, lowerEmail.indexOf("@"));
            if (!localPart.isBlank()) {
                searchTerms.add(localPart);
                // Também tenta sem pontos (ex: "joaosilva")
                searchTerms.add(localPart.replace(".", ""));
                searchTerms.add(localPart.replace("-", ""));
                searchTerms.add(localPart.replace("_", ""));
            }
        }

        // Palavras do nome com 3+ caracteres
        if (!lowerName.isBlank()) {
            // Remove acentos para comparação
            String normalized = java.text.Normalizer.normalize(lowerName, java.text.Normalizer.Form.NFD)
                    .replaceAll("[\\u0300-\\u036f]", "");
            for (String word : normalized.split("\\s+")) {
                if (word.length() >= 3) {
                    searchTerms.add(word);
                }
            }
            // Nome completo sem espaços
            String fullNameNoSpace = normalized.replaceAll("\\s+", "");
            if (fullNameNoSpace.length() >= 5) {
                searchTerms.add(fullNameNoSpace);
            }
            // Nome completo com hífen
            String fullNameHyphen = normalized.replaceAll("\\s+", "-");
            searchTerms.add(fullNameHyphen);
            // Nome completo com underline
            String fullNameUnderscore = normalized.replaceAll("\\s+", "_");
            searchTerms.add(fullNameUnderscore);
        }

        log.debug("Termos para filtro de socialLinks: {}", searchTerms);

        // Filtra URLs que contenham qualquer termo — retorna ArrayList mutável
        // para o Hibernate conseguir gerenciar o @ElementCollection
        return socialLinks.stream()
                .filter(link -> {
                    if (link == null) return false;
                    String lowerLink = link.toLowerCase();
                    return searchTerms.stream().anyMatch(lowerLink::contains);
                })
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
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
