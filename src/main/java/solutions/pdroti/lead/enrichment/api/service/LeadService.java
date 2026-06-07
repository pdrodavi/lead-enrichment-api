package solutions.pdroti.lead.enrichment.api.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solutions.pdroti.lead.enrichment.api.config.RedisCacheConfig;
import solutions.pdroti.lead.enrichment.api.model.Lead;
import solutions.pdroti.lead.enrichment.api.repository.LeadRepository;

import java.util.List;
import java.util.Optional;
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

    /**
     * Ponto de entrada para enriquecimento de um lead.
     * Segue a estratégia Cache-Aside.
     */
    @Transactional
    public Lead enrichLead(Lead lead) {
        log.info("Iniciando enriquecimento para o lead: {}", lead.getEmail());

        // 1. Tentar recuperar do Cache (Redis)
        Optional<Object> cachedLead = redisCacheService.get(lead.getEmail());
        if (cachedLead.isPresent()) {
            log.info("Lead encontrado no cache: {}", lead.getEmail());
            return (Lead) cachedLead.get();
        }

        // 2. Tentar recuperar do Banco de Dados (PostgreSQL)
        Optional<Lead> existingLead = leadRepository.findByEmail(lead.getEmail());
        if (existingLead.isPresent() && isDataFresh(existingLead.get())) {
            Lead dbLead = existingLead.get();
            redisCacheService.put(dbLead.getEmail(), dbLead);
            return dbLead;
        }

        // 3. Processamento de Enriquecimento (DNS + Scraping)
        return performFullEnrichment(lead);
    }

    private Lead performFullEnrichment(Lead lead) {
        String domain = extractDomain(lead.getEmail());
        lead.setDomain(domain);

        // Validação de DNS (MX Record)
        boolean hasMx = dnsValidationService.hasMxRecord(domain);
        lead.setMxStatus(hasMx);

        if (hasMx) {
            // Se o domínio for válido, realiza o scraping de tecnologias e redes sociais
            Lead enrichedData = (Lead) techScraperService.scrape(domain);
            lead.setTechnologies(enrichedData.getTechnologies());
            lead.setSocialLinks(enrichedData.getSocialLinks());
        }

        // Salvar no Banco e no Cache
        Lead savedLead = leadRepository.save(lead);
        redisCacheService.put(savedLead.getEmail(), savedLead);
        
        log.info("Lead enriquecido e salvo com sucesso: {}", lead.getEmail());
        return savedLead;
    }

    private String extractDomain(String email) {
        return email.substring(email.indexOf("@") + 1);
    }

    private boolean isDataFresh(Lead lead) {
        // Lógica simples: dados com menos de 30 dias são considerados "frescos"
        // Implementar conforme necessidade de negócio
        return true; 
    }

    @SuppressWarnings("unchecked")
    @Transactional 
    public Lead enrichLead(String email) {
        
        var cachedLead = redisCacheService.get(email);
        
        if (cachedLead.isPresent()) {
            return (Lead) cachedLead.get();
        }

        var lead = Lead.builder().email(email).build();
        
        if (dnsValidationService.hasMxRecord(email)) {
            try {
                var techData = techScraperService.scrapeTechnologies(email);
                var socialData = socialDiscoveryService.discoverSocialLinks(email);
                lead.setTechnologies(techData);
                lead.setSocialLinks(socialData);
            } catch (Exception e) {
                lead.setScrapingError(true);
            }
        }

        var savedLead = leadRepository.save(lead);
        redisCacheService.put(email, savedLead);
        return savedLead;
    }

    @Transactional
    public Lead enrich(String email, String domain) {

        Optional<Object> cachedLead = redisCacheService.get(email);
        
        if (cachedLead.isPresent()) {
            return (Lead) cachedLead.get();
        }

        // Check cache first — if already enriched, return cached version
        // assert findByEmail(email) != null;
        Optional<Lead> existing = Optional.empty();
        if (leadRepository.findByEmail(email).isPresent()) {
            existing = leadRepository.findById(leadRepository.findByEmail(email).get().getId());
        }

        if (existing.isPresent() && existing.get().getTechnologies() != null) {
            return existing.get();
        }

        // Build and enrich a new Lead
        Lead lead = Lead.builder()
                .email(email)
                .domain(domain)
                .build();

        // Step 1: MX validation
        boolean mxValid = dnsValidationService.hasMxRecord(domain);
        lead.setMxStatus(mxValid);

        // Step 2: Scrape technologies
        try {
            List<String> technologies = techScraperService.scrapeTechnologies(domain);
            lead.setTechnologies(technologies);
        } catch (Exception e) {
            lead.setTechnologies(List.of("TechScrapeError: " + e.getMessage()));
        }

        // Step 3: Discover social links
        try {
            List<String> socialLinks = socialDiscoveryService.discoverSocialLinks(domain);
            lead.setSocialLinks(socialLinks);
        } catch (Exception e) {
            lead.setSocialLinks(List.of());
        }

        var savedLead = leadRepository.save(lead);
        redisCacheService.put(email, savedLead);
        return savedLead;
    }

    @Cacheable(value = RedisCacheConfig.CACHE_LEADS, key = "#id", unless = "#result == null")
    public Optional<Lead> findById(String id) {
        try {
            Long numericId = Long.parseLong(id);
            return leadRepository.findById(numericId);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Long findByEmail(String email) {
        // Fallback: returns null so we always create a new record;
        // a real implementation would query the database.
        return null;
    }
}
