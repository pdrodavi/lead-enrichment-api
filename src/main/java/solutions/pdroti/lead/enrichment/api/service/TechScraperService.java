package solutions.pdroti.lead.enrichment.api.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import solutions.pdroti.lead.enrichment.api.dto.DorkScanResult;
import solutions.pdroti.lead.enrichment.api.dto.ScrapedPageData;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
public class TechScraperService {

    private static final int TIMEOUT_MS = 10_000;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";

    private static final Map<String, List<String>> SCRIPT_DETECTORS = Map.ofEntries(
            Map.entry("Facebook Pixel", List.of("facebook", "fbq")),
            Map.entry("Hotjar", List.of("hotjar")),
            Map.entry("LinkedIn Insights", List.of("linkedin")),
            Map.entry("Google Analytics 4", List.of("gtag", "ga4")),
            Map.entry("HubSpot", List.of("hs-analytics", "hubspot")),
            Map.entry("Intercom", List.of("intercom")),
            Map.entry("TikTok Pixel", List.of("tiktok")),
            Map.entry("Microsoft Clarity", List.of("clarity")),
            Map.entry("FullStory", List.of("fullstory")),
            Map.entry("Mixpanel", List.of("mixpanel")),
            Map.entry("Amplitude", List.of("amplitude")),
            Map.entry("Segment", List.of("segment")),
            Map.entry("Stripe", List.of("stripe", "checkout")),
            Map.entry("PayPal", List.of("paypal")),
            Map.entry("Cloudflare Web Analytics", List.of("cloudflare-insights")),
            Map.entry("Sentry", List.of("sentry", "raven")),
            Map.entry("New Relic", List.of("newrelic", "nr-agent")),
            Map.entry("VWO", List.of("vwo", "visual-website-optimizer")),
            Map.entry("Optimizely", List.of("optimizely")),
            Map.entry("Twitter Pixel", List.of("twttr", "twitter-widgets"))
    );

    private static final Map<String, List<String>> SIGNATURES = Map.ofEntries(
            // CMS / Frameworks
            Map.entry("WordPress", List.of("wp-content", "wp-includes", "wordpress")),
            Map.entry("Laravel", List.of("laravel", "csrf-token")),
            Map.entry("Drupal", List.of("drupal", "drupal.js", "sites/default")),
            Map.entry("Joomla", List.of("joomla", "com_content", "com_users")),
            Map.entry("Magento", List.of("mage/", "mage-cache", "Magento")),
            Map.entry("Squarespace", List.of("squarespace.com", "squarespace")),
            Map.entry("Webflow", List.of("webflow", "webflow.js")),
            Map.entry("Adobe Experience Manager", List.of("aem", "cq-", "adobedtm")),

            // JavaScript frameworks
            Map.entry("jQuery", List.of("jquery")),
            Map.entry("React", List.of("react.js", "react.development.js", "react.production.min.js", "_next/static")),
            Map.entry("Vue.js", List.of("vue.js", "vue.min.js")),
            Map.entry("Angular", List.of("angular.js", "angular.min.js", "ng-app")),
            Map.entry("Next.js", List.of("_next/static", "__NEXT_DATA__", "next.js")),
            Map.entry("Nuxt.js", List.of("_nuxt/", "__NUXT__")),
            Map.entry("Gatsby", List.of("gatsby", "gatsby-config")),
            Map.entry("Alpine.js", List.of("alpinejs", "alpine.js")),
            Map.entry("Svelte", List.of("svelte")),

            // CSS / UI frameworks
            Map.entry("Bootstrap", List.of("bootstrap.css", "bootstrap.min.css", "bootstrap.js", "bootstrap.min.js")),
            Map.entry("Tailwind CSS", List.of("tailwindcss", "tailwind")),
            Map.entry("Materialize", List.of("materialize", "materialize.css")),
            Map.entry("Font Awesome", List.of("font-awesome", "fontawesome")),

            // Analytics & Marketing
            Map.entry("Google Tag Manager", List.of("gtm.js", "googletagmanager.com")),
            Map.entry("Google Analytics", List.of("analytics.js", "ga.js", "gtag")),
            Map.entry("Hotjar", List.of("hotjar")),
            Map.entry("HubSpot", List.of("hubspot")),
            Map.entry("Intercom", List.of("intercom")),
            Map.entry("Twitter Pixel", List.of("twttr")),
            Map.entry("TikTok Pixel", List.of("tiktok")),
            Map.entry("Facebook Pixel", List.of("facebook", "fbq")),
            Map.entry("Microsoft Clarity", List.of("clarity")),
            Map.entry("Yandex Metrica", List.of("mc.yandex", "yandex_metrika")),
            Map.entry("Matomo", List.of("matomo", "piwik")),

            // Infra & CDN
            Map.entry("Cloudflare", List.of("cloudflare", "__cfduid")),
            Map.entry("Cloudflare Protection", List.of("__cf_chl_tk", "cf_chl_prog", "challenge-platform")),
            Map.entry("CloudFront", List.of("cloudfront.net")),
            Map.entry("Fastly", List.of("fastly")),
            Map.entry("Akamai", List.of("akamai")),

            // E-commerce
            Map.entry("Shopify", List.of("shopify.com", "myshopify.com", "/cdn/shop/")),
            Map.entry("Wix", List.of("wix.com", "wixstatic.com")),
            Map.entry("OpenCart", List.of("opencart", "route=common")),
            Map.entry("PrestaShop", List.of("prestashop")),

            // Payment
            Map.entry("Stripe", List.of("stripe.com", "checkout.stripe")),
            Map.entry("PayPal", List.of("paypal.com", "paypalobjects")),
            Map.entry("Mercado Pago", List.of("mercadopago")),

            // Fonts
            Map.entry("Google Fonts", List.of("fonts.googleapis")),
            Map.entry("Adobe Fonts", List.of("typekit.net", "use.typekit")),

            // Error tracking
            Map.entry("Sentry", List.of("sentry")),
            Map.entry("New Relic", List.of("newrelic")),

            // Consent / Privacy
            Map.entry("CookieYes", List.of("cookieyes")),
            Map.entry("Cookiebot", List.of("cookiebot")),
            Map.entry("OneTrust", List.of("onetrust")),

            // Embedded services
            Map.entry("YouTube", List.of("youtube.com/embed", "youtube-nocookie")),
            Map.entry("Vimeo", List.of("vimeo.com")),
            Map.entry("Google Maps", List.of("maps.googleapis", "maps.google")),
            Map.entry("reCAPTCHA", List.of("recaptcha", "g-recaptcha")),
            Map.entry("Disqus", List.of("disqus")),
            Map.entry("Zendesk", List.of("zendesk")),
            Map.entry("LiveChat", List.of("livechat"))
    );

    private static final Map<String, String> META_GENERATORS = Map.ofEntries(
            Map.entry("wordpress", "WordPress"),
            Map.entry("laravel", "Laravel"),
            Map.entry("drupal", "Drupal"),
            Map.entry("joomla", "Joomla"),
            Map.entry("magento", "Magento"),
            Map.entry("blogger", "Blogger"),
            Map.entry("expressionengine", "ExpressionEngine"),
            Map.entry("ghost", "Ghost"),
            Map.entry("hugo", "Hugo"),
            Map.entry("jekyll", "Jekyll"),
            Map.entry("gatsby", "Gatsby"),
            Map.entry("next.js", "Next.js"),
            Map.entry("nuxt", "Nuxt"),
            Map.entry("squarespace", "Squarespace"),
            Map.entry("wix", "Wix"),
            Map.entry("webflow", "Webflow"),
            Map.entry("sitecore", "Sitecore"),
            Map.entry("umbraco", "Umbraco"),
            Map.entry("concrete5", "Concrete CMS"),
            Map.entry("prestashop", "PrestaShop"),
            Map.entry("shopify", "Shopify"),
            Map.entry("typo3", "TYPO3"),
            Map.entry("spip", "SPIP"),
            Map.entry("dotnetnuke", "DNN (DotNetNuke)"),
            Map.entry("zend", "Zend CMS"),
            Map.entry("silverstripe", "SilverStripe"),
            Map.entry("octobercms", "October CMS"),
            Map.entry("statamic", "Statamic"),
            Map.entry("craft", "Craft CMS"),
            Map.entry("grav", "Grav")
    );

    // ========== Google Dorks — padrões de busca de info exposta ==========

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?:\\+\\d{1,3}[\\s.-]?)?(?:\\(\\d{2,3}\\)[\\s.-]?)?\\d{4,5}[\\s.-]?\\d{4}");

    private static final List<String> ADMIN_PATTERNS = List.of(
            "/admin", "/wp-admin", "/administrator", "/backend", "/cpanel",
            "/painel", "/login", "/signin", "/dashboard", "/manager",
            "/joomla/administrator", "/drupal/admin", "/moderator"
    );

    private static final List<String> DOCUMENT_EXTENSIONS = List.of(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".csv", ".txt", ".json", ".xml"
    );

    private static final List<String> CONFIG_EXTENSIONS = List.of(
            ".env", ".env.bak", ".env.backup", ".config", ".conf",
            ".sql", ".dump", ".bak", ".old", ".swp", ".yml.bak"
    );

    private static final List<String> BACKUP_EXTENSIONS = List.of(
            ".zip", ".tar", ".tar.gz", ".tgz", ".rar", ".7z", ".gz",
            "-backup", "-bkp", ".sql.gz", ".dump.sql"
    );

    private static final List<String> LOG_PATTERNS = List.of(
            "error_log", "debug.log", "access.log", "error.log", "wp-debug.log",
            "laravel.log", "syslog", "messages.log"
    );

    private static final List<String> ERROR_KEYWORDS = List.of(
            "warning:", "fatal error:", "stack trace:", "exception:",
            "syntax error", "parse error", "uncaught", "notice:",
            "mysql_error", "sql error", "cannot modify header"
    );

    private static final List<String> DB_KEYWORDS = List.of(
            "mysql", "mariadb", "postgresql", "database_host",
            "db_host", "db_name", "db_user", "db_password",
            "pdo_mysql", "mysqli_connect", "pg_connect"
    );

    enum ScrapeError {
        TIMEOUT("Timeout", (e, msg) ->
                e instanceof java.net.SocketTimeoutException || msg.contains("timeout") || msg.contains("timed out")),
        CLOUDFLARE("Cloudflare Protection", (e, msg) ->
                msg.contains("cloudflare") || msg.contains("1020") || msg.contains("challenge")),
        ACCESS_DENIED("Access Denied (403)", (e, msg) ->
                msg.contains("403") || msg.contains("forbidden")),
        SSL_HANDSHAKE("SSL Handshake Failed", (e, msg) ->
                e instanceof javax.net.ssl.SSLException || msg.contains("ssl") || msg.contains("handshake")),
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

        String format() {
            return "ScrapeError: " + description;
        }

        static String classify(Exception e, String message) {
            String msg = message != null ? message.toLowerCase() : "";
            for (var error : values()) {
                if (error.matcher.matches(e, msg)) {
                    return error.format();
                }
            }
            return "ScrapeError: " + e.getClass().getSimpleName();
        }

        @FunctionalInterface
        interface ErrorMatcher {
            boolean matches(Exception e, String message);
        }
    }

    /**
     * Analisa o HTML do domínio e retorna lista de tecnologias detectadas.
     * Inclui fallback com descrição do erro em caso de falha de scrape.
     */
    public List<String> scrapeTechnologies(String domain) {
        if (domain == null || domain.isBlank()) {
            return List.of();
        }

        Set<String> technologies = new LinkedHashSet<>();

        try {
            Document doc = fetchDocument(normalizeUrl(domain));
            String html = doc.html().toLowerCase();

            detectByHtmlSignatures(html, technologies);
            detectByScriptSrc(doc, technologies);
            detectByMetaTags(doc, technologies);
            detectByMetaProperties(doc, technologies);

        } catch (Exception e) {
            log.warn("Erro ao scrapear {}: {}", domain, e.getMessage());
            handleScrapeError(e, technologies);
        }

        return List.copyOf(technologies);
    }

    /**
     * Scrape completo da página: tecnologias + metadados públicos.
     * Retorna um {@link ScrapedPageData} com todos os dados extraídos.
     */
    public ScrapedPageData scrapePage(String domain) {
        if (domain == null || domain.isBlank()) {
            return ScrapedPageData.empty();
        }

        try {
            Document doc = fetchDocument(normalizeUrl(domain));
            String html = doc.html().toLowerCase();

            Set<String> technologies = new LinkedHashSet<>();
            detectByHtmlSignatures(html, technologies);
            detectByScriptSrc(doc, technologies);
            detectByMetaTags(doc, technologies);
            detectByMetaProperties(doc, technologies);

            return new ScrapedPageData(
                    extractPageTitle(doc),
                    extractMetaDescription(doc),
                    extractLanguage(doc),
                    extractFavicon(doc),
                    extractCanonicalUrl(doc),
                    extractThemeColor(doc),
                    extractCharset(doc),
                    List.copyOf(technologies),
                    extractOpenGraph(doc),
                    extractTwitterCards(doc),
                    extractH1Headings(doc),
                    extractSocialLinksFromPage(doc)
            );
        } catch (Exception e) {
            log.warn("Erro ao scrapear página {}: {}", domain, e.getMessage());
            var error = ScrapeError.classify(e, e.getMessage());
            return new ScrapedPageData(
                    null, null, null, null, null, null, null,
                    List.of(error), Map.of(), Map.of(), List.of(), List.of()
            );
        }
    }

    /** Garante scheme https se ausente. */
    private static String normalizeUrl(String domain) {
        return domain.startsWith("http") ? domain : "https://" + domain;
    }

    /** Faz o fetch da página com timeout e User-Agent configurados. */
    private static Document fetchDocument(String url) throws java.io.IOException {
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .followRedirects(true)
                .get();
    }

    /** Detecta tecnologias por assinaturas no HTML (strings características). */
    private static void detectByHtmlSignatures(String html, Set<String> technologies) {
        SIGNATURES.forEach((tech, sigs) -> {
            if (sigs.stream().anyMatch(html::contains)) {
                technologies.add(tech);
            }
        });
    }

    /** Detecta tecnologias por atributo src em scripts (ex: Facebook Pixel, Hotjar). */
    private static void detectByScriptSrc(Document doc, Set<String> technologies) {
        doc.select("script[src]").forEach(script -> {
            String src = script.attr("src").toLowerCase();
            SCRIPT_DETECTORS.forEach((tech, keywords) -> {
                if (keywords.stream().anyMatch(src::contains)) {
                    technologies.add(tech);
                }
            });
        });
    }

    /** Detecta tecnologias via meta tags (generator, CSRF token, etc.). */
    private static void detectByMetaTags(Document doc, Set<String> technologies) {
        doc.select("meta[name]").forEach(meta -> {
            String name = meta.attr("name").toLowerCase();
            String content = meta.attr("content").toLowerCase();

            if ("generator".equals(name)) {
                META_GENERATORS.forEach((key, tech) -> {
                    if (content.contains(key)) technologies.add(tech);
                });
            }
            if ("csrf-param".equals(name) || "csrf-token".equals(name)) {
                technologies.add("CSRF Protection");
            }
            if (content.contains("bucket") && name.contains("gtm")) {
                technologies.add("Google Tag Manager");
            }
        });
    }

    /** Detecta tecnologias via meta property (Open Graph, Twitter Cards). */
    private static void detectByMetaProperties(Document doc, Set<String> technologies) {
        doc.select("meta[property]").forEach(meta -> {
            String property = meta.attr("property").toLowerCase();
            if (property.startsWith("og:")) technologies.add("Open Graph");
            if (property.startsWith("twitter:")) technologies.add("Twitter Cards");
            if (property.contains("fb:app_id")) technologies.add("Facebook App");
        });
    }

    // ========== Métodos de extração de dados públicos ==========

    /** Extrai o título da página (<title>). */
    private static String extractPageTitle(Document doc) {
        String title = doc.title();
        return title.isBlank() ? null : title.strip();
    }

    /** Extrai a meta description. */
    private static String extractMetaDescription(Document doc) {
        return doc.select("meta[name=description]").stream()
                .map(m -> m.attr("content").strip())
                .filter(c -> !c.isBlank())
                .findFirst().orElse(null);
    }

    /** Extrai o idioma do atributo lang no <html>. */
    private static String extractLanguage(Document doc) {
        return doc.select("html").stream()
                .map(h -> h.attr("lang").strip())
                .filter(l -> !l.isBlank())
                .findFirst().orElse(null);
    }

    /** Extrai a URL do favicon. */
    private static String extractFavicon(Document doc) {
        return doc.select("link[rel~=(?i)^(icon|shortcut icon|apple-touch-icon)$]").stream()
                .map(l -> l.attr("href"))
                .filter(h -> !h.isBlank())
                .findFirst()
                .orElseGet(() -> doc.select("link[rel~=(?i)icon]").stream()
                        .map(l -> l.attr("href"))
                        .filter(h -> !h.isBlank())
                        .findFirst().orElse(null));
    }

    /** Extrai a URL canônica. */
    private static String extractCanonicalUrl(Document doc) {
        return doc.select("link[rel=canonical]").stream()
                .map(l -> l.attr("href").strip())
                .filter(h -> !h.isBlank())
                .findFirst().orElse(null);
    }

    /** Extrai a cor do tema (theme-color). */
    private static String extractThemeColor(Document doc) {
        return doc.select("meta[name=theme-color]").stream()
                .map(m -> m.attr("content").strip())
                .filter(c -> !c.isBlank())
                .findFirst().orElse(null);
    }

    /** Extrai o charset declarado. */
    private static String extractCharset(Document doc) {
        return doc.select("meta[charset]").stream()
                .map(m -> m.attr("charset").strip())
                .filter(c -> !c.isBlank())
                .findFirst()
                .orElseGet(() -> doc.select("meta[http-equiv=Content-Type]").stream()
                        .map(m -> m.attr("content"))
                        .filter(c -> c.contains("charset="))
                        .map(c -> c.substring(c.indexOf("charset=") + 8).strip())
                        .findFirst().orElse(null));
    }

    /** Extrai tags Open Graph (og:title, og:description, og:image, etc.). */
    private static Map<String, String> extractOpenGraph(Document doc) {
        Map<String, String> og = new LinkedHashMap<>();
        doc.select("meta[property^=og:]").forEach(meta -> {
            String property = meta.attr("property").toLowerCase();
            String content = meta.attr("content").strip();
            if (!content.isBlank()) og.put(property, content);
        });
        return og;
    }

    /** Extrai tags Twitter Card (twitter:card, twitter:site, etc.). */
    private static Map<String, String> extractTwitterCards(Document doc) {
        Map<String, String> tc = new LinkedHashMap<>();
        doc.select("meta[name^=twitter:]").forEach(meta -> {
            String name = meta.attr("name").toLowerCase();
            String content = meta.attr("content").strip();
            if (!content.isBlank()) tc.put(name, content);
        });
        return tc;
    }

    /** Extrai os headings h1 da página. */
    private static List<String> extractH1Headings(Document doc) {
        return doc.select("h1").stream()
                .map(Element::text)
                .map(String::strip)
                .filter(t -> !t.isBlank())
                .toList();
    }

    /** Extrai links sociais do HTML (anchor com href). */
    private static List<String> extractSocialLinksFromPage(Document doc) {
        return doc.select("a[href]").stream()
                .map(a -> a.attr("href").strip())
                .filter(h -> !h.isBlank())
                .distinct()
                .toList();
    }

    // ========== Google Dorks — varredura de informações expostas ==========

    /**
     * Executa varredura Google Dorks no HTML e retorna informações expostas.
     */
    public DorkScanResult scanDorks(String domain) {
        if (domain == null || domain.isBlank()) {
            return DorkScanResult.empty();
        }

        try {
            Document doc = fetchDocument(normalizeUrl(domain));
            String html = doc.html();
            String lowerHtml = html.toLowerCase();

            List<String> emails = scanEmails(html);
            List<String> phones = scanPhones(html);
            List<String> adminPaths = scanAdminPaths(lowerHtml);
            List<String> documents = scanDocumentLinks(lowerHtml);
            List<String> configFiles = scanConfigFiles(lowerHtml);
            List<String> backups = scanBackupFiles(lowerHtml);
            List<String> errors = scanErrorMessages(lowerHtml);
            List<String> logs = scanLogFiles(lowerHtml);
            List<String> dbInfo = scanDatabaseInfo(lowerHtml);

            int total = emails.size() + phones.size() + adminPaths.size()
                    + documents.size() + configFiles.size() + backups.size()
                    + errors.size() + logs.size() + dbInfo.size();

            log.info("Dorks scan para {}: {} achados ({} emails, {} phones, {} admin, {} docs)",
                    domain, total, emails.size(), phones.size(), adminPaths.size(), documents.size());

            return new DorkScanResult(
                    emails, phones, adminPaths, documents, configFiles, backups,
                    errors, logs, dbInfo, Map.of(), List.of(), total
            );

        } catch (Exception e) {
            log.warn("Dorks scan falhou para {}: {}", domain, e.getMessage());
            return DorkScanResult.empty();
        }
    }

    /** Escaneia e-mails expostos no HTML. */
    private static List<String> scanEmails(String html) {
        return EMAIL_PATTERN.matcher(html).results()
                .map(r -> r.group().toLowerCase().strip())
                .filter(e -> !e.contains("example.com") && !e.contains("@domain") && !e.contains("@site"))
                .distinct()
                .toList();
    }

    /** Escaneia telefones expostos no HTML. */
    private static List<String> scanPhones(String html) {
        return PHONE_PATTERN.matcher(html).results()
                .map(r -> r.group().strip())
                .filter(p -> p.length() >= 8)
                .distinct()
                .toList();
    }

    /** Escaneia caminhos administrativos expostos. */
    private static List<String> scanAdminPaths(String html) {
        return ADMIN_PATTERNS.stream()
                .filter(html::contains)
                .toList();
    }

    /** Escaneia links para documentos (.pdf, .docx, etc.). */
    private static List<String> scanDocumentLinks(String html) {
        return DOCUMENT_EXTENSIONS.stream()
                .flatMap(ext -> {
                    var pattern = Pattern.compile("\"[^\"]*" + Pattern.quote(ext) + "\"", Pattern.CASE_INSENSITIVE);
                    return pattern.matcher(html).results().map(r -> r.group().replace("\"", ""));
                })
                .distinct()
                .toList();
    }

    /** Escaneia arquivos de configuração expostos (.env, .sql, etc.). */
    private static List<String> scanConfigFiles(String html) {
        return CONFIG_EXTENSIONS.stream()
                .filter(html::contains)
                .toList();
    }

    /** Escaneia arquivos de backup expostos (.zip, .tar.gz, -backup, etc.). */
    private static List<String> scanBackupFiles(String html) {
        return BACKUP_EXTENSIONS.stream()
                .filter(html::contains)
                .toList();
    }

    /** Escaneia mensagens de erro expostas (stack traces, warnings). */
    private static List<String> scanErrorMessages(String html) {
        return ERROR_KEYWORDS.stream()
                .filter(html::contains)
                .toList();
    }

    /** Escaneia arquivos de log expostos. */
    private static List<String> scanLogFiles(String html) {
        return LOG_PATTERNS.stream()
                .filter(html::contains)
                .toList();
    }

    /** Escaneia informações de banco de dados expostas. */
    private static List<String> scanDatabaseInfo(String html) {
        return DB_KEYWORDS.stream()
                .filter(html::contains)
                .toList();
    }

    /** Classifica a exceção em um ScrapeError legível e adiciona à lista. */
    private static void handleScrapeError(Exception e, Set<String> technologies) {
        technologies.add(ScrapeError.classify(e, e.getMessage()));
    }

}
