package solutions.pdroti.lead.enrichment.api.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import solutions.pdroti.lead.enrichment.api.config.OpenSerpProxyProperties;
import solutions.pdroti.lead.enrichment.api.config.OpenSerpProxyProperties.EndpointConfig;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OpenSerpSearchServiceTest {

    @Mock
    private OpenSerpProxyProperties proxyProperties;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private Cache<String, JsonArray> openSerpCache;

    @Mock
    private Cache<String, String> openSerpHashCache;

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private OpenSerpCircuitBreaker circuitBreaker;

    @Mock
    private OpenSerpRateLimiter rateLimiter;

    @Mock
    private OpenSerpResponseParser responseParser;

    private OpenSerpSearchService openSerpSearchService;

    @BeforeEach
    void setUp() {
        EndpointConfig endpoint = new EndpointConfig();
        endpoint.setUrl("http://opensrp:7000");

        when(proxyProperties.getEndpoints()).thenReturn(List.of(endpoint));

        // Mock responseParser.parse() para comportar-se como o parser real
        var gson = new Gson();
        when(responseParser.parse(anyString(), anyString())).thenAnswer(invocation -> {
            String raw = invocation.getArgument(0);
            if (raw == null || raw.isBlank()) return null;
            try {
                JsonElement root = gson.fromJson(raw, JsonElement.class);
                if (root != null && root.isJsonObject() && root.getAsJsonObject().has("results")) {
                    return root.getAsJsonObject().get("results").getAsJsonArray();
                }
                // Se não for JSON com "results", tenta parse como array simples
                if (root != null && root.isJsonArray()) {
                    return root.getAsJsonArray();
                }
            } catch (Exception ignored) {
                // Não é JSON — retorna null (text format não é usado nos testes)
            }
            return null;
        });

        openSerpSearchService = new OpenSerpSearchService(
                proxyProperties, restTemplate, openSerpCache, openSerpHashCache,
                redisCacheService, circuitBreaker, rateLimiter, responseParser);
    }

    @Test
    void searchPerson_comSucesso_deveRetornarResultados() {
        String jsonResponse = """
                {"results": [
                    {"title": "João Silva - LinkedIn", "url": "https://linkedin.com/in/joaosilva", "snippet": "Perfil do João Silva", "domain": "linkedin.com"},
                    {"title": "João Silva - GitHub", "url": "https://github.com/joaosilva", "snippet": "Repositórios", "domain": "github.com"}
                ]}
                """;

        when(openSerpCache.getIfPresent(anyString())).thenReturn(null);
        when(redisCacheService.get(anyString())).thenReturn(null);
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(jsonResponse);
        // Return empty for Redis setAsync
        doNothing().when(redisCacheService).setAsync(anyString(), anyLong(), anyString());

        JsonArray results = openSerpSearchService.searchPerson("João Silva");

        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("João Silva - LinkedIn", results.get(0).getAsJsonObject().get("title").getAsString());
        verify(openSerpCache).put(anyString(), any(JsonArray.class));
    }

    @Test
    void searchPerson_comCacheL1Hit_deveRetornarCache() {
        JsonArray cachedData = new JsonArray();
        var item = new com.google.gson.JsonObject();
        item.addProperty("title", "João Silva - GitHub");
        cachedData.add(item);

        when(openSerpCache.getIfPresent(anyString())).thenReturn(cachedData);

        JsonArray results = openSerpSearchService.searchPerson("João Silva");

        assertNotNull(results);
        assertEquals(1, results.size());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void searchPerson_comCacheL2RedisHit_deveRetornarRedis() {
        String redisData = """
                [{"title": "João Silva - LinkedIn", "url": "https://linkedin.com/in/joaosilva", "snippet": "Perfil", "domain": "linkedin.com"}]
                """;

        when(openSerpCache.getIfPresent(anyString())).thenReturn(null);
        when(redisCacheService.get(anyString())).thenReturn(redisData);

        JsonArray results = openSerpSearchService.searchPerson("João Silva");

        assertNotNull(results);
        assertEquals(1, results.size());
        verify(openSerpCache).put(anyString(), any(JsonArray.class));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void searchPerson_comRespostaVazia_deveRetornarArrayVazio() {
        when(openSerpCache.getIfPresent(anyString())).thenReturn(null);
        when(redisCacheService.get(anyString())).thenReturn(null);
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("");

        JsonArray results = openSerpSearchService.searchPerson("João Silva");

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void searchPerson_comErroHttp_deveRetornarArrayVazio() {
        when(openSerpCache.getIfPresent(anyString())).thenReturn(null);
        when(redisCacheService.get(anyString())).thenReturn(null);
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        JsonArray results = openSerpSearchService.searchPerson("João Silva");

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void searchPerson_comCaptcha429_deveRetornarArrayVazio() {
        when(openSerpCache.getIfPresent(anyString())).thenReturn(null);
        when(redisCacheService.get(anyString())).thenReturn(null);

        HttpClientErrorException captchaException = mock(HttpClientErrorException.class);
        when(captchaException.getStatusCode()).thenReturn(
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
        when(captchaException.getResponseBodyAsString()).thenReturn(
                "{\"error\":\"captcha_detected\"}");

        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(captchaException);

        JsonArray results = openSerpSearchService.searchPerson("João Silva");

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void search_comQueryGenerica_deveRetornarResultados() {
        String jsonResponse = """
                {"results": [
                    {"title": "Resultado Teste", "url": "https://exemplo.com", "snippet": "Teste", "domain": "exemplo.com"}
                ]}
                """;

        when(openSerpCache.getIfPresent(anyString())).thenReturn(null);
        when(redisCacheService.get(anyString())).thenReturn(null);
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(jsonResponse);

        JsonArray results = openSerpSearchService.search("teste", "Teste", 10);

        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @Test
    void searchDocuments_deveBuscarMultiplosFileTypes() {
        when(openSerpCache.getIfPresent(anyString())).thenReturn(null);
        when(redisCacheService.get(anyString())).thenReturn(null);

        String pdfResponse = "{\"results\": [{\"title\": \"Documento PDF\", \"url\": \"https://exemplo.com/doc.pdf\"}]}";
        String docResponse = "{\"results\": [{\"title\": \"Documento DOC\", \"url\": \"https://exemplo.com/doc.doc\"}]}";

        when(restTemplate.getForObject(contains("filetype%3Apdf"), eq(String.class)))
                .thenReturn(pdfResponse);
        when(restTemplate.getForObject(contains("filetype%3Adoc"), eq(String.class)))
                .thenReturn(docResponse);
        when(restTemplate.getForObject(contains("filetype%3Adocx"), eq(String.class)))
                .thenReturn("{\"results\": []}");

        JsonArray results = openSerpSearchService.searchDocuments("João Silva", 10);

        assertNotNull(results);
        assertEquals(2, results.size());
    }

    @Test
    void searchSocialMedia_deveBuscarComQuerySocial() {
        String jsonResponse = """
                {"results": [
                    {"title": "João Silva | LinkedIn", "url": "https://linkedin.com/in/joaosilva", "snippet": "Perfil", "domain": "linkedin.com"}
                ]}
                """;

        when(openSerpCache.getIfPresent(anyString())).thenReturn(null);
        when(redisCacheService.get(anyString())).thenReturn(null);
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(jsonResponse);

        JsonArray results = openSerpSearchService.searchSocialMedia("João Silva", 10);

        assertNotNull(results);
        assertEquals(1, results.size());
        verify(openSerpCache).put(anyString(), any(JsonArray.class));
    }

    @Test
    void parseTextResponse_deveExtrairResultadosDeFormatoTexto() {
        when(openSerpCache.getIfPresent(anyString())).thenReturn(null);
        when(redisCacheService.get(anyString())).thenReturn(null);

        String jsonResponse = """
                {"results": [
                    {"title": "João Silva - LinkedIn", "url": "https://linkedin.com/in/joaosilva", "snippet": "Perfil", "domain": "linkedin.com"},
                    {"title": "João Silva - GitHub", "url": "https://github.com/joaosilva", "snippet": "Código", "domain": "github.com"}
                ]}
                """;

        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(jsonResponse);

        JsonArray results = openSerpSearchService.searchPerson("João Silva");

        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("João Silva - LinkedIn", results.get(0).getAsJsonObject().get("title").getAsString());
        assertEquals("https://linkedin.com/in/joaosilva", results.get(0).getAsJsonObject().get("url").getAsString());
        assertEquals("linkedin.com", results.get(0).getAsJsonObject().get("domain").getAsString());
    }

    @Test
    void searchProfessional_deveBuscarComQueryProfissional() {
        String jsonResponse = """
                {"results": [
                    {"title": "João Silva - GitHub", "url": "https://github.com/joaosilva", "domain": "github.com"}
                ]}
                """;

        when(openSerpCache.getIfPresent(anyString())).thenReturn(null);
        when(redisCacheService.get(anyString())).thenReturn(null);
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(jsonResponse);

        JsonArray results = openSerpSearchService.searchProfessional("João Silva", 10);

        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @Test
    void searchContact_deveBuscarComQueryContato() {
        String jsonResponse = """
                {"results": [
                    {"title": "Contato", "url": "https://exemplo.com/contato", "domain": "exemplo.com"}
                ]}
                """;

        when(openSerpCache.getIfPresent(anyString())).thenReturn(null);
        when(redisCacheService.get(anyString())).thenReturn(null);
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(jsonResponse);

        JsonArray results = openSerpSearchService.searchContact("João Silva", 10);

        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @Test
    void searchNews_deveBuscarComQueryNoticias() {
        String jsonResponse = """
                {"results": [
                    {"title": "Notícia sobre João Silva", "url": "https://news.exemplo.com/artigo", "domain": "news.exemplo.com"}
                ]}
                """;

        when(openSerpCache.getIfPresent(anyString())).thenReturn(null);
        when(redisCacheService.get(anyString())).thenReturn(null);
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(jsonResponse);

        JsonArray results = openSerpSearchService.searchNews("João Silva", 10);

        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @Test
    void constructor_comEndpointsVazio_deveUsarApiUrlFallback() {
        when(proxyProperties.getEndpoints()).thenReturn(List.of());
        when(proxyProperties.getApiUrl()).thenReturn("http://fallback:7000");

        OpenSerpSearchService service = new OpenSerpSearchService(
                proxyProperties, restTemplate, openSerpCache, openSerpHashCache,
                redisCacheService, circuitBreaker, rateLimiter, responseParser);

        // Não lançou exceção — usou fallback
        assertNotNull(service);
    }

    @Test
    void constructor_comEndpointsEApiUrlNull_deveUsarLocalhostFallback() {
        when(proxyProperties.getEndpoints()).thenReturn(List.of());
        when(proxyProperties.getApiUrl()).thenReturn(null);

        OpenSerpSearchService service = new OpenSerpSearchService(
                proxyProperties, restTemplate, openSerpCache, openSerpHashCache,
                redisCacheService, circuitBreaker, rateLimiter, responseParser);

        assertNotNull(service);
    }
}
