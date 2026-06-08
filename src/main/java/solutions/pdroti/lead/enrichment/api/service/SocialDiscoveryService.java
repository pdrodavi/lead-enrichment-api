package solutions.pdroti.lead.enrichment.api.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class SocialDiscoveryService {

    private static final int TIMEOUT_MS = 10_000;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";
    private static final String HTTPS_PREFIX = "https://";
    private static final String PROTOCOL_RELATIVE_PREFIX = "//";

    private static final List<String> SOCIAL_DOMAINS = List.of(
            "facebook.com", "fb.com",
            "linkedin.com",
            "instagram.com",
            "twitter.com", "x.com",
            "youtube.com", "youtu.be",
            "tiktok.com",
            "pinterest.com", "pinterest.ca",
            "snapchat.com",
            "reddit.com",
            "tumblr.com",
            "whatsapp.com",
            "telegram.org", "t.me",
            "discord.com", "discord.gg",
            "twitch.tv",
            "medium.com",
            "behance.net",
            "dribbble.com",
            "github.com",
            "gitlab.com",
            "bitbucket.org",
            "stackoverflow.com",
            "slideshare.net",
            "scribd.com",
            "issuu.com",
            "calendly.com",
            "linktr.ee",
            "threads.net"
    );

    /** Busca links de redes sociais no HTML do domínio informado. */
    public List<String> discoverSocialLinks(String domain) {
        try {
            Document doc = fetchPage(domain);
            return extractSocialLinks(doc);
        } catch (Exception e) {
            log.debug("Falha ao buscar social links de {}: {}", domain, e.getMessage());
            return List.of();
        }
    }

    /** Faz o fetch da página com User-Agent e timeout configurados. */
    private Document fetchPage(String domain) throws IOException {
        String url = ensureUrlScheme(domain);
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .followRedirects(true)
                .get();
    }

    /** Garante que o domínio tenha scheme (https por padrão). */
    private String ensureUrlScheme(String domain) {
        return domain.startsWith("http") ? domain : HTTPS_PREFIX + domain;
    }

    /** Extrai links sociais únicos do HTML, normalizando URLs para dedup. */
    private List<String> extractSocialLinks(Document doc) {
        Set<String> uniqueLinks = new LinkedHashSet<>();

        doc.select("a[href]").forEach(anchor -> {
            String href = anchor.attr("href");
            String normalized = tryNormalize(href);
            if (isSocialLink(normalized)) {
                uniqueLinks.add(normalized);
            }
        });

        return List.copyOf(uniqueLinks);
    }

    /** Retorna true se a URL contiver algum domínio de rede social. */
    private boolean isSocialLink(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return SOCIAL_DOMAINS.stream().anyMatch(lower::contains);
    }

    /** Tenta normalizar URL relativa/absoluta para formato canônico (https://host/path/). */
    private String tryNormalize(String rawUrl) {
        try {
            URI uri = new URI(rawUrl);
            return normalizeUri(uri, rawUrl);
        } catch (Exception e) {
            return null;
        }
    }

    /** Normaliza URI para formato canônico com scheme, host e path. */
    private String normalizeUri(URI uri, String rawUrl) {
        if (uri.getScheme() == null && uri.getHost() == null) {
            return handleProtocolRelative(rawUrl);
        }

        String scheme = resolveScheme(uri);
        String host = uri.getHost();
        String path = normalizePath(uri.getPath());

        return scheme + host + path;
    }

    /** Converte URL protocol-relative (//host/path) para https://host/path. */
    private String handleProtocolRelative(String rawUrl) {
        if (rawUrl.startsWith(PROTOCOL_RELATIVE_PREFIX)) {
            return tryNormalize(HTTPS_PREFIX + rawUrl.substring(2));
        }
        return null;
    }

    /** Retorna o scheme da URI ou https como fallback. */
    private String resolveScheme(URI uri) {
        return uri.getScheme() != null ? uri.getScheme() + "://" : HTTPS_PREFIX;
    }

    /** Garante que o path termine com / para normalização. */
    private String normalizePath(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return "/";
        }
        return path.endsWith("/") ? path : path + "/";
    }
}