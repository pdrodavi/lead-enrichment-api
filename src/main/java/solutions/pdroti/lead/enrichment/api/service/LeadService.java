package solutions.pdroti.lead.enrichment.api.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solutions.pdroti.lead.enrichment.api.config.RedisCacheConfig;
import solutions.pdroti.lead.enrichment.api.model.Lead;
import solutions.pdroti.lead.enrichment.api.repository.LeadRepository;
import solutions.pdroti.lead.enrichment.api.util.EmailUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leadRepository;
    private final DnsValidationService dnsValidationService;
    private final TechScraperService techScraperService;
    private final SocialDiscoveryService socialDiscoveryService;
    private final RedisCacheService redisCacheService;
    private static final int DATA_RETENTION_DAYS = 365;
    private static final String DEFAULT_STATUS = "ACTIVE";
    private static final String DELETED_STATUS = "DELETED";

    /**
     * Enriquece um lead com dados de domínio: cache → banco → enrichment completo.
     */
    @Transactional
    public Lead enrich(String email, String domain) {
        log.info("Iniciando enriquecimento para: {}", maskEmail(email));

        Lead cachedLead = getFromCache(email);
        if (cachedLead != null)
            return cachedLead;

        Lead existingLead = getFromDatabase(email);
        if (existingLead != null)
            return existingLead;

        return performFullEnrichment(email, domain);
    }

    /**
     * Enriquece um lead extraindo o domínio do e-mail.
     */
    @Transactional
    public Lead enrichLead(Lead lead) {
        String domain = extractDomainFromEmail(lead.getEmail());
        return enrich(lead.getEmail(), domain);
    }

    /**
     * Retorna todos os leads cadastrados.
     */
    public List<Lead> listAll() {
        return leadRepository.findAll();
    }

    /**
     * Busca lead por ID string (convertido internamente para Long).
     */
    public Optional<Lead> findById(String id) {
        return parseNumericId(id).flatMap(leadRepository::findById);
    }

    /**
     * Soft delete: marca data/hora de exclusão e altera status.
     */
    @Transactional
    @CacheEvict(value = RedisCacheConfig.CACHE_LEADS, key = "#id")
    public boolean softDelete(String id) {
        return parseNumericId(id)
                .flatMap(leadRepository::findById)
                .map(this::performSoftDelete)
                .orElse(false);
    }

    /**
     * Hard delete: remove fisicamente do banco e do cache.
     */
    @Transactional
    @CacheEvict(value = RedisCacheConfig.CACHE_LEADS, key = "#id")
    public boolean hardDelete(String id) {
        return parseNumericId(id)
                .filter(leadRepository::existsById)
                .map(this::performHardDelete)
                .orElse(false);
    }

    // ==================== Métodos Privados ====================

    /** Tenta obter lead do cache Redis (retorno com type-safe). */
    private Lead getFromCache(String email) {
        return redisCacheService.get(email)
                .filter(Lead.class::isInstance)
                .map(Lead.class::cast)
                .map(lead -> {
                    log.info("Cache hit para: {}", maskEmail(email));
                    return lead;
                })
                .orElse(null);
    }

    /** Busca lead no banco se já estiver enriquecido e atualiza cache. */
    private Lead getFromDatabase(String email) {
        return leadRepository.findByEmail(email)
                .filter(this::isLeadAlreadyEnriched)
                .map(lead -> {
                    log.info("Lead existente no DB: {}", maskEmail(email));
                    initializeCollections(lead);
                    redisCacheService.put(email, lead);
                    return lead;
                })
                .orElse(null);
    }

    /** Executa todas as etapas de enrichment e persiste o resultado. */
    private Lead performFullEnrichment(String email, String domain) {
        Lead lead = createNewLead(email, domain);

        executeSafely(() -> dnsValidationService.hasMxRecord(domain),
                lead::setMxStatus, email);

        executeSafely(() -> techScraperService.scrapeTechnologies(domain),
                lead::setTechnologies, email,
                List.of("TechScrapeError"));

        executeSafely(() -> socialDiscoveryService.discoverSocialLinks(domain),
                links -> lead.setSocialLinks(links), email,
                List.<String>of());

        // Enriquecimento adicional com Google Dorks
        enrichWithDorks(lead, domain);

        Lead savedLead = leadRepository.save(lead);
        initializeCollections(savedLead);
        redisCacheService.put(email, savedLead);

        log.info("Lead enriquecido: {}", maskEmail(email));
        return savedLead;
    }

    /** Enriquece com dados de Google Dorks (emails, docs expostos, info pública). */
    private void enrichWithDorks(Lead lead, String domain) {
        try {
            var dorkResult = techScraperService.scanDorks(domain);
            lead.setExposedEmails(dorkResult.exposedEmails());
            lead.setExposedPhones(dorkResult.exposedPhones());
            lead.setExposedAdminPaths(dorkResult.exposedAdminPaths());
            lead.setExposedDocuments(dorkResult.exposedDocuments());
            lead.setExposedConfigFiles(dorkResult.exposedConfigFiles());
            lead.setDorkFindings(dorkResult.totalFindings());
            if (dorkResult.totalFindings() > 0) {
                log.info("Dorks encontrou {} itens para {}", dorkResult.totalFindings(), domain);
            }
        } catch (Exception e) {
            log.debug("Dorks scan ignorado para {}: {}", domain, e.getMessage());
        }
    }

    /**
     * Verifica se o lead já passou por enrichment (tecnologias, social links
     * ou dorks presentes).
     */
    private boolean isLeadAlreadyEnriched(Lead lead) {
        return hasData(lead.getTechnologies())
                || hasData(lead.getSocialLinks())
                || hasData(lead.getExposedEmails());
    }

    private static boolean hasData(List<?> list) {
        return list != null && !list.isEmpty();
    }

    /** Cria lead com valores padrão (consentimento, retenção LGPD, status). */
    private Lead createNewLead(String email, String domain) {
        LocalDateTime now = LocalDateTime.now();
        return Lead.builder()
                .email(email).domain(domain)
                .consentGiven(true).consentDate(now)
                .dataRetentionUntil(now.plusDays(DATA_RETENTION_DAYS))
                .createdAt(now).status(DEFAULT_STATUS)
                .build();
    }

    /** Marca o lead como deletado (soft delete) e limpa cache. */
    private boolean performSoftDelete(Lead lead) {
        lead.setDeletedAt(LocalDateTime.now());
        lead.setStatus(DELETED_STATUS);
        leadRepository.save(lead);
        redisCacheService.evict(lead.getEmail());
        log.info("Lead soft deleted: ID={}", lead.getId());
        return true;
    }

    /** Remove lead do banco pelo ID (hard delete) e limpa cache. */
    private boolean performHardDelete(Long id) {
        var lead = leadRepository.findById(id);
        if (lead.isEmpty()) {
            log.warn("Lead não encontrado para hard delete: ID={}", id);
            return false;
        }
        redisCacheService.evict(lead.get().getEmail());
        leadRepository.deleteById(id);
        log.info("Lead hard deleted: ID={}", id);
        return true;
    }

    /**
     * Executa um supplier e aplica o resultado via setter com fallback em caso de
     * erro.
     */
    private <T> void executeSafely(Supplier<T> supplier, Consumer<T> setter, String email) {
        executeSafely(supplier, setter, email, null);
    }

    /**
     * Executa um supplier e aplica o resultado via setter com fallback em caso de
     * erro.
     */
    private <T> void executeSafely(Supplier<T> supplier, Consumer<T> setter, String email, T fallback) {
        try {
            T result = supplier.get();
            setter.accept(result != null ? result : fallback);
        } catch (Exception e) {
            log.warn("Erro para {}: {}", maskEmail(email), e.getMessage());
            if (fallback != null) {
                setter.accept(fallback);
            }
        }
    }

    /**
     * Substitui coleções gerenciadas pelo Hibernate por ArrayList comuns,
     * evitando proxies Hibernate durante serialização JSON para o Redis.
     */
    private void initializeCollections(Lead lead) {
        lead.setTechnologies(copyList(lead.getTechnologies()));
        lead.setSocialLinks(copyList(lead.getSocialLinks()));
        lead.setExposedEmails(copyList(lead.getExposedEmails()));
        lead.setExposedPhones(copyList(lead.getExposedPhones()));
        lead.setExposedAdminPaths(copyList(lead.getExposedAdminPaths()));
        lead.setExposedDocuments(copyList(lead.getExposedDocuments()));
        lead.setExposedConfigFiles(copyList(lead.getExposedConfigFiles()));
    }

    private static <T> List<T> copyList(List<T> list) {
        return list != null ? new java.util.ArrayList<>(list) : java.util.Collections.emptyList();
    }

    /** Extrai domínio do e-mail (parte após @). */
    private String extractDomainFromEmail(String email) {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Email inválido: " + email);
        }
        return email.substring(email.indexOf("@") + 1);
    }

    /** Converte ID string para Long com log em caso de formato inválido. */
    private Optional<Long> parseNumericId(String id) {
        try {
            return Optional.of(Long.parseLong(id));
        } catch (NumberFormatException e) {
            log.warn("ID inválido: {}", id);
            return Optional.empty();
        }
    }

    /** Ofusca e-mail para logging (delega ao utilitário). */
    private String maskEmail(String email) {
        return EmailUtils.mask(email);
    }
}