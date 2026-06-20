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

    /** Header de bloco no formato texto: [N] Título (domínio) */
    private static final Pattern HEADER_LINE = Pattern.compile(
            "^\\[(\\d+)\\] (.+) \\(([^)]+)\\)$");

    private static final Pattern URL_LINE = Pattern.compile("^URL:\\s*(\\S.*)$", Pattern.MULTILINE);

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
        String[] lines = raw.split("\\n");
        StringBuilder body = new StringBuilder();
        String currentTitle = null;
        String currentDomain = null;

        for (String line : lines) {
            Matcher headerMatcher = HEADER_LINE.matcher(line);
            if (headerMatcher.find()) {
                // Finaliza bloco anterior
                if (currentTitle != null) {
                    addTextResult(results, currentTitle, currentDomain, body.toString());
                }
                currentTitle = headerMatcher.group(2).trim();
                currentDomain = headerMatcher.group(3).trim();
                body = new StringBuilder();
            } else if (currentTitle != null) {
                // Linha de corpo do bloco atual
                if (body.length() > 0) body.append("\n");
                body.append(line);
            }
        }
        // Último bloco
        if (currentTitle != null) {
            addTextResult(results, currentTitle, currentDomain, body.toString());
        }
        return results;
    }

    private void addTextResult(JsonArray results, String title, String domain, String body) {
        String url = "";
        String snippet = body.trim();
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
}
