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

    @Test
    void scrapeTechnologies_comDomainNull_deveRetornarListaVazia() {
        List<String> result = techScraperService.scrapeTechnologies(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void scrapeTechnologies_comDomainBlank_deveRetornarListaVazia() {
        List<String> result = techScraperService.scrapeTechnologies("   ");
        assertTrue(result.isEmpty());
    }

    @Test
    void scrapeTechnologies_comSucesso_deveDetectarTecnologias() {
        String html = """
                <html>
                <head>
                    <meta name="generator" content="WordPress 6.4">
                    <meta property="og:title" content="Teste">
                </head>
                <body><script src="https://www.googletagmanager.com/gtag/js?id=UA-123"></script></body>
                </html>
                """;

        when(restTemplate.getForObject("https://exemplo.com", String.class)).thenReturn(html);
        when(properties.getSignatures()).thenReturn(Map.of());
        when(properties.getScriptDetectors()).thenReturn(
                Map.of("Google Analytics", List.of("googletagmanager.com")));
        when(properties.getMetaGenerators()).thenReturn(Map.of("wordpress", "WordPress"));

        List<String> result = techScraperService.scrapeTechnologies("exemplo.com");

        assertTrue(result.contains("WordPress"));
        assertTrue(result.contains("Google Analytics"));
    }

    @Test
    void scrapeTechnologies_comErroHttp_deveRetornarErroComoTecnologia() {
        when(restTemplate.getForObject("https://exemplo.com", String.class))
                .thenThrow(new RuntimeException("Connection refused"));

        List<String> result = techScraperService.scrapeTechnologies("exemplo.com");

        assertFalse(result.isEmpty());
        assertTrue(result.stream().anyMatch(r -> r.contains("Error")));
    }

    @Test
    void scrapeTechnologies_comHtmlSimples_deveDetectarOpenGraphETwitterCards() {
        String html = """
                <html>
                <head>
                    <meta property="og:title" content="Título">
                    <meta property="og:description" content="Descrição">
                    <meta name="twitter:card" content="summary">
                </head>
                <body></body>
                </html>
                """;

        when(restTemplate.getForObject("https://exemplo.com", String.class)).thenReturn(html);
        when(properties.getSignatures()).thenReturn(Map.of());
        when(properties.getScriptDetectors()).thenReturn(Map.of());
        when(properties.getMetaGenerators()).thenReturn(Map.of());

        List<String> result = techScraperService.scrapeTechnologies("exemplo.com");

        assertTrue(result.contains("Open Graph"));
    }

    @Test
    void scrapePage_comDomainValido_deveRetornarDadosCompletos() {
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

        ScrapedPageData result = techScraperService.scrapePage("exemplo.com");

        assertEquals("Exemplo Site", result.title());
        assertEquals("Site de exemplo", result.description());
        assertEquals("pt-BR", result.language());
        assertEquals("/favicon.ico", result.favicon());
        assertEquals("https://exemplo.com/", result.canonicalUrl());
        assertEquals("#ff0000", result.themeColor());
        assertEquals("utf-8", result.charset());
        assertTrue(result.openGraph().containsKey("og:title"));
        assertTrue(result.socialLinks().contains("https://facebook.com/exemplo"));
    }

    @Test
    void scrapeTechnologiesAndCheckName_comNomeEncontrado_deveRetornarMencao() {
        String html = """
                <html><head><title>João Silva - Site</title></head>
                <body>Bem-vindo ao site do João Silva</body>
                </html>
                """;

        when(restTemplate.getForObject("https://joaosilva.com", String.class)).thenReturn(html);
        when(properties.getSignatures()).thenReturn(Map.of());
        when(properties.getScriptDetectors()).thenReturn(Map.of());
        when(properties.getMetaGenerators()).thenReturn(Map.of());

        TechScraperService.ScrapeResult result = techScraperService.scrapeTechnologiesAndCheckName(
                "joaosilva.com", "João Silva");

        assertTrue(result.technologies().isEmpty());
        assertTrue(result.nameMentions().stream().anyMatch(m -> m.contains("Nome completo encontrado")));
    }

    @Test
    void scrapeTechnologiesAndCheckName_comDomainNull_deveRetornarVazio() {
        TechScraperService.ScrapeResult result = techScraperService.scrapeTechnologiesAndCheckName(null, "Teste");

        assertTrue(result.technologies().isEmpty());
        assertTrue(result.nameMentions().isEmpty());
    }
}
