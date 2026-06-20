package solutions.pdroti.lead.enrichment.api.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import solutions.pdroti.lead.enrichment.api.config.OpenSerpProxyProperties;
import solutions.pdroti.lead.enrichment.api.config.OpenSerpProxyProperties.EndpointConfig;
import solutions.pdroti.lead.enrichment.api.util.ContentTracker;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Cliente HTTP para a API do OpenSERP (self-hosted Google Search API).
 * <p>
 * Realiza buscas no Google de forma programática através de uma
 * instância self-hosted do OpenSERP. Executado pelo
 * {@link OpenSerpEnricherService} durante o pipeline de enriquecimento.
 * <p>
 * Endpoint consultado:
 * <pre>
 * GET /google/search?text={query}&limit={n}
 * </pre>
 * <p>
 * Cache:
 * <ul>
 *   <li>L1 — Caffeine local (TTL 30min, 5.000 entradas) com cópia defensiva via Gson</li>
 *   <li>L2 — Redis distribuído (TTL 30min, chave prefixada {@code lead-enrich:})</li>
 *   <li>ContentTracker — hash SHA-256 para detectar mudanças entre refetches</li>
 * </ul>
 * <p>
 * Resiliência:
 * <ul>
 *   <li>CAPTCHA detection — erros 429 com {@code captcha_detected} são identificados</li>
 *   <li>Circuit breaker — após N captchas consecutivos, pausa por um período</li>
 *   <li>Rate limiting — delay mínimo de 2s entre requisições</li>
 *   <li>Proxy rotation — round-robin entre múltiplos endpoints OpenSERP</li>
 *   <li>Failover — se um endpoint falha, tenta o próximo automaticamente</li>
 * </ul>
 *
 * @see OpenSerpEnricher
 * @see <a href="https://github.com/serpapi/open-serp">OpenSERP</a>
 */
@Slf4j
@Service
public class OpenSerpSearchService {

    /** Limite padrão de resultados por busca. */
    private static final int DEFAULT_LIMIT = 30;

    /** Delay mínimo entre requisições ao OpenSERP (rate limiting). */
    /** Máximo de retentativas com backoff exponencial. */
    private static final int MAX_RETRIES = 2;

    /** Backoff inicial para retry (1s * 2^attempt). */
    private static final long BASE_BACKOFF_MS = 1_000;

    private final RestTemplate restTemplate;
    private final Gson gson;
    private final Cache<String, JsonArray> openSerpCache;
    private final ContentTracker contentTracker;
    private final OpenSerpCircuitBreaker circuitBreaker;
    private final OpenSerpRateLimiter rateLimiter;
    private final OpenSerpResponseParser responseParser;

    /** Lista de endpoints configurados (com proxy opcional). */
    private final List<EndpointEntry> endpoints = new CopyOnWriteArrayList<>();

    /** Índice round-robin para rotação de endpoints. */
    private final AtomicInteger endpointIndex = new AtomicInteger(0);

    /** Endpoint único com URL normalizada e proxy opcional. */
    private record EndpointEntry(String baseUrl, String proxy) {
        String normalize() {
            return baseUrl.replace("/search", "").replaceAll("/$", "");
        }
    }

    /**
     * Construtor que inicializa o RestTemplate e a lista de endpoints.
     * Se {@code endpoints} estiver vazia, usa o {@code api.url} como fallback.
     */
    private final RedisCacheService redisCacheService;

    public OpenSerpSearchService(
            OpenSerpProxyProperties proxyProperties,
            RestTemplate openSerpRestTemplate,
            Cache<String, JsonArray> openSerpCache,
            Cache<String, String> openSerpHashCache,
            RedisCacheService redisCacheService,
            OpenSerpCircuitBreaker circuitBreaker,
            OpenSerpRateLimiter rateLimiter,
            OpenSerpResponseParser responseParser) {
        this.restTemplate = openSerpRestTemplate;
        this.gson = new GsonBuilder().setStrictness(Strictness.LENIENT).create();
        this.openSerpCache = openSerpCache;
        this.contentTracker = new ContentTracker(openSerpHashCache, "OpenSERP");
        this.redisCacheService = redisCacheService;
        this.circuitBreaker = circuitBreaker;
        this.rateLimiter = rateLimiter;
        this.responseParser = responseParser;

        // Carrega endpoints da configuração
        List<EndpointConfig> configured = proxyProperties.getEndpoints();
        if (configured != null && !configured.isEmpty()) {
            for (EndpointConfig ep : configured) {
                if (ep.getUrl() != null && !ep.getUrl().isBlank()) {
                    endpoints.add(new EndpointEntry(ep.getUrl(), ep.getProxy()));
                    log.debug("OpenSERP endpoint configurado: {} {}", ep.getUrl(),
                            ep.getProxy() != null ? "(com proxy: " + ep.getProxy().replaceAll(":.*@", ":***@") + ")" : "(sem proxy)");
                }
            }
        }

        // Fallback: usa api.url se nenhum endpoint foi configurado
        if (endpoints.isEmpty()) {
            String fallbackUrl = proxyProperties.getApiUrl();
            if (fallbackUrl == null || fallbackUrl.isBlank()) {
                fallbackUrl = "http://localhost:7000";
            }
            endpoints.add(new EndpointEntry(fallbackUrl, null));
            log.warn("OpenSERP endpoint fallback: {} (sem proxy)", fallbackUrl);
        }
    }

    /**
     * Tenta obter do cache L1 (Caffeine) ou L2 (Redis). Se não encontrado,
     * executa o {@code fetcher} para buscar, popula ambos os caches e retorna.
     * <p>
     * Padrão unificado usado por {@link #searchPerson}, {@link #search} e
     * {@link #searchDocuments} para evitar duplicação do mesmo fluxo.
     *
     * @param cacheKey  chave única para o cache
     * @param label     identificador amigável para logs
     * @param fetcher   função que executa a busca real (chamada apenas em cache miss)
     * @return JsonArray com resultados (nunca null)
     */
    private JsonArray getOrFetch(String cacheKey, String label, java.util.function.Supplier<JsonArray> fetcher) {
        // L1 - Caffeine local
        JsonArray cached = openSerpCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("Cache L1 hit para '{}' ({} resultados)", label, cached.size());
            return gson.fromJson(cached.toString(), JsonArray.class);
        }
        log.info("Cache miss para '{}' — buscando...", label);

        // L2 - Redis (leitura síncrona com timeout, nunca bloqueia >5s)
        String fromRedis = redisCacheService.get(cacheKey);
        if (fromRedis != null) {
            try {
                JsonArray parsed = gson.fromJson(fromRedis, JsonArray.class);
                openSerpCache.put(cacheKey, parsed);
                log.info("Cache L2 Redis hit para '{}' ({} resultados)", label, parsed.size());
                return parsed;
            } catch (Exception e) {
                log.debug("Redis parse falhou para '{}'", label);
            }
        }

        rateLimiter.acquire();
        JsonArray results = fetcher.get();
        JsonArray finalResults = results != null ? results : new JsonArray();

        contentTracker.trackContentChange(cacheKey, finalResults.toString());

        JsonArray cacheCopy = gson.fromJson(finalResults.toString(), JsonArray.class);
        openSerpCache.put(cacheKey, cacheCopy);
        redisCacheService.setAsync(cacheKey, 1800, finalResults.toString());
        log.info("Cache populado [{}] — {} resultados", cacheKey, cacheCopy.size());
        return finalResults;
    }

    /**
     * Retorna o próximo endpoint no esquema round-robin.
     */
    private EndpointEntry nextEndpoint() {
        int index = endpointIndex.getAndUpdate(i -> (i + 1) % endpoints.size());
        return endpoints.get(index);
    }

    /**
     * Busca resultados no Google através do OpenSERP.
     * <p>
     * A query é URL-encoded automaticamente. Com resiliência:
     * <ul>
     *   <li>Rate limiting — delay mínimo entre requisições</li>
     *   <li>Circuit breaker — para se detectar muitos CAPTCHAs consecutivos</li>
     *   <li>Retry com backoff exponencial — em caso de CAPTCHA</li>
     *   <li>Round-robin entre endpoints configurados</li>
     * </ul>
     *
     * @param name  termo de busca (nome da pessoa, empresa, etc.)
     * @param limit máximo de resultados a retornar
     * @return JsonArray com a lista de resultados (título, url, snippet, domínio)
     */
    public JsonArray searchPerson(String name, int limit) {
        if (circuitBreaker.isOpen()) {
            log.debug("OpenSERP circuit breaker aberto — pulando busca para '{}'", name);
            return new JsonArray();
        }

        String cacheKey = "person:" + name.toLowerCase().strip() + ":" + limit;

        return getOrFetch(cacheKey, name, () -> {
            String encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8);
            EndpointEntry ep = nextEndpoint();
            log.debug("OpenSERP usando endpoint: {} (proxy: {})", ep.normalize(),
                    ep.proxy() != null ? "configurado" : "direto");
            JsonArray results = fetchWithRetry(ep, encodedName, name, 0, limit);
            log.debug("OpenSERP: {} resultados para '{}' (limit={})",
                    results != null ? results.size() : 0, name, limit);
            return results;
        });
    }

    /**
     * Constrói a URL de busca incluindo proxy como query parameter se configurado.
     * Algumas forks do OpenSERP suportam {@code &proxy=...} para rotear via proxy.
     */
    private String buildSearchUrl(EndpointEntry ep, String encodedQuery, int limit) {
        String base = ep.normalize();
        String url = base + "/google/search?text=" + encodedQuery + "&limit=" + limit;
        if (ep.proxy() != null && !ep.proxy().isBlank()) {
            url += "&proxy=" + URLEncoder.encode(ep.proxy(), StandardCharsets.UTF_8);
        }
        return url;
    }

    /**
     * Busca com o limite padrão de 30 resultados.
     */
    public JsonArray searchPerson(String name) {
        return searchPerson(name, DEFAULT_LIMIT);
    }

    /**
     * Faz a requisição com retry exponencial em caso de CAPTCHA.
     * Em caso de falha, tenta o próximo endpoint automaticamente (failover).
     */
    private JsonArray fetchWithRetry(EndpointEntry ep, String encodedQuery,
                                      String label, int attempt, int limit) {
        String url = buildSearchUrl(ep, encodedQuery, limit);
        try {
            String raw = restTemplate.getForObject(url, String.class);
            circuitBreaker.reset();
            return responseParser.parse(raw, label);
        } catch (HttpClientErrorException e) {
            if (isCaptchaError(e)) {
                return handleCaptcha(encodedQuery, label, attempt, limit);
            }
            log.debug("OpenSERP erro HTTP para '{}': {} {} — {}",
                    label, e.getStatusCode(), e.getStatusText(), e.getResponseBodyAsString());
            return tryNextEndpoint(encodedQuery, label, attempt, limit);
        } catch (Exception e) {
            log.debug("OpenSERP falhou (requisição) para '{}': {}", label, e.getMessage());
            return tryNextEndpoint(encodedQuery, label, attempt, limit);
        }
    }

    /**
     * Tenta o próximo endpoint da lista (failover) se houver mais de um.
     */
    private JsonArray tryNextEndpoint(String encodedQuery, String label,
                                       int attempt, int limit) {
        if (endpoints.size() > 1 && attempt < endpoints.size()) {
            EndpointEntry next = nextEndpoint();
            log.debug("OpenSERP failover — tentando próximo endpoint: {}", next.normalize());
            return fetchWithRetry(next, encodedQuery, label + " (failover)", attempt + 1, limit);
        }
        return null;
    }

    /**
     * Verifica se a exceção corresponde a um erro de CAPTCHA do OpenSERP.
     */
    private boolean isCaptchaError(HttpClientErrorException e) {
        if (e.getStatusCode().value() != 429) return false;
        return parseCaptchaErrorBody(e);
    }

    /** Verifica se o body da resposta HTTP 429 contém indicação de CAPTCHA. */
    private boolean parseCaptchaErrorBody(HttpClientErrorException e) {
        try {
            String body = e.getResponseBodyAsString();
            if (body == null || body.isBlank()) return false;
            JsonElement root = gson.fromJson(body, JsonElement.class);
            if (root == null || !root.isJsonObject()) return false;
            JsonElement errorEl = root.getAsJsonObject().get("error");
            return errorEl != null && "captcha_detected".equals(errorEl.getAsString());
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Lida com CAPTCHA: incrementa contador, abre circuit breaker se exceder
     * threshold, faz retry com backoff exponencial e tenta próximo endpoint.
     */
    private JsonArray handleCaptcha(String encodedQuery, String label,
                                     int attempt, int limit) {
        boolean opened = circuitBreaker.recordCaptcha();
        if (opened) return null;

        // Tenta próximo endpoint primeiro (failover)
        if (endpoints.size() > 1) {
            EndpointEntry next = nextEndpoint();
            log.debug("OpenSERP CAPTCHA — alternando para próximo endpoint: {}", next.normalize());
            return fetchWithRetry(next, encodedQuery, label + " (failover)", attempt, limit);
        }

        // Sem failover disponível, faz retry com backoff
        if (attempt < MAX_RETRIES) {
            long backoff = BASE_BACKOFF_MS * (1L << attempt);
            log.debug("OpenSERP retry {} para '{}' após {}ms", attempt + 1, label, backoff);
            try {
                Thread.sleep(backoff);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            }
            // Pega o próximo endpoint para o retry (round-robin)
            EndpointEntry retryEp = nextEndpoint();
            return fetchWithRetry(retryEp, encodedQuery, label, attempt + 1, limit);
        }

        return null;
    }

    /**
     * Executa uma busca genérica e retorna os resultados.
     *
     * @param query termos da busca (já deve estar limpa)
     * @param label identificador para logs
     * @param limit máximo de resultados
     * @return JsonArray com resultados
     */
    public JsonArray search(String query, String label, int limit) {
        if (circuitBreaker.isOpen()) {
            log.debug("OpenSERP circuit breaker aberto — pulando busca '{}'", label);
            return new JsonArray();
        }

        String cacheKey = "search:" + query.toLowerCase().strip() + ":" + limit;

        return getOrFetch(cacheKey, label, () -> {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            EndpointEntry ep = nextEndpoint();
            return fetchWithRetry(ep, encodedQuery, label, 0, limit);
        });
    }

    /**
     * Busca documentos de vários tipos (PDF, DOC, DOCX, XLS, XLSX, PPT, etc.).
     *
     * @param name  nome da pessoa
     * @param limit máximo de resultados por tipo
     * @return JsonArray consolidado de documentos encontrados
     */
    public JsonArray searchDocuments(String name, int limit) {
        if (circuitBreaker.isOpen()) {
            log.debug("OpenSERP circuit breaker aberto — pulando busca de documentos para '{}'", name);
            return new JsonArray();
        }

        String cacheKey = "docs:" + name.toLowerCase().strip() + ":" + limit;

        return getOrFetch(cacheKey, "docs " + name, () -> {
            JsonArray all = new JsonArray();
            String[] fileTypes = {"pdf", "doc", "docx"};

            for (String fileType : fileTypes) {
                rateLimiter.acquire();
                String query = "\"" + name + "\" filetype:" + fileType;
                String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
                EndpointEntry ep = nextEndpoint();

                JsonArray results = fetchWithRetry(ep, encodedQuery,
                        name + " filetype:" + fileType, 0, limit);
                if (results != null && !results.isEmpty()) {
                    log.debug("OpenSERP docs ({}): {} resultados para '{}'", fileType, results.size(), name);
                    all.addAll(results);
                }
            }

            log.debug("OpenSERP documentos: {} resultados no total para '{}'", all.size(), name);
            return all;
        });
    }

    /**
     * Busca específica por redes sociais da pessoa.
     * Ex: {@code "Nome" (linkedin OR facebook OR instagram)}.
     */
    public JsonArray searchSocialMedia(String name, int limit) {
        if (circuitBreaker.isOpen()) {
            log.warn("OpenSERP circuit breaker aberto — pulando busca social para '{}'", name);
            return new JsonArray();
        }
        String cacheKey = "social:" + name.toLowerCase().strip() + ":" + limit;
        return getOrFetch(cacheKey, "social " + name, () -> {
            String query = "\"" + name + "\" (linkedin OR instagram OR facebook OR twitter OR github OR tiktok)";
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            EndpointEntry ep = nextEndpoint();
            return fetchWithRetry(ep, encodedQuery, name + " social", 0, limit);
        });
    }

    /**
     * Busca por informações profissionais da pessoa (LinkedIn, GitHub, portfolio).
     * Ex: {@code "Nome" (linkedin OR github OR "about.me" OR "portfolio")}.
     */
    public JsonArray searchProfessional(String name, int limit) {
        if (circuitBreaker.isOpen()) {
            log.warn("OpenSERP circuit breaker aberto — pulando busca profissional para '{}'", name);
            return new JsonArray();
        }
        String cacheKey = "prof:" + name.toLowerCase().strip() + ":" + limit;
        return getOrFetch(cacheKey, "prof " + name, () -> {
            String query = "\"" + name + "\" (linkedin OR github OR \"about.me\" OR lattes OR currículo OR CV)";
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            EndpointEntry ep = nextEndpoint();
            return fetchWithRetry(ep, encodedQuery, name + " professional", 0, limit);
        });
    }

    /**
     * Busca por informações de contato (e-mail, telefone).
     * Ex: {@code "Nome" (email OR contato OR telefone OR phone)}.
     */
    public JsonArray searchContact(String name, int limit) {
        if (circuitBreaker.isOpen()) {
            log.warn("OpenSERP circuit breaker aberto — pulando busca de contato para '{}'", name);
            return new JsonArray();
        }
        String cacheKey = "contact:" + name.toLowerCase().strip() + ":" + limit;
        return getOrFetch(cacheKey, "contact " + name, () -> {
            String query = "\"" + name + "\" (email OR contato OR contact OR telefone OR phone OR WhatsApp)";
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            EndpointEntry ep = nextEndpoint();
            return fetchWithRetry(ep, encodedQuery, name + " contact", 0, limit);
        });
    }

    /**
     * Busca por notícias mencionando a pessoa.
     * Ex: {@code "Nome" (notícia OR news OR release)}.
     */
    public JsonArray searchNews(String name, int limit) {
        if (circuitBreaker.isOpen()) {
            log.warn("OpenSERP circuit breaker aberto — pulando busca de notícias para '{}'", name);
            return new JsonArray();
        }
        String cacheKey = "news:" + name.toLowerCase().strip() + ":" + limit;
        return getOrFetch(cacheKey, "news " + name, () -> {
            String query = "\"" + name + "\" (notícia OR news OR release OR artigo OR article OR entrevista)";
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        EndpointEntry ep = nextEndpoint();
        return fetchWithRetry(ep, encodedQuery, name + " news", 0, limit);
        });
    }
}