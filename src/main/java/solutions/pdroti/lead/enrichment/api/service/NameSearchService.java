package solutions.pdroti.lead.enrichment.api.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Busca informações de uma pessoa pelo nome usando mecanismos de busca
 * públicos (DuckDuckGo — mais permissivo que Google para scraping).
 * Usado quando nenhum domínio é informado no enriquecimento.
 */
@Slf4j
@Service
public class NameSearchService {

    private static final int TIMEOUT_MS = 15_000;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final String SEARCH_URL = "https://html.duckduckgo.com/html/?q=";

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    private static final List<String> SOCIAL_KEYWORDS = List.of(
            "facebook.com", "linkedin.com", "instagram.com", "twitter.com", "x.com",
            "github.com", "gitlab.com", "youtube.com", "tiktok.com", "medium.com",
            "behance.net", "dribbble.com", "twitch.tv", "reddit.com", "t.me",
            "whatsapp.com", "discord.com", "calendly.com", "linktr.ee"
    );

    /** Busca pelo nome e retorna emails encontrados nos resultados. */
    public List<String> searchEmails(String name) {
        try {
            Document doc = searchName(name);
            Set<String> emails = new LinkedHashSet<>();

            for (Element snippet : doc.select(".result__snippet")) {
                var matcher = EMAIL_PATTERN.matcher(snippet.text());
                while (matcher.find()) {
                    String email = matcher.group().toLowerCase();
                    if (!email.contains("example.com") && !email.contains("@domain")) {
                        emails.add(email);
                    }
                }
            }
            return List.copyOf(emails);
        } catch (Exception e) {
            log.debug("Falha ao buscar emails para '{}': {}", name, e.getMessage());
            return List.of();
        }
    }

    /** Busca pelo nome e retorna links de redes sociais encontrados. */
    public List<String> searchSocialLinks(String name) {
        try {
            Document doc = searchName(name);
            Set<String> links = new LinkedHashSet<>();

            for (Element result : doc.select(".result__url")) {
                String url = result.attr("href");
                if (isSocialLink(url)) {
                    links.add(url);
                }
            }
            return List.copyOf(links);
        } catch (Exception e) {
            log.debug("Falha ao buscar social links para '{}': {}", name, e.getMessage());
            return List.of();
        }
    }

    /** Busca pelo nome e extrai menções ao nome completo nos resultados. */
    public List<String> searchNameMentions(String name) {
        try {
            Document doc = searchName(name);
            String lowerText = doc.text().toLowerCase();
            String lowerName = name.toLowerCase().strip();

            if (lowerText.contains(lowerName)) {
                return List.of("Nome completo encontrado: " + name);
            }
            return List.of();
        } catch (Exception e) {
            log.debug("Falha ao buscar menções para '{}': {}", name, e.getMessage());
            return List.of();
        }
    }

    /** Executa a busca no DuckDuckGo. */
    private Document searchName(String name) throws Exception {
        String query = URLEncoder.encode(name, StandardCharsets.UTF_8);
        return Jsoup.connect(SEARCH_URL + query)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .followRedirects(true)
                .get();
    }

    private boolean isSocialLink(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return SOCIAL_KEYWORDS.stream().anyMatch(lower::contains);
    }
}
