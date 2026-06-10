package solutions.pdroti.lead.enrichment.api.dto;

import java.util.List;
import java.util.Map;

/**
 * DTO com dados públicos extraídos do scraping de uma página web.
 * <p>
 * Inclui metadados da página (título, descrição, idioma, favicon,
 * charset, cor do tema), tecnologias detectadas, tags Open Graph,
 * Twitter Cards, headings h1 e links sociais.
 *
 * @param title        Título da página ({@code <title>})
 * @param description  Meta description
 * @param language     Idioma (atributo {@code lang} do {@code <html>})
 * @param favicon      URL do favicon
 * @param canonicalUrl URL canônica
 * @param themeColor   Cor do tema (meta theme-color)
 * @param charset      Charset declarado
 * @param technologies Lista de tecnologias detectadas (CMS, frameworks, analytics)
 * @param openGraph    Tags Open Graph (og:title, og:description, etc.)
 * @param twitterCards Tags Twitter Card (twitter:card, twitter:site, etc.)
 * @param h1Headings   Texto dos headings {@code <h1>}
 * @param socialLinks  Links encontrados na página
 */
public record ScrapedPageData(
        String title,
        String description,
        String language,
        String favicon,
        String canonicalUrl,
        String themeColor,
        String charset,
        List<String> technologies,
        Map<String, String> openGraph,
        Map<String, String> twitterCards,
        List<String> h1Headings,
        List<String> socialLinks
) {
    public static ScrapedPageData empty() {
        return new ScrapedPageData(null, null, null, null, null, null, null,
                List.of(), Map.of(), Map.of(), List.of(), List.of());
    }
}
