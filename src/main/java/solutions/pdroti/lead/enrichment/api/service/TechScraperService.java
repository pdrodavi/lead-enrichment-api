package solutions.pdroti.lead.enrichment.api.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import solutions.pdroti.lead.enrichment.api.config.TechScraperProperties;
import solutions.pdroti.lead.enrichment.api.dto.ScrapedPageData;
import solutions.pdroti.lead.enrichment.api.enums.ScrapeError;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Serviço de detecção de tecnologias e scraping de páginas web.
 * <p>
 * Utiliza Jsoup para parsear HTML e detectar tecnologias a partir de
 * assinaturas configuradas no {@link TechScraperProperties}.
 * <p>
 * Assinaturas:
 * <ul>
 *   <li>{@code signatures} — substrings no HTML (WordPress, React, Bootstrap, etc.)</li>
 *   <li>{@code script-detectors} — atributos src de scripts (Facebook Pixel, Hotjar, etc.)</li>
 *   <li>{@code meta-generators} — meta tags generator (WordPress, Joomla, Drupal, etc.)</li>
 * </ul>
 * <p>
 * Otimizações:
 * <ul>
 *   <li>Scraping de tecnologias + verificação de nome em UMA requisição HTTP</li>
 *   <li>Cache Caffeine (1h) via {@code DomainEnricherService}</li>
 * </ul>
 */
@Slf4j
@Service
public class TechScraperService {

    private final TechScraperProperties properties;
    private final RestTemplate restTemplate;

    public TechScraperService(TechScraperProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
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
            detectTechnologies(doc, technologies);
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
            Set<String> technologies = new LinkedHashSet<>();
            detectTechnologies(doc, technologies);

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

    /**
     * Detecta tecnologias no documento HTML usando assinaturas conhecidas.
     * Centraliza a lógica de detecção usada por {@link #scrapeTechnologies}
     * e {@link #scrapePage}, evitando duplicação.
     */
    private void detectTechnologies(Document doc, Set<String> technologies) {
        String html = doc.html().toLowerCase();
        detectByHtmlSignatures(html, technologies);
        detectByScriptSrc(doc, technologies);
        detectByMetaTags(doc, technologies);
        detectByMetaProperties(doc, technologies);
    }

    private static final String HTTPS_PREFIX = "https://";
    private static final String ATTR_CONTENT = "content";

    /** Garante scheme https se ausente. */
    private static String normalizeUrl(String domain) {
        return domain.startsWith("http") ? domain : HTTPS_PREFIX + domain;
    }

    /** Faz o fetch da página com timeout e User-Agent configurados. */
    private Document fetchDocument(String url) throws java.io.IOException {
        // Usa RestTemplate com connection pooling para baixar o HTML,
        // depois parseia com Jsoup — evita criar conexão nova por requisição
        String html = restTemplate.getForObject(url, String.class);
        if (html == null || html.isBlank()) {
            throw new java.io.IOException("Resposta vazia de " + url);
        }
        return Jsoup.parse(html);
    }

    /** Detecta tecnologias por assinaturas no HTML (strings características). */
    private void detectByHtmlSignatures(String html, Set<String> technologies) {
        properties.getSignatures().forEach((tech, sigs) -> {
            if (sigs.stream().anyMatch(html::contains)) {
                technologies.add(tech);
            }
        });
    }

    /** Detecta tecnologias por atributo src em scripts (ex: Facebook Pixel, Hotjar). */
    private void detectByScriptSrc(Document doc, Set<String> technologies) {
        doc.select("script[src]").forEach(script -> {
            String src = script.attr("src").toLowerCase();
            properties.getScriptDetectors().forEach((tech, keywords) -> {
                if (keywords.stream().anyMatch(src::contains)) {
                    technologies.add(tech);
                }
            });
        });
    }

    /** Detecta tecnologias via meta tags (generator, CSRF token, etc.). */
    private void detectByMetaTags(Document doc, Set<String> technologies) {
        doc.select("meta[name]").forEach(meta -> {
            String name = meta.attr("name").toLowerCase();
            String content = meta.attr(ATTR_CONTENT).toLowerCase();

            if ("generator".equals(name)) {
                properties.getMetaGenerators().forEach((key, tech) -> {
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
                .map(m -> m.attr(ATTR_CONTENT).strip())
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

    /** Extrai a URL do favicon (qualquer link com rel que contenha "icon"). */
    private static String extractFavicon(Document doc) {
        return doc.select("link[rel~=(?i)icon]").stream()
                .map(l -> l.attr("href"))
                .filter(h -> !h.isBlank())
                .findFirst()
                .orElse(null);
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
                        .map(m -> m.attr(ATTR_CONTENT))
                        .filter(c -> c.contains("charset="))
                        .map(c -> c.substring(c.indexOf("charset=") + 8).strip())
                        .findFirst().orElse(null));
    }

    /** Extrai tags Open Graph (og:title, og:description, og:image, etc.). */
    private static Map<String, String> extractOpenGraph(Document doc) {
        Map<String, String> og = new LinkedHashMap<>();
        doc.select("meta[property^=og:]").forEach(meta -> {
            String property = meta.attr("property").toLowerCase();
            String val = meta.attr(ATTR_CONTENT).strip();
            if (!val.isBlank()) og.put(property, val);
        });
        return og;
    }

    /** Extrai tags Twitter Card (twitter:card, twitter:site, etc.). */
    private static Map<String, String> extractTwitterCards(Document doc) {
        Map<String, String> tc = new LinkedHashMap<>();
        doc.select("meta[name^=twitter:]").forEach(meta -> {
            String name = meta.attr("name").toLowerCase();
            String val = meta.attr(ATTR_CONTENT).strip();
            if (!val.isBlank()) tc.put(name, val);
        });
        return tc;
    }

    /** Extrai os headings h1 da página. */
    @SuppressWarnings("null")
    private static List<String> extractH1Headings(Document doc) {
        return doc.select("h1").stream()
                .map(e -> e.text())
                .map(s -> s.strip())
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

    /**
     * Scrapeia tecnologias E verifica nome na página em UMA ÚNICA requisição HTTP.
     * Evita o problema de duas chamadas HTTP separadas para o mesmo domínio.
     */
    public ScrapeResult scrapeTechnologiesAndCheckName(String domain, String name) {
        if (domain == null || domain.isBlank()) {
            return new ScrapeResult(List.of(), List.of());
        }

        Set<String> technologies = new LinkedHashSet<>();
        List<String> nameMentions = new ArrayList<>();

        try {
            Document doc = fetchDocument(normalizeUrl(domain));
            detectTechnologies(doc, technologies);

            if (name != null && !name.isBlank()) {
                String pageText = doc.text();
                String pageUrl = HTTPS_PREFIX + domain;

                if (nameMatchesExactly(pageText, name)) {
                    nameMentions.add("Nome completo encontrado em: " + pageUrl);
                }
                String title = doc.title();
                if (nameMatchesExactly(title, name)) {
                    nameMentions.add("Nome completo encontrado no título da página: " + pageUrl);
                }
            }
        } catch (Exception e) {
            log.warn("Erro ao scrapear {}: {}", domain, e.getMessage());
            handleScrapeError(e, technologies);
        }

        return new ScrapeResult(List.copyOf(technologies), nameMentions);
    }

    /**
     * Resultado combinado de scraping + verificação de nome.
     * Retornado por {@link #scrapeTechnologiesAndCheckName} para evitar
     * múltiplas requisições HTTP ao mesmo domínio.
     */
    public record ScrapeResult(List<String> technologies, List<String> nameMentions) {}

    /**
     * Verifica se o nome de uma pessoa é mencionado no HTML da página do domínio.
     * <p>
     * Busca APENAS o nome completo no texto visível da página, exigindo que
     * apareça como termo distinto (com boundaries). Nunca faz match parcial
     * de partes do nome para evitar associar dados de outra pessoa.
     *
     * @param domain domínio para buscar (ex: "pdroti.com")
     * @param name   nome completo da pessoa (ex: "João Silva")
     * @return lista de menções encontradas, ou lista vazia se não encontrado
     */
    public List<String> findNameInPage(String domain, String name) {
        if (domain == null || domain.isBlank() || name == null || name.isBlank()) {
            return List.of();
        }

        try {
            Document doc = fetchDocument(normalizeUrl(domain));
            String pageText = doc.text();
            List<String> mentions = new ArrayList<>();

            // URL completa com protocolo para extração em nameMentionUrls
            String pageUrl = "https://" + domain;

            // Verifica nome completo no texto da página (match exato com boundaries)
            if (nameMatchesExactly(pageText, name)) {
                mentions.add("Nome completo encontrado em: " + pageUrl);
            }

            // Verifica também no título da página
            String title = doc.title();
            if (nameMatchesExactly(title, name)) {
                mentions.add("Nome completo encontrado no título da página: " + pageUrl);
            }

            return mentions;
        } catch (Exception e) {
            log.debug("Falha ao buscar nome na página {}: {}", domain, e.getMessage());
            return List.of();
        }
    }

    /**
     * Verifica se o nome completo aparece no texto como termo distinto,
     * evitando matches parciais dentro de outras palavras.
     *
     * @param text texto onde buscar
     * @param name nome completo a ser encontrado
     * @return true se o nome completo for encontrado com boundaries
     */
    private boolean nameMatchesExactly(String text, String name) {
        if (text == null || name == null) return false;
        String lowerText = text.toLowerCase();
        String lowerName = name.toLowerCase();

        int idx = lowerText.indexOf(lowerName);
        if (idx < 0) return false;

        // Verifica se não há caractere alfanumérico antes do nome
        if (idx > 0) {
            char before = lowerText.charAt(idx - 1);
            if (Character.isLetterOrDigit(before)) return false;
        }

        // Verifica se não há caractere alfanumérico depois do nome
        int endIdx = idx + lowerName.length();
        if (endIdx < lowerText.length()) {
            char after = lowerText.charAt(endIdx);
            if (Character.isLetterOrDigit(after)) return false;
        }

        return true;
    }

    /** Classifica a exceção em um ScrapeError legível e adiciona à lista. */
    private static void handleScrapeError(Exception e, Set<String> technologies) {
        technologies.add(ScrapeError.classify(e, e.getMessage()));
    }

}
