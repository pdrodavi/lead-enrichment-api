package solutions.pdroti.lead.enrichment.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solutions.pdroti.lead.enrichment.api.dto.SerpResultItem;
import solutions.pdroti.lead.enrichment.api.dto.SerpSearchResult;
import solutions.pdroti.lead.enrichment.api.model.Lead;
import solutions.pdroti.lead.enrichment.api.util.DataParser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Responsável pelo enriquecimento de leads via OpenSERP (Google Search).
 * <p>
 * Busca o nome da pessoa no Google, extrai links, redes sociais,
 * e-mails expostos, menções ao nome e documentos relacionados.
 * Extraído do {@code LeadService} para manter a responsabilidade única (SRP).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenSerpEnricher {

    private static final int OPENSERP_MAX_RESULTS = 30;

    private final OpenSerpSearch openSerpSearch;
    private final SocialDiscoveryService socialDiscoveryService;
    private final ObjectMapper objectMapper;

    /** Agrupa as coleções de saída do processamento do OpenSERP. */
    private record SerpProcessingContext(
            List<SerpResultItem> matchedItems,
            Set<String> allLinks,
            Set<String> socialLinksFound,
            List<String> nameMentions,
            List<String> emails,
            List<String> foundDocs
    ) {
        static SerpProcessingContext empty() {
            return new SerpProcessingContext(
                    new ArrayList<>(), new LinkedHashSet<>(), new LinkedHashSet<>(),
                    new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
    }

    /**
     * Enriquece o lead com dados do OpenSERP: busca o nome no Google,
     * extrai links, redes sociais, e-mails expostos e menções ao nome.
     *
     * @param lead entidade a ser enriquecida
     * @param name nome da pessoa para busca
     */
    public void enrich(Lead lead, String name) {
        log.info("Buscando '{}' no OpenSERP", name);

        lead.setExposedEmails(null);
        lead.setDorkFindings(0);
        lead.setOpenSerpRawData(null);
        lead.setFoundDocuments(null);

        JsonArray results = fetchResults(name);
        JsonArray docResults = fetchDocuments(name);

        boolean hasResults = (results != null && !results.isEmpty());
        boolean hasDocs = (docResults != null && !docResults.isEmpty());

        if (!hasResults && !hasDocs) {
            log.warn("OpenSERP não retornou resultados para '{}'", name);
            lead.setSocialLinks(new ArrayList<>());
            lead.setExposedEmails(new ArrayList<>());
            lead.setNameMentions(new ArrayList<>());
            lead.setFoundDocuments(new ArrayList<>());
            lead.setOpenSerpRawData(serializeResult(SerpSearchResult.empty(name)));
            return;
        }

        var socialDomains = socialDiscoveryService.getSocialDomains();
        SerpProcessingContext ctx = SerpProcessingContext.empty();

        if (hasResults) {
            processResults(results, name, ctx, socialDomains);
        }
        if (hasDocs) {
            processResults(docResults, name, ctx, socialDomains);
        }

        lead.setSocialLinks(new ArrayList<>(ctx.socialLinksFound()));
        lead.setDiscoveredUrls(new ArrayList<>(ctx.allLinks()));
        lead.setExposedEmails(ctx.emails());
        lead.setDorkFindings(ctx.emails().size());
        lead.setNameMentions(ctx.nameMentions());
        lead.setFoundDocuments(ctx.foundDocs());
        lead.setOpenSerpRawData(serializeResult(
                new SerpSearchResult(name, ctx.matchedItems().size(), ctx.matchedItems())));

        log.info("OpenSERP: {} links totais, {} sociais, {} e-mails, {} menções, {} docs, {} resultados estruturados",
                ctx.allLinks().size(), ctx.socialLinksFound().size(), ctx.emails().size(),
                ctx.nameMentions().size(), ctx.foundDocs().size(), ctx.matchedItems().size());
    }

    /**
     * Processa um JsonArray de resultados do OpenSERP, aplicando o filtro de nome.
     *
     * @param results      resultados brutos da busca
     * @param name         nome para filtrar
     * @param ctx          contexto de processamento (coleções de saída)
     * @param socialDomains lista de domínios de redes sociais conhecidos
     */
    private void processResults(JsonArray results, String name,
                                 SerpProcessingContext ctx, List<String> socialDomains) {
        for (int i = 0; i < results.size(); i++) {
            JsonObject r = results.get(i).getAsJsonObject();
            String link = r.has("url") ? r.get("url").getAsString() : null;
            String snippet = r.has("snippet") ? r.get("snippet").getAsString() : "";
            String title = r.has("title") ? r.get("title").getAsString() : "";

            if (link == null) continue;

            if (!DataParser.nameMatchesExactly(snippet, name) && !DataParser.nameMatchesExactly(title, name)) {
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

            ctx.nameMentions().add("Nome completo encontrado em: " + link);
            DataParser.extractEmails(ctx.emails(), snippet, title);
        }
    }

    private JsonArray fetchResults(String name) {
        try {
            JsonArray results = openSerpSearch.searchPerson(name, OPENSERP_MAX_RESULTS);
            log.info("OpenSERP: {} resultados brutos para '{}'", results.size(), name);
            return results;
        } catch (Exception e) {
            log.warn("OpenSERP falhou para '{}': {}", name, e.getMessage());
            return new JsonArray();
        }
    }

    private JsonArray fetchDocuments(String name) {
        try {
            JsonArray docResults = openSerpSearch.searchDocuments(name, OPENSERP_MAX_RESULTS);
            log.info("OpenSERP documentos: {} resultados brutos para '{}'", docResults.size(), name);
            return docResults;
        } catch (Exception e) {
            log.warn("OpenSERP documentos falhou para '{}': {}", name, e.getMessage());
            return new JsonArray();
        }
    }

    private String serializeResult(SerpSearchResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("Falha ao serializar resultado estruturado do OpenSERP: {}", e.getMessage());
            return "{\"query\":\"\",\"totalResults\":0,\"items\":[]}";
        }
    }
}
