package solutions.pdroti.lead.enrichment.api.service;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import solutions.pdroti.lead.enrichment.api.config.SocialDiscoveryProperties;
import solutions.pdroti.lead.enrichment.api.dto.SocialProfileData;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class SocialDiscoveryService {

    private static final int TIMEOUT_MS = 10_000;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";
    private static final String HTTPS_PREFIX = "https://";
    private static final String PROTOCOL_RELATIVE_PREFIX = "//";

    private final SocialDiscoveryProperties properties;
    private final Cache<String, List<String>> socialLinksCache;
    private final Executor enrichmentExecutor;

    public SocialDiscoveryService(SocialDiscoveryProperties properties,
                                   Cache<String, List<String>> socialLinksCache,
                                   @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
                                   java.util.concurrent.Executor enrichmentExecutor) {
        this.properties = properties;
        this.socialLinksCache = socialLinksCache;
        this.enrichmentExecutor = enrichmentExecutor;
    }

    /** Busca links de redes sociais no HTML do domínio informado. */
    public List<String> discoverSocialLinks(String domain) {
        if (domain == null || domain.isBlank()) return List.of();

        // Tenta cache primeiro
        String cacheKey = domain.toLowerCase().strip();
        List<String> cached = socialLinksCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("SocialLinks cache hit para {}", domain);
            return cached;
        }

        try {
            Document doc = fetchPage(domain);
            List<String> links = extractSocialLinks(doc);
            socialLinksCache.put(cacheKey, links);
            return links;
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
        return properties.getSocialDomains().stream().anyMatch(lower::contains);
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

    /**
     * Retorna a lista de domínios de redes sociais conhecidos.
     * Usado pelo {@link LeadService} para classificar links encontrados via OpenSERP.
     *
     * @return lista imutável de domínios sociais
     */
    public List<String> getSocialDomains() {
        return properties.getSocialDomains();
    }

    /** Mapa de identificadores de domínio → nome da plataforma. */
    // (externalizado para application.yml → social-discovery.platform-names)

    /**
     * Tenta fazer scraping de cada URL de rede social encontrada em paralelo
     * e retorna dados estruturados (título, descrição). Erros são
     * ignorados silenciosamente — cada perfil que falhar é pulado.
     */
    public List<SocialProfileData> scrapeSocialProfiles(List<String> socialUrls) {
        if (socialUrls == null || socialUrls.isEmpty()) {
            return List.of();
        }

        // Scraping paralelo — cada perfil social é scaneado simultaneamente
        var futures = socialUrls.stream()
                .map(url -> CompletableFuture.supplyAsync(
                        () -> scrapeProfileSafely(url), enrichmentExecutor))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();
    }

    /** Scrapeia um perfil social com try-catch, retornando null em caso de erro. */
    private SocialProfileData scrapeProfileSafely(String url) {
        try {
            String platform = identifyPlatform(url);
            Document doc = fetchSocialPage(url);
            String title = extractPageTitle(doc);
            String description = extractMetaDescription(doc);
            var profile = new SocialProfileData(url, platform, title, description);
            log.debug("Perfil scrapy: {} — {}", platform, title);
            return profile;
        } catch (Exception e) {
            log.debug("Falha ao scrapear {}: {}", url, e.getMessage());
            return null;
        }
    }

    /** Identifica o nome da plataforma a partir da URL. */
    private String identifyPlatform(String url) {
        String lower = url.toLowerCase();
        return properties.getPlatformNames().entrySet().stream()
                .filter(e -> lower.contains(e.getKey()))
                .map(e -> e.getValue())
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
                .map(s -> s.strip())
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