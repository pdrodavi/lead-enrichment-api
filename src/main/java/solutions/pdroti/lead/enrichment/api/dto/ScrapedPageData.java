package solutions.pdroti.lead.enrichment.api.dto;

import java.util.List;
import java.util.Map;

/** Dados públicos extraídos do scrape de uma página. */
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
