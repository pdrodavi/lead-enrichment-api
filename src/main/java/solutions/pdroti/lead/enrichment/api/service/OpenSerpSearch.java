package solutions.pdroti.lead.enrichment.api.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Cliente HTTP para a API do OpenSERP (self-hosted Google Search API).
 * <p>
 * Realiza buscas no Google de forma programática através de uma
 * instância self-hosted do OpenSERP. As consultas são usadas
 * quando não há domínio conhecido para o lead.
 * <p>
 * Endpoint consultado:
 * <pre>
 * GET /google/search?text={query}&limit={n}
 * </pre>
 *
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
            @Value("${serper.api.url:http://localhost:7000}") String baseUrl,
            RestTemplate restTemplate) {
        this.baseUrl = baseUrl.replace("/search", "").replaceAll("/$", "");
        this.restTemplate = restTemplate;
        this.gson = new Gson();
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
     * @throws Exception se houver falha de conexão ou parse do JSON
     */
    public JsonArray searchPerson(String name, int limit) throws Exception {
        String encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8);
        String url = baseUrl + "/google/search?text=" + encodedName + "&limit=" + limit;

        try {
            String json = restTemplate.getForObject(url, String.class);
            if (json == null) {
                log.warn("OpenSERP retornou resposta vazia para '{}'", name);
                return new JsonArray();
            }
            JsonObject root = gson.fromJson(json, JsonObject.class);

            JsonArray results = root.getAsJsonArray("results");
            if (results == null) {
                log.debug("OpenSERP sem resultados para '{}'", name);
                return new JsonArray();
            }

            log.debug("OpenSERP: {} resultados para '{}' (limit={})", results.size(), name, limit);
            return results;
        } catch (Exception e) {
            log.warn("OpenSERP falhou para '{}': {}", name, e.getMessage());
            return new JsonArray();
        }
    }

    /**
     * Busca com o limite padrão de 30 resultados.
     *
     * @param name termo de busca
     * @return JsonArray com resultados
     * @throws Exception se houver falha de conexão
     * @see #searchPerson(String, int)
     */
    public JsonArray searchPerson(String name) throws Exception {
        return searchPerson(name, DEFAULT_LIMIT);
    }

    /**
     * Busca documentos (PDF, DOC, XLS, PPT, etc.) que contenham o nome da pessoa.
     * <p>
     * Utiliza o operador {@code filetype:} do Google para filtrar resultados
     * por tipo de arquivo. Executa uma busca separada para cada tipo.
     *
     * @param name  nome da pessoa para buscar nos documentos
     * @param limit máximo de resultados por tipo de arquivo
     * @return JsonArray mesclado com resultados de todos os tipos de documento
     */
    public JsonArray searchDocuments(String name, int limit) {
        String[] fileTypes = {"pdf"};
        JsonArray all = new JsonArray();

        for (String fileType : fileTypes) {
            try {
                String query = "\"" + name + "\" filetype:" + fileType;
                String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
                String url = baseUrl + "/google/search?text=" + encodedQuery + "&limit=" + limit;

                String json = restTemplate.getForObject(url, String.class);
                if (json == null) {
                    log.debug("OpenSERP docs ({}): resposta vazia para '{}'", fileType, name);
                    continue;
                }
                JsonObject root = gson.fromJson(json, JsonObject.class);
                JsonArray results = root.getAsJsonArray("results");
                if (results != null && !results.isEmpty()) {
                    log.debug("OpenSERP docs ({}): {} resultados para '{}'", fileType, results.size(), name);
                    all.addAll(results);
                }
            } catch (Exception e) {
                log.debug("OpenSERP docs falhou para filetype={} '{}': {}", fileType, name, e.getMessage());
            }
        }

        log.info("OpenSERP documentos: {} resultados no total para '{}'", all.size(), name);
        return all;
    }
}
