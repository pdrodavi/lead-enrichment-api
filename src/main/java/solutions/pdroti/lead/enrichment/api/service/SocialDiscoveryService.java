package solutions.pdroti.lead.enrichment.api.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class SocialDiscoveryService {

    private static final int TIMEOUT_MS = 10_000;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";

    @SuppressWarnings("unused")
    private static final Set<String> TRACKING_PARAMS = Set.of(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "fbclid", "gclid", "gclsrc", "dclid", "msclkid",
            "twclid", "igshid", "li_fat_id", "mc_cid", "mc_eid",
            "ref", "source", "si", "s"
    );

    public List<String> discoverSocialLinks(String domain) {
        List<String> socialLinks = new ArrayList<>();

        try {
            String url = domain.startsWith("http") ? domain : "https://" + domain;

            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .get();

            doc.select("a[href]").forEach(anchor -> {
                String href = anchor.attr("href").toLowerCase();
                String normalized = tryNormalize(anchor.attr("href"));

                if (normalized != null) {
                    if (containsSocialDomain(href, "facebook.com") && !socialLinks.contains(normalized)) {
                        socialLinks.add(normalized);
                    } else if (containsSocialDomain(href, "linkedin.com") && !socialLinks.contains(normalized)) {
                        socialLinks.add(normalized);
                    } else if (containsSocialDomain(href, "instagram.com") && !socialLinks.contains(normalized)) {
                        socialLinks.add(normalized);
                    } else if (containsSocialDomain(href, "twitter.com") || containsSocialDomain(href, "x.com")) {
                        if (!socialLinks.contains(normalized)) {
                            socialLinks.add(normalized);
                        }
                    }
                }
            });

        } catch (Exception e) {
            // Silently returns empty list if domain is unreachable
        }

        return socialLinks;
    }

    private boolean containsSocialDomain(String href, String domain) {
        return href.contains(domain);
    }

    private String tryNormalize(String rawUrl) {
        try {
            URI uri = new URI(rawUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            String path = uri.getPath();

            if (scheme == null && host == null) {
                if (rawUrl.startsWith("//")) {
                    return tryNormalize("https:" + rawUrl);
                }
                return null;
            }

            StringBuilder normalized = new StringBuilder();
            normalized.append(scheme != null ? scheme : "https").append("://");
            normalized.append(host);
            if (path != null && !path.isEmpty() && !path.equals("/")) {
                normalized.append(path);
            }
            normalized.append("/");

            return normalized.toString();

        } catch (Exception e) {
            return null;
        }
    }
}
