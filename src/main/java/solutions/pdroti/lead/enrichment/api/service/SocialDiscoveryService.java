package solutions.pdroti.lead.enrichment.api.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import solutions.pdroti.lead.enrichment.api.dto.SocialProfileData;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    // ========== Scraping de perfis de redes sociais ==========

    /** Mapa de identificadores de domínio → nome da plataforma. */
    private static final Map<String, String> PLATFORM_NAMES = new LinkedHashMap<>();
    static {
        PLATFORM_NAMES.put("github.com", "GitHub");
        PLATFORM_NAMES.put("gitlab.com", "GitLab");
        PLATFORM_NAMES.put("bitbucket.org", "Bitbucket");
        PLATFORM_NAMES.put("linkedin.com", "LinkedIn");
        PLATFORM_NAMES.put("instagram.com", "Instagram");
        PLATFORM_NAMES.put("facebook.com", "Facebook");
        PLATFORM_NAMES.put("fb.com", "Facebook");
        PLATFORM_NAMES.put("twitter.com", "X (Twitter)");
        PLATFORM_NAMES.put("x.com", "X (Twitter)");
        PLATFORM_NAMES.put("youtube.com", "YouTube");
        PLATFORM_NAMES.put("youtu.be", "YouTube");
        PLATFORM_NAMES.put("tiktok.com", "TikTok");
        PLATFORM_NAMES.put("medium.com", "Medium");
        PLATFORM_NAMES.put("behance.net", "Behance");
        PLATFORM_NAMES.put("dribbble.com", "Dribbble");
        PLATFORM_NAMES.put("twitch.tv", "Twitch");
        PLATFORM_NAMES.put("reddit.com", "Reddit");
        PLATFORM_NAMES.put("stackoverflow.com", "Stack Overflow");
        PLATFORM_NAMES.put("pinterest.com", "Pinterest");
        PLATFORM_NAMES.put("calendly.com", "Calendly");
        PLATFORM_NAMES.put("linktr.ee", "Linktree");
        PLATFORM_NAMES.put("threads.net", "Threads");
        PLATFORM_NAMES.put("slideshare.net", "SlideShare");
        PLATFORM_NAMES.put("scribd.com", "Scribd");
        PLATFORM_NAMES.put("issuu.com", "Issuu");
        PLATFORM_NAMES.put("discord.com", "Discord");
        PLATFORM_NAMES.put("discord.gg", "Discord");
        PLATFORM_NAMES.put("telegram.org", "Telegram");
        PLATFORM_NAMES.put("t.me", "Telegram");
        PLATFORM_NAMES.put("whatsapp.com", "WhatsApp");
    }

    /**
     * Tenta fazer scraping de cada URL de rede social encontrada
     * e retorna dados estruturados (título, descrição). Erros são
     * ignorados silenciosamente — cada perfil que falhar é pulado.
     */
    public List<SocialProfileData> scrapeSocialProfiles(List<String> socialUrls) {
        if (socialUrls == null || socialUrls.isEmpty()) {
            return List.of();
        }

        List<SocialProfileData> results = new ArrayList<>();

        for (String url : socialUrls) {
            try {
                String platform = identifyPlatform(url);
                Document doc = fetchSocialPage(url);
                String title = extractPageTitle(doc);
                String description = extractMetaDescription(doc);

                results.add(new SocialProfileData(url, platform, title, description));
                log.info("Perfil scrapy: {} — {}", platform, title);
            } catch (Exception e) {
                log.debug("Falha ao scrapear {}: {}", url, e.getMessage());
            }
        }

        return List.copyOf(results);
    }

    /** Identifica o nome da plataforma a partir da URL. */
    private String identifyPlatform(String url) {
        String lower = url.toLowerCase();
        return PLATFORM_NAMES.entrySet().stream()
                .filter(e -> lower.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("Rede Social");
    }

    /** Faz fetch da página social com User-Agent realista. */
    private Document fetchSocialPage(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .followRedirects(true)
                .get();
    }

    /** Extrai o título da página. */
    private String extractPageTitle(Document doc) {
        return Optional.ofNullable(doc.title())
                .map(String::strip)
                .filter(s -> !s.isBlank())
                .orElse(null);
    }

    /** Extrai a meta description ou og:description. */
    private String extractMetaDescription(Document doc) {
        // Tenta og:description primeiro (mais descritivo)
        Element ogDesc = doc.selectFirst("meta[property=og:description]");
        if (ogDesc != null) {
            String content = ogDesc.attr("content");
            if (content != null && !content.isBlank()) {
                return content.strip();
            }
        }

        // Fallback para meta description padrão
        Element metaDesc = doc.selectFirst("meta[name=description]");
        if (metaDesc != null) {
            String content = metaDesc.attr("content");
            if (content != null && !content.isBlank()) {
                return content.strip();
            }
        }

        return null;
    }
}