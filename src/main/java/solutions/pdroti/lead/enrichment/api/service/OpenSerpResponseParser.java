package solutions.pdroti.lead.enrichment.api.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser de respostas do OpenSERP.
 * <p>
 * Suporta dois formatos:
 * <ul>
 *   <li><b>JSON</b> — formato estruturado padrão</li>
 *   <li><b>Texto/table</b> — formato legado com blocos {@code [N] Título (domínio)}</li>
 * </ul>
 */
@Slf4j
@Component
public class OpenSerpResponseParser {

    private static final Pattern URL_LINE = Pattern.compile("^URL:\\s*(\\S.*)$", Pattern.MULTILINE);

    private static final Pattern RESULT_BLOCK = Pattern.compile(
            "^\\[(\\d+)\\]\\s+(.+?)\\s+\\(([^)]+)\\)$\\n?(.*?)(?=^\\[\\d+\\]|\\z)",
            Pattern.MULTILINE | Pattern.DOTALL);

    /**
     * Interpreta a resposta bruta do OpenSERP e extrai um JsonArray de resultados.
     *
     * @param raw   resposta bruta (JSON ou texto)
     * @param label identificador para logs
     * @return JsonArray com resultados, ou null se não foi possível extrair
     */
    public JsonArray parse(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            log.debug("OpenSERP resposta vazia para '{}'", label);
            return null;
        }

        String trimmed = raw.trim();

        // 1. Tenta JSON primeiro
        JsonArray jsonResults = tryParseJson(trimmed);
        if (jsonResults != null) return jsonResults;

        // 2. Fallback: formato texto
        JsonArray textResults = parseText(trimmed);
        if (textResults != null && !textResults.isEmpty()) {
            log.debug("OpenSERP: {} resultados extraídos do formato texto para '{}'",
                    textResults.size(), label);
            return textResults;
        }

        log.debug("OpenSERP: não foi possível extrair resultados de '{}'", label);
        return null;
    }

    /** Tenta interpretar a resposta como JSON e extrair o array "results". */
    private JsonArray tryParseJson(String raw) {
        try {
            JsonElement root = com.google.gson.JsonParser.parseString(raw);
            if (root == null || root.isJsonNull() || !root.isJsonObject()) return null;

            JsonObject obj = root.getAsJsonObject();
            if (!obj.has("results")) return null;

            JsonElement resultsEl = obj.get("results");
            return resultsEl.isJsonArray() ? resultsEl.getAsJsonArray() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Parseia a resposta em formato texto/table do OpenSERP. */
    private JsonArray parseText(String raw) {
        JsonArray results = new JsonArray();
        Matcher matcher = RESULT_BLOCK.matcher(raw);
        while (matcher.find()) {
            String title = matcher.group(2).trim();
            String domain = matcher.group(3).trim();
            String body = matcher.group(4) != null ? matcher.group(4).trim() : "";

            String url = "";
            String snippet = body;
            Matcher urlMatcher = URL_LINE.matcher(body);
            if (urlMatcher.find()) {
                url = urlMatcher.group(1).trim();
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
}
