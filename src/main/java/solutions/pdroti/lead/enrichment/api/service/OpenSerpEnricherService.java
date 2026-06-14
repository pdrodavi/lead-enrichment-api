package solutions.pdroti.lead.enrichment.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import solutions.pdroti.lead.enrichment.api.dto.SerpResultItem;
import solutions.pdroti.lead.enrichment.api.dto.SerpSearchResult;
import solutions.pdroti.lead.enrichment.api.model.Lead;
import solutions.pdroti.lead.enrichment.api.util.DataParser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

/**
 * Responsável pelo enriquecimento de leads via OpenSERP (Google Search).
 * <p>
 * Busca o nome da pessoa no Google em 6 frentes paralelas:
 * busca geral, documentos, redes sociais, perfil profissional,
 * informações de contato e notícias.
 * <p>
 * <b>Merge seguro:</b> os campos compartilhados com
 * {@link DomainEnricherService} ({@code socialLinks}, {@code nameMentions},
 * {@code exposedEmails}, {@code foundDocuments}, {@code discoveredUrls})
 * são mesclados via {@code LinkedHashSet} para evitar race conditions
 * na execução paralela.
 * <p>
 * Extraído do {@code LeadService} para manter a responsabilidade única (SRP).
 */
@Slf4j
@Service
public class OpenSerpEnricherService {

    private static final int OPENSERP_MAX_RESULTS = 15;

    private final OpenSerpSearchService openSerpSearch;
    private final SocialDiscoveryService socialDiscoveryService;
    private final ObjectMapper objectMapper;
    private final Executor enrichmentExecutor;

    public OpenSerpEnricherService(OpenSerpSearchService openSerpSearch,
                             SocialDiscoveryService socialDiscoveryService,
                             ObjectMapper objectMapper,
                             @Qualifier("enrichmentExecutor") Executor enrichmentExecutor) {
        this.openSerpSearch = openSerpSearch;
        this.socialDiscoveryService = socialDiscoveryService;
        this.objectMapper = objectMapper;
        this.enrichmentExecutor = enrichmentExecutor;
    }

    /** Agrupa as coleções de saída do processamento do OpenSERP. */
    private record SerpProcessingContext(
            List<SerpResultItem> matchedItems,
            Set<String> allLinks,
            Set<String> socialLinksFound,
            List<String> nameMentions,
            List<String> emails,
            List<String> phones,
            List<String> foundDocs
    ) {
        static SerpProcessingContext empty() {
            return new SerpProcessingContext(
                    new ArrayList<>(), new LinkedHashSet<>(), new LinkedHashSet<>(),
                    new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
    }

    /**
     * Enriquece o lead com dados do OpenSERP: múltiplas buscas em paralelo.
     *
     * @param lead entidade a ser enriquecida
     * @param name nome da pessoa para busca
     */
    public void enrich(Lead lead, String name) {
        log.info("Buscando '{}' no OpenSERP (6 frentes em paralelo)", name);

        lead.setExposedEmails(null);
        lead.setDorkFindings(0);
        lead.setOpenSerpRawData(null);
        lead.setFoundDocuments(null);

        // 6 buscas em PARALELO (pool dedicado)
        CompletableFuture<JsonArray> generalFuture = supplySearch(() -> fetchResults(name));
        CompletableFuture<JsonArray> docsFuture = supplySearch(() -> fetchDocuments(name));
        CompletableFuture<JsonArray> socialFuture = supplySearch(() -> fetchSocial(name));
        CompletableFuture<JsonArray> professionalFuture = supplySearch(() -> fetchProfessional(name));
        CompletableFuture<JsonArray> contactFuture = supplySearch(() -> fetchContact(name));
        CompletableFuture<JsonArray> newsFuture = supplySearch(() -> fetchNews(name));

        CompletableFuture.allOf(generalFuture, docsFuture, socialFuture,
                professionalFuture, contactFuture, newsFuture).join();

        JsonArray general = generalFuture.join();
        JsonArray docs = docsFuture.join();
        JsonArray social = socialFuture.join();
        JsonArray professional = professionalFuture.join();
        JsonArray contact = contactFuture.join();
        JsonArray news = newsFuture.join();

        boolean hasAny = Stream.of(general, docs, social, professional, contact, news)
                .anyMatch(r -> r != null && !r.isEmpty());

        if (!hasAny) {
            log.warn("OpenSERP não retornou resultados para '{}' — possível bloqueio do Google (CAPTCHA).", name);
            lead.setSocialLinks(new ArrayList<>());
            lead.setExposedEmails(new ArrayList<>());
            lead.setNameMentions(new ArrayList<>());
            lead.setFoundDocuments(new ArrayList<>());
            lead.setOpenSerpRawData(serializeResult(SerpSearchResult.empty(name)));
            return;
        }

        var socialDomains = socialDiscoveryService.getSocialDomains();
        SerpProcessingContext ctx = SerpProcessingContext.empty();

        // Processa cada fonte
        processResults(general, name, ctx, socialDomains, "Geral");
        processResults(docs, name, ctx, socialDomains, "Documentos");
        processResults(social, name, ctx, socialDomains, "Redes Sociais");
        processResults(professional, name, ctx, socialDomains, "Profissional");
        processResults(contact, name, ctx, socialDomains, "Contato");
        processResults(news, name, ctx, socialDomains, "Notícias");

        // Merge com dados que o DomainEnricherService pode ter definido em paralelo
        Set<String> mergedSocial = new LinkedHashSet<>(
                lead.getSocialLinks() != null ? lead.getSocialLinks() : List.of());
        mergedSocial.addAll(ctx.socialLinksFound());
        lead.setSocialLinks(new ArrayList<>(mergedSocial));

        Set<String> mergedUrls = new LinkedHashSet<>(
                lead.getDiscoveredUrls() != null ? lead.getDiscoveredUrls() : List.of());
        mergedUrls.addAll(ctx.allLinks());
        lead.setDiscoveredUrls(new ArrayList<>(mergedUrls));

        Set<String> mergedEmails = new LinkedHashSet<>(
                lead.getExposedEmails() != null ? lead.getExposedEmails() : List.of());
        mergedEmails.addAll(ctx.emails());
        lead.setExposedEmails(new ArrayList<>(mergedEmails));
        lead.setDorkFindings(lead.getExposedEmails().size());

        Set<String> mergedMentions = new LinkedHashSet<>(
                lead.getNameMentions() != null ? lead.getNameMentions() : List.of());
        mergedMentions.addAll(ctx.nameMentions());
        lead.setNameMentions(new ArrayList<>(mergedMentions));

        Set<String> mergedDocs = new LinkedHashSet<>(
                lead.getFoundDocuments() != null ? lead.getFoundDocuments() : List.of());
        mergedDocs.addAll(ctx.foundDocs());
        lead.setFoundDocuments(new ArrayList<>(mergedDocs));
        lead.setOpenSerpRawData(serializeResult(
                new SerpSearchResult(name, ctx.matchedItems().size(), ctx.matchedItems())));

        log.info("OpenSERP: {} links, {} sociais, {} e-mails, {} telefones, {} menções, {} docs, {} estruturados",
                ctx.allLinks().size(), ctx.socialLinksFound().size(), ctx.emails().size(),
                ctx.phones().size(), ctx.nameMentions().size(), ctx.foundDocs().size(), ctx.matchedItems().size());
    }

    /** Executa um supplier com try-catch, retornando array vazio em caso de erro. */
    private CompletableFuture<JsonArray> supplySearch(java.util.function.Supplier<JsonArray> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try { return supplier.get(); }
            catch (Exception e) {
                log.warn("OpenSERP busca falhou: {}", e.getMessage());
                return new JsonArray();
            }
        }, enrichmentExecutor);
    }

    /**
     * Processa um JsonArray de resultados e mescla no contexto.
     */
    private void processResults(JsonArray results, String name,
                                 SerpProcessingContext ctx, List<String> socialDomains, String source) {
        if (results == null || results.isEmpty()) return;

        for (int i = 0; i < results.size(); i++) {
            JsonObject r = results.get(i).getAsJsonObject();
            String link = r.has("url") ? r.get("url").getAsString() : null;
            String snippet = r.has("snippet") ? r.get("snippet").getAsString() : "";
            String title = r.has("title") ? r.get("title").getAsString() : "";

            if (link == null) continue;

            // Filtra apenas resultados que mencionam o nome exato
            // (aplicado em TODAS as fontes — social, profissional e contato
            //  também precisam referenciar o nome para serem relevantes)
            boolean nameInSnippet = DataParser.nameMatchesExactly(snippet, name);
            boolean nameInTitle = DataParser.nameMatchesExactly(title, name);

            if (!nameInSnippet && !nameInTitle) {
                continue;
            }

            SerpResultItem item = SerpResultItem.fromSearchResult(
                    ctx.matchedItems().size() + 1, link, title, snippet);
            ctx.matchedItems().add(item);

            if (item.fileType() != null) {
                ctx.foundDocs().add(link);
            }

            ctx.allLinks().add(link);
            String lowerLink = link.toLowerCase();

            if (socialDomains.stream().anyMatch(lowerLink::contains)) {
                ctx.socialLinksFound().add(link);
            }

            if (nameInSnippet || nameInTitle) {
                ctx.nameMentions().add("Nome completo encontrado em: " + link + " (" + source + ")");
            }

            DataParser.extractEmails(ctx.emails(), snippet, title);
            DataParser.extractPhones(ctx.phones(), snippet);
        }
    }

    private JsonArray fetchResults(String name) {
        JsonArray results = openSerpSearch.searchPerson(name, OPENSERP_MAX_RESULTS);
        log.debug("OpenSERP geral: {} resultados para '{}'", results.size(), name);
        return results;
    }

    private JsonArray fetchDocuments(String name) {
        JsonArray results = openSerpSearch.searchDocuments(name, OPENSERP_MAX_RESULTS);
        log.debug("OpenSERP documentos: {} resultados para '{}'", results.size(), name);
        return results;
    }

    private JsonArray fetchSocial(String name) {
        JsonArray results = openSerpSearch.searchSocialMedia(name, OPENSERP_MAX_RESULTS);
        log.debug("OpenSERP redes sociais: {} resultados para '{}'", results.size(), name);
        return results;
    }

    private JsonArray fetchProfessional(String name) {
        JsonArray results = openSerpSearch.searchProfessional(name, OPENSERP_MAX_RESULTS);
        log.debug("OpenSERP profissional: {} resultados para '{}'", results.size(), name);
        return results;
    }

    private JsonArray fetchContact(String name) {
        JsonArray results = openSerpSearch.searchContact(name, OPENSERP_MAX_RESULTS);
        log.debug("OpenSERP contato: {} resultados para '{}'", results.size(), name);
        return results;
    }

    private JsonArray fetchNews(String name) {
        JsonArray results = openSerpSearch.searchNews(name, OPENSERP_MAX_RESULTS);
        log.debug("OpenSERP notícias: {} resultados para '{}'", results.size(), name);
        return results;
    }

    private String serializeResult(SerpSearchResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("Falha ao serializar resultado: {}", e.getMessage());
            return "{\"query\":\"\",\"totalResults\":0,\"items\":[]}";
        }
    }
}
