package solutions.pdroti.lead.enrichment.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import solutions.pdroti.lead.enrichment.api.dto.DnsResult;
import solutions.pdroti.lead.enrichment.api.dto.RdapData;
import solutions.pdroti.lead.enrichment.api.model.Lead;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DomainEnricherServiceTest {

    @Mock
    private DnsValidationService dnsValidationService;

    @Mock
    private TechScraperService techScraperService;

    @Mock
    private SocialDiscoveryService socialDiscoveryService;

    @Mock
    private RdapService rdapService;

    @Mock
    private Cache<String, List<String>> techCache;

    private DomainEnricherService domainEnricherService;

    @BeforeEach
    void setUp() {
        domainEnricherService = new DomainEnricherService(
                dnsValidationService, techScraperService, socialDiscoveryService,
                rdapService, techCache);
    }

    @Test
    void resetEnrichmentData_deveLimparTodosOsCampos() {
        Lead lead = Lead.builder()
                .mxStatus(true)
                .dnsMxRecords(List.of("mail.exemplo.com"))
                .technologies(List.of("WordPress"))
                .socialLinks(List.of("https://linkedin.com/empresa"))
                .exposedEmails(List.of("contato@exemplo.com"))
                .rdapRegistrar("HOSTINGER")
                .build();

        domainEnricherService.resetEnrichmentData(lead);

        assertFalse(lead.getMxStatus());
        assertTrue(lead.getDnsMxRecords().isEmpty());
        assertTrue(lead.getTechnologies().isEmpty());
        assertTrue(lead.getSocialLinks().isEmpty());
        assertTrue(lead.getExposedEmails().isEmpty());
        assertNull(lead.getRdapRegistrar());
        assertNull(lead.getRdapRawData());
    }

    @Test
    void enrich_comDadosValidos_devePreencherLead() {
        Lead lead = Lead.builder().build();
        String domain = "exemplo.com";
        String name = "João Silva";

        DnsResult dnsResult = new DnsResult(true,
                List.of("mail.exemplo.com"), List.of("192.168.1.1"),
                List.of(), List.of(), List.of("v=spf1 include:_spf.exemplo.com"));

        when(dnsValidationService.lookupDomain(domain)).thenReturn(dnsResult);

        JsonNode rdapJson = JsonNodeFactory.instance.objectNode();
        RdapData rdapData = new RdapData(rdapJson, "HOSTINGER", "Empresa Ltda",
                "admin@exemplo.com", "2020-01-01T00:00:00Z",
                "2025-01-01T00:00:00Z", List.of("ns1.exemplo.com"),
                List.of("client transfer prohibited"), null, "identitydigital");
        when(rdapService.lookup(domain)).thenReturn(rdapData);

        when(techCache.getIfPresent(domain.toLowerCase())).thenReturn(null);
        when(techScraperService.scrapeTechnologiesAndCheckName(domain, name))
                .thenReturn(new TechScraperService.ScrapeResult(
                        List.of("WordPress", "PHP"), List.of("Nome completo encontrado em: https://exemplo.com")));

        when(socialDiscoveryService.discoverSocialLinks(domain)).thenReturn(
                List.of("https://facebook.com/empresa", "https://instagram.com/empresa"));
        when(socialDiscoveryService.scrapeSocialProfiles(anyList()))
                .thenReturn(List.of());

        domainEnricherService.enrich(lead, domain, name);

        assertTrue(lead.getMxStatus());
        assertEquals(1, lead.getDnsMxRecords().size());
        assertEquals("HOSTINGER", lead.getRdapRegistrar());
        assertTrue(lead.getTechnologies().contains("WordPress"));
        assertEquals(2, lead.getSocialLinks().size());
        assertTrue(lead.getNameMentions().stream().anyMatch(m -> m.contains("Nome completo encontrado")));
    }

    @Test
    void enrich_comDnsFalha_deveContinuarComRdap() {
        Lead lead = Lead.builder().build();
        String domain = "exemplo.com";

        when(dnsValidationService.lookupDomain(domain))
                .thenThrow(new RuntimeException("DNS error"));

        JsonNode rdapJson = JsonNodeFactory.instance.objectNode();
        RdapData rdapData = new RdapData(rdapJson, "CLOUDFLARE", null, null, null, null,
                List.of("ns1.cloudflare.com"), List.of(), null, "identitydigital");
        when(rdapService.lookup(domain)).thenReturn(rdapData);

        when(techCache.getIfPresent(domain.toLowerCase())).thenReturn(null);
        when(techScraperService.scrapeTechnologiesAndCheckName(eq(domain), any()))
                .thenReturn(new TechScraperService.ScrapeResult(List.of(), List.of()));

        when(socialDiscoveryService.discoverSocialLinks(domain)).thenReturn(List.of());

        domainEnricherService.enrich(lead, domain, "Teste");

        assertFalse(lead.getMxStatus());
        assertEquals("CLOUDFLARE", lead.getRdapRegistrar());
    }

    @Test
    void enrich_comRdapVazio_deveContinuarComDns() {
        Lead lead = Lead.builder().build();
        String domain = "exemplo.com";

        DnsResult dnsResult = new DnsResult(true, List.of("mail.exemplo.com"),
                List.of("10.0.0.1"), List.of(), List.of(), List.of());
        when(dnsValidationService.lookupDomain(domain)).thenReturn(dnsResult);

        when(rdapService.lookup(domain)).thenReturn(RdapData.empty());

        when(techCache.getIfPresent(domain.toLowerCase())).thenReturn(null);
        when(techScraperService.scrapeTechnologiesAndCheckName(eq(domain), any()))
                .thenReturn(new TechScraperService.ScrapeResult(List.of(), List.of()));

        when(socialDiscoveryService.discoverSocialLinks(domain)).thenReturn(List.of());

        domainEnricherService.enrich(lead, domain, "Teste");

        assertTrue(lead.getMxStatus());
        assertNull(lead.getRdapRegistrar());
    }

    @Test
    void enrich_comTechCacheHit_deveUsarCache() {
        Lead lead = Lead.builder().build();
        String domain = "exemplo.com";

        DnsResult dnsResult = new DnsResult(false, List.of(), List.of(), List.of(), List.of(), List.of());
        when(dnsValidationService.lookupDomain(domain)).thenReturn(dnsResult);
        when(rdapService.lookup(domain)).thenReturn(RdapData.empty());

        when(techCache.getIfPresent(domain.toLowerCase())).thenReturn(List.of("React", "Node.js"));

        when(socialDiscoveryService.discoverSocialLinks(domain)).thenReturn(List.of());

        domainEnricherService.enrich(lead, domain, "Teste");

        assertTrue(lead.getTechnologies().contains("React"));
        verify(techScraperService, never()).scrapeTechnologiesAndCheckName(anyString(), anyString());
    }

    @Test
    void enrich_comSocialLinks_devePreencherPerfis() {
        Lead lead = Lead.builder().build();
        String domain = "exemplo.com";

        when(dnsValidationService.lookupDomain(domain))
                .thenReturn(new DnsResult(false, List.of(), List.of(), List.of(), List.of(), List.of()));
        when(rdapService.lookup(domain)).thenReturn(RdapData.empty());
        when(techCache.getIfPresent(domain.toLowerCase())).thenReturn(List.of());

        when(socialDiscoveryService.discoverSocialLinks(domain))
                .thenReturn(List.of("https://linkedin.com/company/empresa"));

        domainEnricherService.enrich(lead, domain, "Teste");

        assertEquals(1, lead.getSocialLinks().size());
        verify(socialDiscoveryService).scrapeSocialProfiles(
                List.of("https://linkedin.com/company/empresa"));
    }
}
