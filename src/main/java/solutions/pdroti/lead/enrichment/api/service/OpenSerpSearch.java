package solutions.pdroti.lead.enrichment.api.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

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

    /** Timeout para conexão, leitura e escrita (20 segundos). */
    private static final int TIMEOUT_SECONDS = 20;

    private final OkHttpClient client;
    private final Gson gson;
    private final String baseUrl;

    /**
     * Construtor que inicializa o cliente HTTP e a URL base.
     * A URL é normalizada removendo sufixos como "/search" ou "/".
     *
     * @param baseUrl URL base da API OpenSERP (padrão: http://localhost:7000)
     */
    public OpenSerpSearch(@Value("${serper.api.url:http://localhost:7000}") String baseUrl) {
        this.baseUrl = baseUrl.replace("/search", "").replaceAll("/$", "");
        this.client = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
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

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("OpenSERP retornou HTTP {} para '{}'", response.code(), name);
                return new JsonArray();
            }
            String json = response.body().string();
            JsonObject root = gson.fromJson(json, JsonObject.class);

            JsonArray results = root.getAsJsonArray("results");
            if (results == null) {
                log.debug("OpenSERP sem resultados para '{}'", name);
                return new JsonArray();
            }

            log.debug("OpenSERP: {} resultados para '{}' (limit={})", results.size(), name, limit);
            return results;
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
}
