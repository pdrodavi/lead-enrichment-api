package solutions.pdroti.lead.enrichment.api.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import solutions.pdroti.lead.enrichment.api.config.RedisCacheConfig;
import org.springframework.transaction.annotation.Transactional;
import solutions.pdroti.lead.enrichment.api.model.Lead;
import solutions.pdroti.lead.enrichment.api.repository.LeadRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;

@Service
@Slf4j
@RequiredArgsConstructor
public class LeadService2 {

    private final LeadRepository leadRepository;
    private final DnsValidationService dnsValidationService;
    private final TechScraperService techScraperService;
    private final SocialDiscoveryService socialDiscoveryService;
    private final RedisCacheService redisCacheService;

    @Transactional
    public Lead enrich(String email, String domain) {
        log.info("Iniciando enriquecimento para: {}", maskEmail(email));

        // 1. Cache check (Redis)
        Optional<Object> cached = redisCacheService.get(email);
        if (cached.isPresent()) {
            log.info("Cache hit para: {}", maskEmail(email));
            return (Lead) cached.get();
        }

        // 2. DB check — se já enriquecido, retorna
        Optional<Lead> existing = leadRepository.findByEmail(email);
        if (existing.isPresent() && existing.get().getTechnologies() != null && !existing.get().getTechnologies().isEmpty()) {
            log.info("Lead existente encontrado no DB: {}", maskEmail(email));
            Lead dbLead = existing.get();
            Hibernate.initialize(dbLead.getTechnologies());
            Hibernate.initialize(dbLead.getSocialLinks());
            redisCacheService.put(email, dbLead);
            return dbLead;
        }

        // 3. Full enrichment
        Lead lead = Lead.builder()
                .email(email)
                .domain(domain)
                .consentGiven(true)
                .consentDate(LocalDateTime.now())
                .dataRetentionUntil(LocalDateTime.now().plusDays(365))
                .createdAt(LocalDateTime.now())
                .build();

        boolean mxValid = dnsValidationService.hasMxRecord(domain);
        lead.setMxStatus(mxValid);

        try {
            List<String> technologies = techScraperService.scrapeTechnologies(domain);
            lead.setTechnologies(technologies);
        } catch (Exception e) {
            log.warn("Erro ao fazer scrape de tecnologias para {}: {}", maskEmail(email), e.getMessage());
            lead.setTechnologies(List.of("TechScrapeError: " + e.getMessage()));
        }

        try {
            List<String> socialLinks = socialDiscoveryService.discoverSocialLinks(domain);
            lead.setSocialLinks(socialLinks);
        } catch (Exception e) {
            log.warn("Erro ao descobrir redes sociais para {}: {}", maskEmail(email), e.getMessage());
            lead.setSocialLinks(List.of());
        }

        Lead savedLead = leadRepository.save(lead);
        Hibernate.initialize(savedLead.getTechnologies());
        Hibernate.initialize(savedLead.getSocialLinks());
        redisCacheService.put(email, savedLead);
        log.info("Lead enriquecido e salvo com sucesso: {}", maskEmail(email));
        return savedLead;
    }

    /**
     * Usado pelo Spring Batch para processar leads pendentes.
     */
    @Transactional
    public Lead enrichLead(Lead lead) {
        String domain = lead.getEmail().substring(lead.getEmail().indexOf("@") + 1);
        return enrich(lead.getEmail(), domain);
    }

    public Optional<Lead> findById(String id) {
        try {
            Long numericId = Long.parseLong(id);
            return leadRepository.findById(numericId);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Exclusão lógica (LGPD — direito ao esquecimento).
     * Marca o lead como excluído em vez de remover fisicamente.
     */
    @Transactional
    @CacheEvict(value = RedisCacheConfig.CACHE_LEADS, key = "#id")
    public boolean softDelete(String id) {
        try {
            Long numericId = Long.parseLong(id);
            return leadRepository.findById(numericId).map(lead -> {
                lead.setDeletedAt(LocalDateTime.now());
                lead.setStatus("DELETED");
                leadRepository.save(lead);
                redisCacheService.evict(lead.getEmail());
                log.info("Lead excluído (lógico): ID={}", numericId);
                return true;
            }).orElse(false);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Remove fisicamente do banco (apenas para compliance total).
     */
    @Transactional
    @CacheEvict(value = RedisCacheConfig.CACHE_LEADS, key = "#id")
    public boolean hardDelete(String id) {
        try {
            Long numericId = Long.parseLong(id);
            if (leadRepository.existsById(numericId)) {
                leadRepository.findById(numericId).ifPresent(lead ->
                        redisCacheService.evict(lead.getEmail()));
                leadRepository.deleteById(numericId);
                log.info("Lead excluído (físico): ID={}", numericId);
                return true;
            }
            return false;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Sanitização de email para logs (LGPD — evitar expor PII).
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        int atIndex = email.indexOf("@");
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (localPart.length() <= 3) {
            return localPart.charAt(0) + "***" + domain;
        }
        return localPart.substring(0, 3) + "***" + domain;
    }
}
