package solutions.pdroti.lead.enrichment.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestTemplate;
import solutions.pdroti.lead.enrichment.api.config.TechScraperProperties;
import solutions.pdroti.lead.enrichment.api.dto.ScrapedPageData;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TechScraperServiceTest {

    @Mock
    private TechScraperProperties properties;

    @Mock
    private RestTemplate restTemplate;

    private TechScraperService techScraperService;

    @BeforeEach
    void setUp() {
        techScraperService = new TechScraperService(properties, restTemplate);
    }

    // ========== scrapeTechnologies ==========

    @Test
    void scrapeTechnologies_comDomainNull_deveRetornarListaVazia() {
        assertTrue(techScraperService.scrapeTechnologies(null).isEmpty());
    }

    @Test
    void scrapeTechnologies_comDomainBlank_deveRetornarListaVazia() {
        assertTrue(techScraperService.scrapeTechnologies("   ").isEmpty());
    }

    @Test
    void scrapeTechnologies_comSucesso_deveDetectarAssinaturas() {
        String html = "<html><body>wp-content plugins</body></html>";
        when(restTemplate.getForObject("https://exemplo.com", String.class)).thenReturn(html);
        when(properties.getSignatures()).thenReturn(Map.of("WordPress", List.of("wp-content", "wp-includes")));
        when(properties.getScriptDetectors()).thenReturn(Map.of());
        when(properties.getMetaGenerators()).thenReturn(Map.of());

        var result = techScraperService.scrapeTechnologies("exemplo.com");

        assertTrue(result.contains("WordPress"));
    }

    @Test
    void scrapeTechnologies_comScriptDetector_deveDetectar() {
        String html = "<html><body><script src=\"https://connect.facebook.net/fbq.js\"></script></body></html>";
        when(restTemplate.getForObject("https://exemplo.com", String.class)).thenReturn(html);
        when(properties.getSignatures()).thenReturn(Map.of());
        when(properties.getScriptDetectors()).thenReturn(Map.of("Facebook Pixel", List.of("facebook.net", "fbq")));
        when(properties.getMetaGenerators()).thenReturn(Map.of());

        var result = techScraperService.scrapeTechnologies("exemplo.com");

        assertTrue(result.contains("Facebook Pixel"));
    }

    @Test
    void scrapeTechnologies_comMetaGenerator_deveDetectar() {
        String html = "<html><head><meta name=\"generator\" content=\"WordPress 6.4\"></head><body></body></html>";
        when(restTemplate.getForObject("https://exemplo.com", String.class)).thenReturn(html);
        when(properties.getSignatures()).thenReturn(Map.of());
        when(properties.getScriptDetectors()).thenReturn(Map.of());
        when(properties.getMetaGenerators()).thenReturn(Map.of("wordpress", "WordPress"));

        var result = techScraperService.scrapeTechnologies("exemplo.com");

        assertTrue(result.contains("WordPress"));
    }

    @Test
    void scrapeTechnologies_comCsrfToken_deveDetectar() {
        String html = "<html><head><meta name=\"csrf-param\" content=\"token\"></head><body></body></html>";
        when(restTemplate.getForObject("https://exemplo.com", String.class)).thenReturn(html);
        when(properties.getSignatures()).thenReturn(Map.of());
        when(properties.getScriptDetectors()).thenReturn(Map.of());
        when(properties.getMetaGenerators()).thenReturn(Map.of());

        var result = techScraperService.scrapeTechnologies("exemplo.com");

        assertTrue(result.contains("CSRF Protection"));
    }

    @Test
    void scrapeTechnologies_comFacebookAppId_deveDetectar() {
        String html = "<html><head><meta property=\"fb:app_id\" content=\"123456\"></head><body></body></html>";
        when(restTemplate.getForObject("https://exemplo.com", String.class)).thenReturn(html);
        when(properties.getSignatures()).thenReturn(Map.of());
        when(properties.getScriptDetectors()).thenReturn(Map.of());
        when(properties.getMetaGenerators()).thenReturn(Map.of());

        var result = techScraperService.scrapeTechnologies("exemplo.com");

        assertTrue(result.contains("Facebook App"));
    }

    @Test
    void scrapeTechnologies_comGoogleTagManager_deveDetectar() {
        String html = "<html><head><meta name=\"gtm-teste\" content=\"GTM-bucket\"></head><body></body></html>";
        when(restTemplate.getForObject("https://exemplo.com", String.class)).thenReturn(html);
        when(properties.getSignatures()).thenReturn(Map.of());
        when(properties.getScriptDetectors()).thenReturn(Map.of());
        when(properties.getMetaGenerators()).thenReturn(Map.of());

        var result = techScraperService.scrapeTechnologies("exemplo.com");

        assertTrue(result.contains("Google Tag Manager"));
    }

    @Test
    void scrapeTechnologies_comHtmlComHttpScheme_deveUsarHttp() {
        when(restTemplate.getForObject("http://exemplo.com", String.class)).thenReturn("<html></html>");
        when(properties.getSignatures()).thenReturn(Map.of());
        when(properties.getScriptDetectors()).thenReturn(Map.of());
        when(properties.getMetaGenerators()).thenReturn(Map.of());

        var result = techScraperService.scrapeTechnologies("http://exemplo.com");

        assertTrue(result.isEmpty());
        verify(restTemplate).getForObject("http://exemplo.com", String.class);
    }

    @Test
    void scrapeTechnologies_comErroTimeout_deveRetornarScrapeError() {
        when(restTemplate.getForObject("https://exemplo.com", String.class))
                .thenThrow(new RuntimeException("timeout"));

        var result = techScraperService.scrapeTechnologies("exemplo.com");

        assertTrue(result.stream().anyMatch(r -> r.contains("Timeout")));
    }

    @Test
    void scrapeTechnologies_comErro403_deveRetornarAccessDenied() {
        when(restTemplate.getForObject("https://exemplo.com", String.class))
                .thenThrow(new RuntimeException("403 forbidden"));

        var result = techScraperService.scrapeTechnologies("exemplo.com");

        assertTrue(result.stream().anyMatch(r -> r.contains("403")));
    }

    @Test
    void scrapeTechnologies_comErroSSL_deveRetornarSslError() {
        when(restTemplate.getForObject("https://exemplo.com", String.class))
                .thenThrow(new RuntimeException("SSL handshake failed"));

        var result = techScraperService.scrapeTechnologies("exemplo.com");

        assertTrue(result.stream().anyMatch(r -> r.contains("SSL")));
    }

    // ========== scrapePage ==========

    @Test
    void scrapePage_comDomainNull_deveRetornarEmpty() {
        var result = techScraperService.scrapePage(null);
        assertNull(result.title());
    }

    @Test
    void scrapePage_comDomainBlank_deveRetornarEmpty() {
        var result = techScraperService.scrapePage("   ");
        assertNull(result.title());
    }

    @Test
    void scrapePage_comErro_deveRetornarPaginaComErro() {
        when(restTemplate.getForObject("https://exemplo.com", String.class))
                .thenThrow(new RuntimeException("Timeout"));

        var result = techScraperService.scrapePage("exemplo.com");

        assertNotNull(result);
        assertTrue(result.technologies().stream().anyMatch(t -> t.contains("Erro")));
    }

    @Test
    void scrapePage_comDadosCompletos_deveRetornarDados() {
        String html = """
                <html lang="pt-BR">
                <head>
                    <title>Exemplo Site</title>
                    <meta name="description" content="Site de exemplo">
                    <link rel="icon" href="/favicon.ico">
                    <link rel="canonical" href="https://exemplo.com/">
                    <meta name="theme-color" content="#ff0000">
                    <meta charset="utf-8">
                    <meta property="og:title" content="Exemplo OG">
                </head>
                <body><h1>Bem-vindo</h1><a href="https://facebook.com/exemplo">FB</a></body>
                </html>
                """;

        when(restTemplate.getForObject("https://exemplo.com", String.class)).thenReturn(html);
        when(properties.getSignatures()).thenReturn(Map.of());
        when(properties.getScriptDetectors()).thenReturn(Map.of());
        when(properties.getMetaGenerators()).thenReturn(Map.of());

        var result = techScraperService.scrapePage("exemplo.com");

        assertEquals("Exemplo Site", result.title());
        assertEquals("Site de exemplo", result.description());
        assertEquals("pt-BR", result.language());
        assertEquals("/favicon.ico", result.favicon());
        assertEquals("https://exemplo.com/", result.canonicalUrl());
        assertEquals("#ff0000", result.themeColor());
        assertEquals("utf-8", result.charset());
        assertTrue(result.openGraph().containsKey("og:title"));
        assertEquals("Bem-vindo", result.h1Headings().get(0));
        assertTrue(result.socialLinks().contains("https://facebook.com/exemplo"));
    }

    // ========== scrapeTechnologiesAndCheckName ==========

    @Test
    void scrapeTechnologiesAndCheckName_comNomeNoBody_deveRetornarMencao() {
        String html = "<html><head><title>João Silva</title></head><body>João Silva é engenheiro</body></html>";
        when(restTemplate.getForObject("https://joaosilva.com", String.class)).thenReturn(html);
        when(properties.getSignatures()).thenReturn(Map.of());
        when(properties.getScriptDetectors()).thenReturn(Map.of());
        when(properties.getMetaGenerators()).thenReturn(Map.of());

        var result = techScraperService.scrapeTechnologiesAndCheckName("joaosilva.com", "João Silva");

        assertTrue(result.nameMentions().stream().anyMatch(m -> m.contains("Nome completo encontrado em:")));
    }

    @Test
    void scrapeTechnologiesAndCheckName_comNomeApenasNoTitulo_deveRetornarMencao() {
        String html = "<html><head><title>João Silva - Portfolio</title></head><body>Bem-vindo</body></html>";
        when(restTemplate.getForObject("https://joaosilva.com", String.class)).thenReturn(html);
        when(properties.getSignatures()).thenReturn(Map.of());
        when(properties.getScriptDetectors()).thenReturn(Map.of());
        when(properties.getMetaGenerators()).thenReturn(Map.of());

        var result = techScraperService.scrapeTechnologiesAndCheckName("joaosilva.com", "João Silva");

        assertTrue(result.nameMentions().stream().anyMatch(m -> m.contains("título da página")));
    }

    @Test
    void scrapeTechnologiesAndCheckName_comNomeNaoEncontrado_deveRetornarVazio() {
        String html = "<html><head><title>Outra Pessoa</title></head><body>Conteúdo qualquer</body></html>";
        when(restTemplate.getForObject("https://exemplo.com", String.class)).thenReturn(html);
        when(properties.getSignatures()).thenReturn(Map.of());
        when(properties.getScriptDetectors()).thenReturn(Map.of());
        when(properties.getMetaGenerators()).thenReturn(Map.of());

        var result = techScraperService.scrapeTechnologiesAndCheckName("exemplo.com", "João Silva");

        assertTrue(result.nameMentions().isEmpty());
    }

    @Test
    void scrapeTechnologiesAndCheckName_comDomainNull_deveRetornarVazio() {
        var result = techScraperService.scrapeTechnologiesAndCheckName(null, "Teste");
        assertTrue(result.technologies().isEmpty());
        assertTrue(result.nameMentions().isEmpty());
    }

    @Test
    void scrapeTechnologiesAndCheckName_comNameNull_deveRetornarApenasTecnologias() {
        String html = "<html><head><meta name=\"generator\" content=\"Joomla\"></head><body></body></html>";
        when(restTemplate.getForObject("https://exemplo.com", String.class)).thenReturn(html);
        when(properties.getSignatures()).thenReturn(Map.of());
        when(properties.getScriptDetectors()).thenReturn(Map.of());
        when(properties.getMetaGenerators()).thenReturn(Map.of("joomla", "Joomla"));

        var result = techScraperService.scrapeTechnologiesAndCheckName("exemplo.com", null);

        assertTrue(result.technologies().contains("Joomla"));
        assertTrue(result.nameMentions().isEmpty());
    }

    // ========== findNameInPage ==========

    @Test
    void findNameInPage_comNomeEncontrado_deveRetornarLista() {
        String html = "<html><head><title>João Silva</title></head><body>João Silva é engenheiro</body></html>";
        when(restTemplate.getForObject("https://joaosilva.com", String.class)).thenReturn(html);

        var result = techScraperService.findNameInPage("joaosilva.com", "João Silva");

        assertFalse(result.isEmpty());
    }

    @Test
    void findNameInPage_comNomeNaoEncontrado_deveRetornarVazio() {
        String html = "<html><body>Conteúdo sem nome</body></html>";
        when(restTemplate.getForObject("https://exemplo.com", String.class)).thenReturn(html);

        var result = techScraperService.findNameInPage("exemplo.com", "Maria Souza");

        assertTrue(result.isEmpty());
    }

    @Test
    void findNameInPage_comDomainNull_deveRetornarVazio() {
        assertTrue(techScraperService.findNameInPage(null, "Teste").isEmpty());
    }

    @Test
    void findNameInPage_comNameNull_deveRetornarVazio() {
        assertTrue(techScraperService.findNameInPage("exemplo.com", null).isEmpty());
    }
}
