package solutions.pdroti.lead.enrichment.api.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Service
public class TechScraperService {

    private static final int TIMEOUT_MS = 10_000;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";

    private static final Map<String, List<String>> SIGNATURES = Map.ofEntries(
            Map.entry("WordPress", List.of("wp-content", "wp-includes", "wordpress")),
            Map.entry("Google Tag Manager", List.of("gtm.js", "googletagmanager.com")),
            Map.entry("Google Analytics", List.of("analytics.js", "ga.js", "gtag")),
            Map.entry("Cloudflare", List.of("cloudflare", "__cfduid")),
            Map.entry("jQuery", List.of("jquery")),
            Map.entry("Bootstrap", List.of("bootstrap.css", "bootstrap.min.css", "bootstrap.js", "bootstrap.min.js")),
            Map.entry("React", List.of("react.js", "react.development.js", "react.production.min.js", "_next/static")),
            Map.entry("Vue.js", List.of("vue.js", "vue.min.js")),
            Map.entry("Angular", List.of("angular.js", "angular.min.js", "ng-app")),
            Map.entry("Laravel", List.of("laravel", "csrf-token")),
            Map.entry("Shopify", List.of("shopify.com", "myshopify.com", "/cdn/shop/")),
            Map.entry("Wix", List.of("wix.com", "wixstatic.com")),
            Map.entry("Cloudflare Protection", List.of("__cf_chl_tk", "cf_chl_prog", "challenge-platform"))
    );

    public List<String> scrapeTechnologies(String domain) {
        List<String> technologies = new ArrayList<>();

        try {
            String url = domain.startsWith("http") ? domain : "https://" + domain;

            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .get();

            String html = doc.html().toLowerCase();

            for (Map.Entry<String, List<String>> entry : SIGNATURES.entrySet()) {
                for (String signature : entry.getValue()) {
                    if (html.contains(signature)) {
                        technologies.add(entry.getKey());
                        break;
                    }
                }
            }

            detectByScriptSrc(doc, technologies);
            detectByMetaTags(doc, technologies);

        } catch (Exception e) {
            handleScrapeError(domain, e, technologies);
        }

        return technologies;
    }

    private void detectByScriptSrc(Document doc, List<String> technologies) {
        doc.select("script[src]").forEach(script -> {
            String src = script.attr("src").toLowerCase();
            if (src.contains("facebook") || src.contains("fb")) {
                if (!technologies.contains("Facebook Pixel")) {
                    technologies.add("Facebook Pixel");
                }
            }
            if (src.contains("hotjar")) {
                if (!technologies.contains("Hotjar")) {
                    technologies.add("Hotjar");
                }
            }
            if (src.contains("linkedin")) {
                if (!technologies.contains("LinkedIn Insights")) {
                    technologies.add("LinkedIn Insights");
                }
            }
        });
    }

    private void detectByMetaTags(Document doc, List<String> technologies) {
        doc.select("meta[name]").forEach(meta -> {
            String name = meta.attr("name").toLowerCase();
            String content = meta.attr("content").toLowerCase();

            if (name.contains("generator") && content.contains("wordpress")) {
                if (!technologies.contains("WordPress")) {
                    technologies.add("WordPress");
                }
            }
            if (name.contains("generator") && content.contains("laravel")) {
                if (!technologies.contains("Laravel")) {
                    technologies.add("Laravel");
                }
            }
        });
    }

    private void handleScrapeError(String domain, Exception e, List<String> technologies) {
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

        if (e instanceof java.net.SocketTimeoutException || message.contains("timeout") || message.contains("timed out")) {
            technologies.add("ScrapeError: Timeout");
        } else if (message.contains("cloudflare") || message.contains("1020") || message.contains("challenge")) {
            technologies.add("ScrapeError: Cloudflare Protection");
        } else if (message.contains("403") || message.contains("forbidden")) {
            technologies.add("ScrapeError: Access Denied (403)");
        } else if (message.contains("ssl") || message.contains("handshake")) {
            technologies.add("ScrapeError: SSL Handshake Failed");
        } else if (message.contains("unknowhost") || message.contains("unknownhost") || message.contains("no such host")) {
            technologies.add("ScrapeError: Domain Not Found");
        } else if (message.contains("404") || message.contains("not found")) {
            technologies.add("ScrapeError: Page Not Found (404)");
        } else {
            technologies.add("ScrapeError: " + e.getClass().getSimpleName());
        }
    }

    public Map<String, Object> scrape(String url) {
        Map<String, Object> data = new HashMap<>();
        try {
            Document doc = Jsoup.connect(url).get();
            String html = doc.html().toLowerCase();
            List<String> techs = new ArrayList<>();
            if (html.contains("wp-content")) techs.add("WordPress");
            if (html.contains("googletagmanager")) techs.add("GTM");
            if (html.contains("react")) techs.add("React");
            data.put("technologies", techs);
            data.put("socialLinks", doc.select("a[href*=facebook.com], a[href*=linkedin.com]").eachAttr("href"));
        } catch (Exception e) {
            data.put("error", "Scraping failed");
        }
        return data;
    }
}
