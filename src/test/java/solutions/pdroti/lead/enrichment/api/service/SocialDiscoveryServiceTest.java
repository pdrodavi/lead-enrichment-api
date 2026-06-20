package solutions.pdroti.lead.enrichment.api.service;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solutions.pdroti.lead.enrichment.api.config.SocialDiscoveryProperties;
import solutions.pdroti.lead.enrichment.api.dto.SocialProfileData;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SocialDiscoveryServiceTest {

    @Mock
    private SocialDiscoveryProperties properties;

    @Mock
    private Cache<String, List<String>> socialLinksCache;

    @Mock
    private Cache<String, SocialProfileData> socialProfileCache;

    @Mock
    private org.springframework.web.client.RestTemplate restTemplate;

    private SocialDiscoveryService socialDiscoveryService;

    @BeforeEach
    void setUp() {
        socialDiscoveryService = new SocialDiscoveryService(
                properties, socialLinksCache, socialProfileCache,
                restTemplate, Runnable::run);
    }

    @Test
    void discoverSocialLinks_comDomainNull_deveRetornarListaVazia() {
        List<String> result = socialDiscoveryService.discoverSocialLinks(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void discoverSocialLinks_comDomainBlank_deveRetornarListaVazia() {
        List<String> result = socialDiscoveryService.discoverSocialLinks("   ");
        assertTrue(result.isEmpty());
    }

    @Test
    void discoverSocialLinks_comCacheHit_deveRetornarCache() {
        List<String> cachedLinks = List.of("https://facebook.com/empresa");
        when(socialLinksCache.getIfPresent("exemplo.com")).thenReturn(cachedLinks);

        List<String> result = socialDiscoveryService.discoverSocialLinks("exemplo.com");

        assertEquals(1, result.size());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void discoverSocialLinks_comRestTemplateFalha_deveRetornarListaVazia() {
        when(socialLinksCache.getIfPresent("exemplo.com")).thenReturn(null);
        when(restTemplate.getForObject("https://exemplo.com", String.class))
                .thenThrow(new RuntimeException("Timeout"));

        List<String> result = socialDiscoveryService.discoverSocialLinks("exemplo.com");

        assertTrue(result.isEmpty());
    }

    @Test
    void discoverSocialLinks_comHtmlComLinksSociais_deveExtrair() {
        String html = """
                <html><body>
                    <a href="https://facebook.com/empresa">Facebook</a>
                    <a href="https://instagram.com/empresa">Instagram</a>
                    <a href="https://twitter.com/empresa">Twitter</a>
                    <a href="https://linkedin.com/company/empresa">LinkedIn</a>
                </body></html>
                """;

        when(socialLinksCache.getIfPresent("exemplo.com")).thenReturn(null);
        when(restTemplate.getForObject("https://exemplo.com", String.class)).thenReturn(html);
        when(properties.getSocialDomains()).thenReturn(
                List.of("facebook.com", "instagram.com", "twitter.com", "linkedin.com"));

        List<String> result = socialDiscoveryService.discoverSocialLinks("exemplo.com");

        assertEquals(4, result.size());
        assertTrue(result.stream().anyMatch(l -> l.contains("facebook.com")));
        assertTrue(result.stream().anyMatch(l -> l.contains("linkedin.com")));
        verify(socialLinksCache).put(eq("exemplo.com"), anyList());
    }

    @Test
    void getSocialDomains_deveRetornarListaDeDominios() {
        List<String> domains = List.of("facebook.com", "linkedin.com");
        when(properties.getSocialDomains()).thenReturn(domains);

        List<String> result = socialDiscoveryService.getSocialDomains();

        assertEquals(2, result.size());
        assertTrue(result.contains("facebook.com"));
    }

    @Test
    void scrapeSocialProfiles_comListaVazia_deveRetornarListaVazia() {
        List<SocialProfileData> result = socialDiscoveryService.scrapeSocialProfiles(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void scrapeSocialProfiles_comNull_deveRetornarListaVazia() {
        List<SocialProfileData> result = socialDiscoveryService.scrapeSocialProfiles(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void scrapeSocialProfiles_comUrlComCacheHit_deveRetornarCache() throws Exception {
        SocialProfileData cachedProfile = new SocialProfileData(
                "https://github.com/pdroti", "GitHub", "pdroti (Pedro)", "Dev");
        when(socialProfileCache.getIfPresent("https://github.com/pdroti")).thenReturn(cachedProfile);

        List<SocialProfileData> result = socialDiscoveryService.scrapeSocialProfiles(
                List.of("https://github.com/pdroti"));

        assertEquals(1, result.size());
        assertEquals("GitHub", result.get(0).platform());
        assertEquals("pdroti (Pedro)", result.get(0).title());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void scrapeSocialProfiles_comUrlSemCache_deveFazerFetch() throws Exception {
        String html = "<html><head><title>Meu Perfil</title>"
                + "<meta property=\"og:description\" content=\"Descrição do perfil\">"
                + "</head></html>";

        when(socialProfileCache.getIfPresent("https://github.com/pdroti")).thenReturn(null);
        when(restTemplate.getForObject("https://github.com/pdroti", String.class)).thenReturn(html);
        when(properties.getPlatformNames()).thenReturn(Map.of("github.com", "GitHub"));

        List<SocialProfileData> result = socialDiscoveryService.scrapeSocialProfiles(
                List.of("https://github.com/pdroti"));

        assertEquals(1, result.size());
        assertEquals("GitHub", result.get(0).platform());
        assertEquals("Meu Perfil", result.get(0).title());
        assertEquals("Descrição do perfil", result.get(0).description());
        verify(socialProfileCache).put(eq("https://github.com/pdroti"), any(SocialProfileData.class));
    }

    @Test
    void discoverSocialLinks_comUrlComHttp_deveUsarHttp() {
        when(socialLinksCache.getIfPresent("http://exemplo.com")).thenReturn(null);
        when(restTemplate.getForObject("http://exemplo.com", String.class))
                .thenReturn("<html></html>");
        when(properties.getSocialDomains()).thenReturn(List.of());

        List<String> result = socialDiscoveryService.discoverSocialLinks("http://exemplo.com");

        assertTrue(result.isEmpty());
        verify(restTemplate).getForObject("http://exemplo.com", String.class);
        verify(socialLinksCache).put(eq("http://exemplo.com"), anyList());
    }
}
