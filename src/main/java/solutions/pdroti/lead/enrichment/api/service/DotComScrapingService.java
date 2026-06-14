package solutions.pdroti.lead.enrichment.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import solutions.pdroti.lead.enrichment.api.model.Lead;
import solutions.pdroti.lead.enrichment.api.util.DataParser;

import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * Serviço especializado em scraping de sites .com/.com.br/.br
 * para extração de redes sociais, telefones e e-mails.
 * <p>
 * Utilizado pelo {@link LeadService} quando nenhum domínio é informado:
 * percorre os URLs descobertos pelo OpenSERP que terminam com .com, .com.br ou .br
 * e mescla os dados encontrados com os resultados já obtidos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DotComScrapingService {

    private final SocialDiscoveryService socialDiscoveryService;
    private final RestTemplate restTemplate;

    /** Limite máximo de sites a scrapear para não sobrecarregar. */
    private static final int MAX_SITES = 10;

    /**
     * Escaneia os URLs descobertos em busca de sites .com/.com.br/.br
     * e extrai redes sociais, telefones e e-mails de cada um.
     * <p>
     * Os dados são mesclados com os resultados já existentes no lead.
     *
     * @param lead lead sendo enriquecido (alterado inline)
     * @param name nome da pessoa (para filtragem)
     */
    public void scrapeDotComSites(Lead lead, String name) {
        List<String> urls = lead.getDiscoveredUrls();
        if (urls == null || urls.isEmpty()) {
            log.info("Nenhum URL descoberto pelo OpenSERP para scraping .com/.com.br/.br");
            return;
        }

        List<String> dotComUrls = filterDotComUrls(urls);
        if (dotComUrls.isEmpty()) {
            log.info("Nenhum site .com/.com.br/.br encontrado entre os URLs descobertos");
            return;
        }

        log.info("Scraping de {} site(s) .com/.com.br/.br para redes sociais, telefones e e-mails", dotComUrls.size());

        var result = scrapeSites(dotComUrls, lead, name);

        lead.setSocialLinks(new ArrayList<>(result.socialLinks()));
        lead.setExposedEmails(new ArrayList<>(result.emails()));
        lead.setExposedPhones(new ArrayList<>(result.phones()));
        lead.setDorkFindings(lead.getExposedEmails().size());

        log.info("Scraping .com/.com.br concluído: {} sociais, {} e-mails, {} telefones",
                result.socialLinks().size(), result.emails().size(), result.phones().size());
    }

    /** Filtra apenas URLs com domínios .com, .com.br ou .br, limitando a {@link #MAX_SITES}. */
    private List<String> filterDotComUrls(List<String> urls) {
        return urls.stream()
                .filter(url -> {
                    try {
                        String host = new URL(url.toLowerCase()).getHost();
                        return host != null && (host.endsWith(".com") || host.endsWith(".br"));
                    } catch (Exception e) {
                        return false;
                    }
                })
                .distinct()
                .limit(MAX_SITES)
                .toList();
    }

    /** Resultado consolidado do scraping de múltiplos sites. */
    private record ScrapeResult(
            Set<String> socialLinks,
            Set<String> emails,
            Set<String> phones
    ) {}

    /** Percorre cada site e coleta dados, mesclando com os já existentes no lead. */
    private ScrapeResult scrapeSites(List<String> dotComUrls, Lead lead, String name) {
        Set<String> mergedSocialLinks = new LinkedHashSet<>(
                lead.getSocialLinks() != null ? lead.getSocialLinks() : List.of());
        Set<String> mergedEmails = new LinkedHashSet<>(
                lead.getExposedEmails() != null ? lead.getExposedEmails() : List.of());
        Set<String> mergedPhones = new LinkedHashSet<>(
                lead.getExposedPhones() != null ? lead.getExposedPhones() : List.of());

        for (String url : dotComUrls) {
            try {
                scrapeSingleSite(url, mergedSocialLinks, mergedEmails, mergedPhones);
            } catch (Exception e) {
                log.debug("Falha ao scrapear {}: {}", url, e.getMessage());
            }
        }

        return new ScrapeResult(mergedSocialLinks, mergedEmails, mergedPhones);
    }

    /** Extrai redes sociais, e-mails e telefones de um único site. */
    private void scrapeSingleSite(String url,
                                   Set<String> socialLinks,
                                   Set<String> emails,
                                   Set<String> phones) throws Exception {
        String domainForSocial = new URL(url).getHost();

        // 1. Redes sociais — usa o serviço especializado
        List<String> foundSocialLinks = socialDiscoveryService.discoverSocialLinks(domainForSocial);
        if (foundSocialLinks != null) {
            socialLinks.addAll(foundSocialLinks);
        }

        // 2. Telefones e e-mails — faz fetch do HTML e extrai via regex
        String html = restTemplate.getForObject(url, String.class);
        if (html == null || html.isBlank()) return;

        Document doc = Jsoup.parse(html);
        String pageText = doc.text();

        extractEmails(pageText, emails);
        extractPhones(pageText, phones);

        log.debug("Scraping concluído para {}", url);
    }

    /** Extrai e-mails do texto da página usando {@link DataParser#EMAIL_PATTERN}. */
    private void extractEmails(String pageText, Set<String> emails) {
        Matcher emailMatcher = DataParser.EMAIL_PATTERN.matcher(pageText);
        while (emailMatcher.find()) {
            String foundEmail = emailMatcher.group().toLowerCase();
            if (!foundEmail.contains("example.com")) {
                emails.add(foundEmail);
            }
        }
    }

    /** Extrai telefones do texto usando {@link DataParser#PHONE_PATTERN}, validando tamanho. */
    private void extractPhones(String pageText, Set<String> phones) {
        Matcher phoneMatcher = DataParser.PHONE_PATTERN.matcher(pageText);
        while (phoneMatcher.find()) {
            String phone = phoneMatcher.group().strip();
            String digits = phone.replaceAll("\\D", "");
            if (digits.length() >= 10 && digits.length() <= 15) {
                phones.add(phone);
            }
        }
    }
}
