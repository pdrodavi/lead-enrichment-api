package solutions.pdroti.lead.enrichment.api.service;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solutions.pdroti.lead.enrichment.api.dto.RdapData;
import solutions.pdroti.lead.enrichment.api.model.Lead;
import solutions.pdroti.lead.enrichment.api.util.DataParser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Responsável pelo enriquecimento de leads com dados de domínio:
 * DNS, RDAP, scraping de tecnologias e descoberta de redes sociais.
 * <p>
 * Extraído do {@code LeadService} para manter a responsabilidade única (SRP).
 * <p>
 * Otimizações:
 * <ul>
 *   <li>Resultados de tecnologias cacheados via Caffeine (1h)</li>
 *   <li>Cache RDAP via Caffeine (1h)</li>
 *   <li>Links sociais cacheados via Caffeine (1h)</li>
 *   <li>Dados de perfil social cacheados via Caffeine (1h)</li>
 *   <li>Merge seguro com {@link OpenSerpEnricherService} via {@code LinkedHashSet}</li>
 * </ul>
 */
@Slf4j
@Service
public class DomainEnricherService {

    private final DnsValidationService dnsValidationService;
    private final TechScraperService techScraperService;
    private final SocialDiscoveryService socialDiscoveryService;
    private final RdapService rdapService;
    private final Cache<String, List<String>> techCache;

    public DomainEnricherService(DnsValidationService dnsValidationService,
                           TechScraperService techScraperService,
                           SocialDiscoveryService socialDiscoveryService,
                           RdapService rdapService,
                           Cache<String, List<String>> techCache) {
        this.dnsValidationService = dnsValidationService;
        this.techScraperService = techScraperService;
        this.socialDiscoveryService = socialDiscoveryService;
        this.rdapService = rdapService;
        this.techCache = techCache;
    }

    /**
     * Reseta todos os campos de enriquecimento para valores padrão.
     *
     * @param lead entidade a ser limpa
     */
    public void resetEnrichmentData(Lead lead) {
        lead.setMxStatus(false);
        lead.setDnsMxRecords(new ArrayList<>());
        lead.setDnsARecords(new ArrayList<>());
        lead.setDnsAaaaRecords(new ArrayList<>());
        lead.setDnsCnameRecords(new ArrayList<>());
        lead.setDnsTxtRecords(new ArrayList<>());
        lead.setTechnologies(new ArrayList<>());
        lead.setSocialLinks(new ArrayList<>());
        lead.setSocialProfileSummaries(new ArrayList<>());
        lead.setExposedEmails(new ArrayList<>());
        lead.setExposedPhones(new ArrayList<>());
        lead.setNameMentions(new ArrayList<>());
        lead.setDorkFindings(0);
        lead.setOpenSerpRawData(null);
        lead.setRdapRawData(null);
        lead.setRdapRegistrar(null);
        lead.setRdapRegistrantName(null);
        lead.setRdapRegistrantEmail(null);
        lead.setRdapRegistrationDate(null);
        lead.setRdapExpirationDate(null);
        lead.setRdapNameservers(new ArrayList<>());
        lead.setRdapStatus(new ArrayList<>());
        lead.setRdapTaxpayerId(null);
        lead.setRdapSource(null);
        lead.setFoundDocuments(new ArrayList<>());
        lead.setDiscoveredUrls(new ArrayList<>());
    }

    /**
     * Enriquece o lead utilizando o domínio: consultas DNS, RDAP, scraping,
     * redes sociais e verificação de nome na página.
     *
     * @param lead   entidade a ser enriquecida
     * @param domain domínio para consulta
     * @param name   nome para verificação na página
     */
    public void enrich(Lead lead, String domain, String name) {
        // 1. Consulta DNS completa (MX, A, AAAA, CNAME, TXT)
        executeSafely(() -> dnsValidationService.lookupDomain(domain),
                result -> {
                    if (result != null) {
                        lead.setMxStatus(result.hasMx());
                        lead.setDnsMxRecords(DataParser.toMutable(result.mxRecords()));
                        lead.setDnsARecords(DataParser.toMutable(result.aRecords()));
                        lead.setDnsAaaaRecords(DataParser.toMutable(result.aaaaRecords()));
                        lead.setDnsCnameRecords(DataParser.toMutable(result.cnameRecords()));
                        lead.setDnsTxtRecords(DataParser.toMutable(result.txtRecords()));
                    }
                }, name);

        // 2. Consulta RDAP (dados de registro do domínio)
        enrichRdap(lead, domain);

        // 3. Scraping de tecnologias + verificação de nome (UMA requisição HTTP)
        // Resultado é cacheado via Caffeine (1h)
        executeSafely(
                () -> {
                    String cacheKey = domain.toLowerCase().strip();
                    var cached = techCache.getIfPresent(cacheKey);
                    if (cached != null) {
                        log.debug("Tech cache hit para {}", domain);
                        return new TechScraperService.ScrapeResult(cached, List.of());
                    }
                    var result = techScraperService.scrapeTechnologiesAndCheckName(domain, name);
                    if (result != null) {
                        techCache.put(cacheKey, result.technologies());
                    }
                    return result;
                },
                result -> {
                    if (result != null) {
                        lead.setTechnologies(new ArrayList<>(result.technologies()));
                        List<String> existingMentions = lead.getNameMentions() != null
                                ? lead.getNameMentions() : new ArrayList<>();
                        Set<String> mergedMentions = new LinkedHashSet<>(existingMentions);
                        mergedMentions.addAll(result.nameMentions());
                        lead.setNameMentions(new ArrayList<>(mergedMentions));

                        boolean nameFound = result.nameMentions().stream()
                                .anyMatch(m -> m.startsWith("Nome completo encontrado"));
                        if (!nameFound) {
                            log.warn("Nome '{}' não encontrado no HTML do domínio {}", name, domain);
                        }
                    }
                }, name);

        // 4. Descoberta de redes sociais — mescla com links vindos do OpenSERP
        List<String> domainSocialLinks = discoverSocialLinksSafely(domain);
        Set<String> mergedSocialLinks = new LinkedHashSet<>(lead.getSocialLinks() != null
                ? lead.getSocialLinks() : List.of());
        mergedSocialLinks.addAll(domainSocialLinks);
        lead.setSocialLinks(new ArrayList<>(mergedSocialLinks));

        // 5. Scraping de perfis sociais (apenas se encontrou links no domínio)
        if (!domainSocialLinks.isEmpty()) {
            List<String> profiles = socialDiscoveryService.scrapeSocialProfiles(domainSocialLinks)
                    .stream().map(p -> p.toSummary()).toList();
            List<String> existingSummaries = lead.getSocialProfileSummaries() != null
                    ? lead.getSocialProfileSummaries() : List.of();
            Set<String> mergedSummaries = new LinkedHashSet<>(existingSummaries);
            mergedSummaries.addAll(profiles);
            lead.setSocialProfileSummaries(new ArrayList<>(mergedSummaries));
        }
    }

    /**
     * Enriquece o lead com dados RDAP do domínio (registrar, titular, datas, nameservers).
     */
    private void enrichRdap(Lead lead, String domain) {
        RdapData rdap = rdapService.lookup(domain);
        if (rdap.rawJson() == null) return;

        lead.setRdapRawData(rdap.rawJson().toString());
        lead.setRdapRegistrar(rdap.registrar());
        lead.setRdapRegistrantName(rdap.registrantName());
        lead.setRdapRegistrantEmail(rdap.registrantEmail());
        lead.setRdapRegistrationDate(DataParser.parseIsoDate(rdap.registrationDate()));
        lead.setRdapExpirationDate(DataParser.parseIsoDate(rdap.expirationDate()));
        lead.setRdapNameservers(DataParser.toMutable(rdap.nameservers()));
        lead.setRdapStatus(DataParser.toMutable(rdap.status()));
        lead.setRdapTaxpayerId(rdap.taxpayerId());
        lead.setRdapSource(rdap.source());

        log.debug("RDAP para {}: registrar={}, registrant={}",
                domain, rdap.registrar(), rdap.registrantName());
    }

    /**
     * Executa um Supplier com try-catch, aplicando o resultado via Consumer.
     * Sobrecarga sem fallback — usa null como fallback padrão.
     */
    private <T> void executeSafely(Supplier<T> supplier, Consumer<T> setter, String logId) {
        executeSafely(supplier, setter, logId, null);
    }

    /**
     * Executa um Supplier com try-catch e aplica o resultado via Consumer,
     * utilizando um valor fallback em caso de erro.
     */
    private <T> void executeSafely(Supplier<T> supplier, Consumer<T> setter,
                                    String logId, T fallback) {
        try {
            T result = supplier.get();
            setter.accept(result != null ? result : fallback);
        } catch (Exception e) {
            log.warn("Erro para {}: {}", logId, e.getMessage());
            if (fallback != null) {
                setter.accept(fallback);
            }
        }
    }

    /**
     * Busca links de redes sociais no domínio com try-catch.
     * Retorna lista mutável vazia em caso de erro.
     */
    private List<String> discoverSocialLinksSafely(String domain) {
        try {
            List<String> result = socialDiscoveryService.discoverSocialLinks(domain);
            return result != null ? new ArrayList<>(result) : new ArrayList<>();
        } catch (Exception e) {
            log.warn("Erro ao buscar dados: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
