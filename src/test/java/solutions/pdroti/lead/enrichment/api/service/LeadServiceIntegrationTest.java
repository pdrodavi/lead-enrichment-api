package solutions.pdroti.lead.enrichment.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import solutions.pdroti.lead.enrichment.api.model.Lead;
import solutions.pdroti.lead.enrichment.api.repository.LeadRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadServiceIntegrationTest {

    @Mock
    private LeadRepository leadRepository;
    @Mock
    private DnsValidationService dnsValidationService;
    @Mock
    private TechScraperService techScraperService;
    @Mock
    private SocialDiscoveryService socialDiscoveryService;
    @Mock
    private RedisCacheService redisCacheService;

    private LeadService leadService;

    @BeforeEach
    void setUp() {
        leadService = new LeadService(leadRepository, dnsValidationService,
                techScraperService, socialDiscoveryService, redisCacheService);
    }

    @Test
    void shouldReturnCachedLeadWhenPresent() {
        String email = "user@example.com";
        Lead cachedLead = Lead.builder().email(email).domain("example.com").build();

        when(redisCacheService.get(email)).thenReturn(Optional.of(cachedLead));

        Lead result = leadService.enrich(email, "example.com");

        assertSame(cachedLead, result);
        verify(leadRepository, never()).findByEmail(any());
    }

    @Test
    void shouldReturnExistingEnrichedLead() {
        String email = "user@example.com";
        Lead existingLead = Lead.builder()
                .email(email).domain("example.com")
                .technologies(List.of("React"))
                .build();

        when(redisCacheService.get(email)).thenReturn(Optional.empty());
        when(leadRepository.findByEmail(email)).thenReturn(Optional.of(existingLead));

        Lead result = leadService.enrich(email, "example.com");

        assertSame(existingLead, result);
        verify(leadRepository, never()).save(any());
    }

    @Test
    void shouldEnrichNewLead() {
        String email = "user@example.com";
        String domain = "example.com";

        when(redisCacheService.get(email)).thenReturn(Optional.empty());
        when(leadRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(dnsValidationService.hasMxRecord(domain)).thenReturn(true);
        when(techScraperService.scrapeTechnologies(domain)).thenReturn(List.of("React"));
        when(socialDiscoveryService.discoverSocialLinks(domain)).thenReturn(List.of("https://linkedin.com/company/example"));

        Lead savedLead = Lead.builder().id(1L).email(email).domain(domain)
                .mxStatus(true).technologies(List.of("React"))
                .socialLinks(List.of("https://linkedin.com/company/example")).build();
        when(leadRepository.save(any(Lead.class))).thenReturn(savedLead);

        Lead result = leadService.enrich(email, domain);

        assertEquals(email, result.getEmail());
        assertEquals(domain, result.getDomain());
        assertTrue(result.getMxStatus());
        assertEquals(List.of("React"), result.getTechnologies());
        verify(redisCacheService).put(email, savedLead);
    }

    @Test
    void shouldHandleScrapingErrorGracefully() {
        String email = "user@example.com";
        String domain = "example.com";

        when(redisCacheService.get(email)).thenReturn(Optional.empty());
        when(leadRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(dnsValidationService.hasMxRecord(domain)).thenReturn(true);
        when(techScraperService.scrapeTechnologies(domain)).thenThrow(new RuntimeException("Timeout"));

        Lead savedLead = Lead.builder().id(1L).email(email).domain(domain).mxStatus(true)
                .technologies(List.of("TechScrapeError: Timeout"))
                .socialLinks(List.of()).build();
        when(leadRepository.save(any(Lead.class))).thenReturn(savedLead);

        Lead result = leadService.enrich(email, domain);

        assertTrue(result.getTechnologies().get(0).contains("TechScrapeError"));
        assertTrue(result.getSocialLinks().isEmpty());
    }
}