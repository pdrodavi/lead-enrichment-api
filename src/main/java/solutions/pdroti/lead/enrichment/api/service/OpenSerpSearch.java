package solutions.pdroti.lead.enrichment.api.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Cliente HTTP para a API do OpenSERP (self-hosted Google Search API).
 * <p>
 * Realiza buscas no Google de forma programática através de uma
 * instância self-hosted do OpenSERP. Executado pelo
 * {@link OpenSerpEnricher} durante o pipeline de enriquecimento.
 * <p>
 * Endpoint consultado:
 * <pre>
 * GET /google/search?text={query}&limit={n}
 * </pre>
 *
 * @see OpenSerpEnricher
 * @see <a href="https://github.com/serpapi/open-serp">OpenSERP</a>
 */
@Slf4j
@Service
public class OpenSerpSearch {

    /** Limite padrão de resultados por busca. */
    private static final int DEFAULT_LIMIT = 30;

    private final RestTemplate restTemplate;
    private final Gson gson;
    private final String baseUrl;

    /**
     * Construtor que inicializa o RestTemplate (gerenciado pelo Spring) e a URL base.
     * A URL é normalizada removendo sufixos como "/search" ou "/".
     *
     * @param baseUrl      URL base da API OpenSERP (padrão: http://localhost:7000)
     * @param restTemplate RestTemplate configurado com timeouts (injetado pelo Spring)
     */
    public OpenSerpSearch(
            @Value("${open-serp.api.url:http://localhost:7000}") String baseUrl,
            @Qualifier("openSerpRestTemplate") RestTemplate restTemplate) {
        this.baseUrl = baseUrl.replace("/search", "").replaceAll("/$", "");
        this.restTemplate = restTemplate;
        this.gson = new GsonBuilder().setStrictness(Strictness.LENIENT).create();
    }

    /**
     * Busca resultados no Google através do OpenSERP.
     * <p>
     * A query é URL-encoded automaticamente. Em caso de erro HTTP
     * ou resposta inválida, retorna um JsonArray vazio (não lança exceção).
     *
     * @param name  termo de busca (nome da pessoa, empresa, etc.)
     * @param limit máximo de resultados a retornar
     * @return JsonArray com a lista de resultados (título, url, snippet, domínio)
     */
    public JsonArray searchPerson(String name, int limit) {
        String encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8);
        String url = baseUrl + "/google/search?text=" + encodedName + "&limit=" + limit;

        JsonArray results = fetchAndParseResults(url, name);
        if (results != null && !results.isEmpty()) {
            log.debug("OpenSERP: {} resultados para '{}' (limit={})", results.size(), name, limit);
        } else {
            log.debug("OpenSERP: sem resultados para '{}' (limit={})", name, limit);
        }
        return results != null ? results : new JsonArray();
    }

    /**
     * Busca com o limite padrão de 30 resultados.
     *
     * @param name termo de busca
     * @return JsonArray com resultados
     * @see #searchPerson(String, int)
     */
    public JsonArray searchPerson(String name) {
        return searchPerson(name, DEFAULT_LIMIT);
    }

    /**
     * Executa a requisição HTTP e faz o parse seguro da resposta.
     * <p>
     * A API OpenSERP pode retornar JSON ou um formato texto/table.
     * Este método tenta JSON primeiro; se falhar, faz o parse do formato texto.
     *
     * @param url   URL completa para a requisição
     * @param label identificador para logs (geralmente o nome buscado)
     * @return JsonArray de resultados, ou null se não foi possível extrair nada
     */
    private JsonArray fetchAndParseResults(String url, String label) {
        String raw;
        try {
            raw = restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            log.warn("OpenSERP falhou (requisição) para '{}': {}", label, e.getMessage());
            return null;
        }

        if (raw == null || raw.isBlank()) {
            log.warn("OpenSERP retornou resposta vazia para '{}'", label);
            return null;
        }

        raw = raw.trim();

        // 1. Tenta parse como JSON primeiro
        JsonArray jsonResults = tryParseAsJson(raw);
        if (jsonResults != null) {
            return jsonResults;
        }

        // 2. Fallback: parse do formato texto/table do OpenSERP
        JsonArray textResults = parseTextResponse(raw);
        if (textResults != null && !textResults.isEmpty()) {
            log.info("OpenSERP: {} resultados extraídos do formato texto para '{}'",
                    textResults.size(), label);
            return textResults;
        }

        log.warn("OpenSERP: não foi possível extrair resultados de '{}'", label);
        return null;
    }

    /**
     * Tenta interpretar a resposta como JSON e extrair o array "results".
     */
    private JsonArray tryParseAsJson(String raw) {
        try {
            JsonElement root = gson.fromJson(raw, JsonElement.class);

            if (root == null || root.isJsonNull() || !root.isJsonObject()) {
                return null;
            }

            JsonObject obj = root.getAsJsonObject();
            if (!obj.has("results")) {
                return null;
            }

            JsonElement resultsEl = obj.get("results");
            if (resultsEl.isJsonArray()) {
                return resultsEl.getAsJsonArray();
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Padrão para linha <code>URL: https://...</code>
     */
    private static final Pattern URL_LINE = Pattern.compile("^URL:\\s*(\\S.*)$", Pattern.MULTILINE);

    /**
     * Padrão que captura cada bloco de resultado individual no formato texto.
     * Agrupa desde o header [N] até o próximo header [N] ou fim do texto.
     */
    private static final Pattern RESULT_BLOCK = Pattern.compile(
            "^\\[(\\d+)\\]\\s+(.+?)\\s+\\(([^)]+)\\)$\\n?(.*?)(?=^\\[\\d+\\]|\\z)",
            Pattern.MULTILINE | Pattern.DOTALL);

    /**
     * Parseia a resposta em formato texto/table do OpenSERP.
     * <p>
     * Formato esperado:
     * <pre>
     * Search: Pedro Davi
     * Engines: google
     *
     * Results
     *
     * [1] Título (dominio.com)
     * Descrição/snippet...
     * URL: https://...
     *
     * [2] Outro título (outro.com)
     * ...
     * </pre>
     */
    private JsonArray parseTextResponse(String raw) {
        JsonArray results = new JsonArray();

        Matcher matcher = RESULT_BLOCK.matcher(raw);
        while (matcher.find()) {
            String title = matcher.group(2).trim();
            String domain = matcher.group(3).trim();
            String body = matcher.group(4) != null ? matcher.group(4).trim() : "";

            // Extrai URL do corpo do bloco
            String url = "";
            String snippet = body;
            Matcher urlMatcher = URL_LINE.matcher(body);
            if (urlMatcher.find()) {
                url = urlMatcher.group(1).trim();
                // Remove a linha "URL:" do snippet
                snippet = body.substring(0, urlMatcher.start()).trim();
            }

            JsonObject item = new JsonObject();
            item.add("title", new JsonPrimitive(title));
            item.add("url", new JsonPrimitive(url));
            item.add("snippet", new JsonPrimitive(snippet));
            item.add("domain", new JsonPrimitive(domain));
            results.add(item);
        }

        return results;
    }

    /**
     * Busca documentos (PDF) que contenham o nome da pessoa.
     * <p>
     * Utiliza o operador {@code filetype:pdf} do Google para filtrar resultados.
     *
     * @param name  nome da pessoa para buscar nos documentos
     * @param limit máximo de resultados
     * @return JsonArray com resultados de documentos
     */
    public JsonArray searchDocuments(String name, int limit) {
        JsonArray all = new JsonArray();

        String fileType = "pdf";
            try {
                String query = "\"" + name + "\" filetype:" + fileType;
                String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
                String url = baseUrl + "/google/search?text=" + encodedQuery + "&limit=" + limit;

                JsonArray results = fetchAndParseResults(url, name + " filetype:" + fileType);
                if (results != null && !results.isEmpty()) {
                    log.debug("OpenSERP docs ({}): {} resultados para '{}'", fileType, results.size(), name);
                    all.addAll(results);
                }
            } catch (Exception e) {
                log.debug("OpenSERP docs falhou para filetype={} '{}': {}", fileType, name, e.getMessage());
            }

        log.info("OpenSERP documentos: {} resultados no total para '{}'", all.size(), name);
        return all;
    }
}