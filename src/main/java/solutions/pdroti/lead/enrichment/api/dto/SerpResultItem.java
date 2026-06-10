package solutions.pdroti.lead.enrichment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Item individual de resultado da busca no OpenSERP (Google Search).
 * <p>
 * Cada resultado contém o link, título, trecho de contexto (snippet)
 * e o domínio de origem extraído automaticamente da URL.
 */
@Schema(description = "Resultado individual da busca no Google via OpenSERP")
public record SerpResultItem(

        @Schema(description = "Posição no ranking da busca", example = "1")
        int position,

        @Schema(description = "URL completa do resultado", example = "https://example.com/sobre")
        String url,

        @Schema(description = "Título da página", example = "João Silva — Sobre")
        String title,

        @Schema(description = "Trecho de contexto onde o nome foi encontrado",
                example = "João Silva é engenheiro de software especializado em...")
        String snippet,

        @Schema(description = "Domínio extraído da URL", example = "example.com")
        String domain
) {

    /** Extrai o domínio de uma URL. */
    private static String extractDomain(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            String lower = url.toLowerCase();
            // Remove protocolo
            String afterProtocol = lower.startsWith("https://") ? lower.substring(8)
                    : lower.startsWith("http://") ? lower.substring(7)
                    : lower;
            // Remove www. e path/query/fragment
            int slashIdx = afterProtocol.indexOf('/');
            String host = slashIdx > 0 ? afterProtocol.substring(0, slashIdx) : afterProtocol;
            return host.replaceFirst("^www\\.", "");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Cria um SerpResultItem extraindo o domínio automaticamente da URL.
     *
     * @param position posição no ranking
     * @param url      URL do resultado
     * @param title    título da página
     * @param snippet  trecho de contexto
     * @return item estruturado com domínio preenchido
     */
    public static SerpResultItem fromSearchResult(int position, String url, String title, String snippet) {
        return new SerpResultItem(position, url, title, snippet, extractDomain(url));
    }
}
