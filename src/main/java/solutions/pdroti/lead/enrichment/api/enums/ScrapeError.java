package solutions.pdroti.lead.enrichment.api.enums;

import solutions.pdroti.lead.enrichment.api.util.ErrorMatcher;

import javax.net.ssl.SSLException;
import java.net.SocketTimeoutException;

/**
 * Classifica erros de scraping web por tipo, permitindo respostas
 * diferenciadas no pipeline de enriquecimento.
 * <p>
 * Extraído do {@code TechScraperService} para manter a responsabilidade
 * única e permitir reuso em outros contextos de scraping.
 */
public enum ScrapeError {

    TIMEOUT("Timeout", (e, msg) ->
            e instanceof SocketTimeoutException || msg.contains("timeout") || msg.contains("timed out")),
    CLOUDFLARE("Cloudflare Protection", (e, msg) ->
            msg.contains("cloudflare") || msg.contains("1020") || msg.contains("challenge")),
    ACCESS_DENIED("Access Denied (403)", (e, msg) ->
            msg.contains("403") || msg.contains("forbidden")),
    SSL_HANDSHAKE("SSL Handshake Failed", (e, msg) ->
            e instanceof SSLException || msg.contains("ssl") || msg.contains("handshake")),
    DOMAIN_NOT_FOUND("Domain Not Found", (e, msg) ->
            msg.contains("unknowhost") || msg.contains("unknownhost") || msg.contains("no such host")),
    PAGE_NOT_FOUND("Page Not Found (404)", (e, msg) ->
            msg.contains("404") || msg.contains("not found"));

    private final String description;
    private final ErrorMatcher matcher;

    ScrapeError(String description, ErrorMatcher matcher) {
        this.description = description;
        this.matcher = matcher;
    }

    public String format() {
        return "ScrapeError: " + description;
    }

    /**
     * Classifica uma exceção retornando a descrição do erro correspondente.
     *
     * @param e       exceção lançada
     * @param message mensagem de erro (pode ser {@code e.getMessage()})
     * @return descrição formatada do erro (ex: "ScrapeError: Timeout")
     */
    public static String classify(Exception e, String message) {
        String msg = message != null ? message.toLowerCase() : "";
        for (var error : values()) {
            if (error.matcher.matches(e, msg)) {
                return error.format();
            }
        }
        return "ScrapeError: " + e.getClass().getSimpleName();
    }
}
