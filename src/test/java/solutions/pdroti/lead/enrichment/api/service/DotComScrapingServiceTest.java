package solutions.pdroti.lead.enrichment.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import solutions.pdroti.lead.enrichment.api.model.Lead;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DotComScrapingServiceTest {

    @Mock
    private SocialDiscoveryService socialDiscoveryService;

    @Mock
    private RestTemplate restTemplate;

    private DotComScrapingService dotComScrapingService;

    @BeforeEach
    void setUp() {
        dotComScrapingService = new DotComScrapingService(socialDiscoveryService, restTemplate);
    }

    @Test
    void scrapeDotComSites_comDiscoveredUrlsNull_deveIgnorar() {
        Lead lead = Lead.builder().discoveredUrls(null).build();

        dotComScrapingService.scrapeDotComSites(lead, "João Silva");

        verifyNoInteractions(socialDiscoveryService, restTemplate);
    }

    @Test
    void scrapeDotComSites_comDiscoveredUrlsVazio_deveIgnorar() {
        Lead lead = Lead.builder().discoveredUrls(List.of()).build();

        dotComScrapingService.scrapeDotComSites(lead, "João Silva");

        verifyNoInteractions(socialDiscoveryService, restTemplate);
    }

    @Test
    void scrapeDotComSites_comUrlsSemComBr_deveIgnorar() {
        Lead lead = Lead.builder()
                .discoveredUrls(List.of(
                        "https://gitlab.org/pdroti",
                        "https://dev.to/@pdroti"))
                .build();

        dotComScrapingService.scrapeDotComSites(lead, "João Silva");

        verifyNoInteractions(socialDiscoveryService, restTemplate);
    }

    @Test
    void scrapeDotComSites_comUrlsDotCom_deveExtrairDados() {
        Lead lead = Lead.builder()
                .socialLinks(List.of())
                .exposedEmails(List.of())
                .exposedPhones(List.of())
                .discoveredUrls(List.of(
                        "https://exemplo.com",
                        "https://exemplo.com.br",
                        "https://github.com/pdroti"))
                .build();

        when(socialDiscoveryService.discoverSocialLinks("exemplo.com"))
                .thenReturn(List.of("https://facebook.com/exemplo"));
        when(restTemplate.getForObject("https://exemplo.com", String.class))
                .thenReturn("<html><body>contato@exemplo.com (11) 99999-8888</body></html>");

        when(socialDiscoveryService.discoverSocialLinks("exemplo.com.br"))
                .thenReturn(List.of("https://instagram.com/exemplobr"));
        when(restTemplate.getForObject("https://exemplo.com.br", String.class))
                .thenReturn("<html><body>admin@exemplo.com.br</body></html>");

        dotComScrapingService.scrapeDotComSites(lead, "João Silva");

        assertTrue(lead.getSocialLinks().contains("https://facebook.com/exemplo"));
        assertTrue(lead.getSocialLinks().contains("https://instagram.com/exemplobr"));
        assertTrue(lead.getExposedEmails().contains("contato@exemplo.com"));
        assertTrue(lead.getExposedEmails().contains("admin@exemplo.com.br"));
        assertTrue(lead.getExposedPhones().contains("(11) 99999-8888"));
        assertEquals(2, lead.getDorkFindings());
    }

    @Test
    void scrapeDotComSites_comFalhaEmUmSite_deveContinuarComOsDemais() {
        Lead lead = Lead.builder()
                .socialLinks(List.of())
                .exposedEmails(List.of())
                .exposedPhones(List.of())
                .discoveredUrls(List.of(
                        "https://site1.com",
                        "https://site2.com"))
                .build();

        when(socialDiscoveryService.discoverSocialLinks("site1.com"))
                .thenThrow(new RuntimeException("Timeout"));

        when(socialDiscoveryService.discoverSocialLinks("site2.com"))
                .thenReturn(List.of("https://linkedin.com/site2"));
        when(restTemplate.getForObject("https://site2.com", String.class))
                .thenReturn("<html><body>email@site2.com</body></html>");

        dotComScrapingService.scrapeDotComSites(lead, "João Silva");

        assertTrue(lead.getSocialLinks().contains("https://linkedin.com/site2"));
        assertTrue(lead.getExposedEmails().contains("email@site2.com"));
    }

    @Test
    void scrapeDotComSites_comLimiteMaxSite_deveRespeitarMaximo() {
        List<String> manyUrls = List.of(
                "https://site1.com", "https://site2.com", "https://site3.com",
                "https://site4.com", "https://site5.com", "https://site6.com",
                "https://site7.com", "https://site8.com", "https://site9.com",
                "https://site10.com", "https://site11.com", "https://site12.com");

        Lead lead = Lead.builder()
                .socialLinks(List.of())
                .exposedEmails(List.of())
                .exposedPhones(List.of())
                .discoveredUrls(manyUrls)
                .build();

        when(socialDiscoveryService.discoverSocialLinks(anyString()))
                .thenReturn(List.of());
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn("<html></html>");

        dotComScrapingService.scrapeDotComSites(lead, "João Silva");

        // Deve ter chamado no máximo MAX_SITES (10) vezes cada serviço
        verify(socialDiscoveryService, atMost(10)).discoverSocialLinks(anyString());
        verify(restTemplate, atMost(10)).getForObject(anyString(), eq(String.class));
    }
}
