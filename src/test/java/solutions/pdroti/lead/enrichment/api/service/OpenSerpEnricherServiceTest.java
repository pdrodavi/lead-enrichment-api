package solutions.pdroti.lead.enrichment.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import solutions.pdroti.lead.enrichment.api.model.Lead;

import java.util.List;
import java.util.concurrent.Executor;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)

@ExtendWith(MockitoExtension.class)
class OpenSerpEnricherServiceTest {

    @Mock
    private OpenSerpSearchService openSerpSearch;

    @Mock
    private SocialDiscoveryService socialDiscoveryService;

    private ObjectMapper objectMapper;
    private OpenSerpEnricherService openSerpEnricherService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        openSerpEnricherService = new OpenSerpEnricherService(
                openSerpSearch, socialDiscoveryService, objectMapper, Runnable::run);
    }

    @Test
    void enrich_comTodosResultadosVazios_deveDefinirListasVazias() {
        when(openSerpSearch.searchPerson(anyString(), anyInt())).thenReturn(new JsonArray());
        when(openSerpSearch.searchDocuments(anyString(), anyInt())).thenReturn(new JsonArray());
        when(openSerpSearch.searchSocialMedia(anyString(), anyInt())).thenReturn(new JsonArray());
        when(openSerpSearch.searchProfessional(anyString(), anyInt())).thenReturn(new JsonArray());
        when(openSerpSearch.searchContact(anyString(), anyInt())).thenReturn(new JsonArray());
        when(openSerpSearch.searchNews(anyString(), anyInt())).thenReturn(new JsonArray());
        when(socialDiscoveryService.getSocialDomains()).thenReturn(List.of());

        Lead lead = Lead.builder().build();
        openSerpEnricherService.enrich(lead, "João Silva");

        assertTrue(lead.getSocialLinks().isEmpty());
        assertTrue(lead.getExposedEmails().isEmpty());
        assertTrue(lead.getNameMentions().isEmpty());
        assertTrue(lead.getFoundDocuments().isEmpty());
        assertNotNull(lead.getOpenSerpRawData());
    }

    @Test
    void enrich_comResultadosValidos_devePreencherLead() {
        when(socialDiscoveryService.getSocialDomains()).thenReturn(
                List.of("linkedin.com", "github.com", "facebook.com"));

        JsonArray socialResults = new JsonArray();
        JsonObject socialItem = new JsonObject();
        socialItem.addProperty("title", "João Silva - LinkedIn");
        socialItem.addProperty("url", "https://linkedin.com/in/joaosilva");
        socialItem.addProperty("snippet", "Perfil do João Silva");
        socialItem.addProperty("domain", "linkedin.com");
        socialResults.add(socialItem);

        JsonArray generalResults = new JsonArray();
        JsonObject generalItem = new JsonObject();
        generalItem.addProperty("title", "João Silva - Site");
        generalItem.addProperty("url", "https://joaosilva.com");
        generalItem.addProperty("snippet", "Site pessoal do João Silva");
        generalItem.addProperty("domain", "joaosilva.com");
        generalResults.add(generalItem);

        when(openSerpSearch.searchPerson(anyString(), anyInt())).thenReturn(generalResults);
        when(openSerpSearch.searchDocuments(anyString(), anyInt())).thenReturn(new JsonArray());
        when(openSerpSearch.searchSocialMedia(anyString(), anyInt())).thenReturn(socialResults);
        when(openSerpSearch.searchProfessional(anyString(), anyInt())).thenReturn(new JsonArray());
        when(openSerpSearch.searchContact(anyString(), anyInt())).thenReturn(new JsonArray());
        when(openSerpSearch.searchNews(anyString(), anyInt())).thenReturn(new JsonArray());

        Lead lead = Lead.builder().build();
        openSerpEnricherService.enrich(lead, "João Silva");

        assertTrue(lead.getSocialLinks().contains("https://linkedin.com/in/joaosilva"));
        assertTrue(lead.getDiscoveredUrls().contains("https://joaosilva.com"));
        assertFalse(lead.getNameMentions().isEmpty());
    }

    @Test
    void enrich_comFalhaEmUmaBusca_deveContinuarComAsDemais() {
        when(socialDiscoveryService.getSocialDomains()).thenReturn(List.of());

        when(openSerpSearch.searchPerson(anyString(), anyInt()))
                .thenThrow(new RuntimeException("Erro"));
        when(openSerpSearch.searchDocuments(anyString(), anyInt())).thenReturn(new JsonArray());
        when(openSerpSearch.searchSocialMedia(anyString(), anyInt())).thenReturn(new JsonArray());
        when(openSerpSearch.searchProfessional(anyString(), anyInt())).thenReturn(new JsonArray());
        when(openSerpSearch.searchContact(anyString(), anyInt())).thenReturn(new JsonArray());
        when(openSerpSearch.searchNews(anyString(), anyInt())).thenReturn(new JsonArray());

        Lead lead = Lead.builder().build();
        openSerpEnricherService.enrich(lead, "João Silva");

        assertNotNull(lead.getSocialLinks());
        assertNotNull(lead.getOpenSerpRawData());
    }

    @Test
    void enrich_comDocumentos_deveAdicionarNaLista() {
        when(socialDiscoveryService.getSocialDomains()).thenReturn(List.of());

        JsonArray docResults = new JsonArray();
        JsonObject docItem = new JsonObject();
        docItem.addProperty("title", "Currículo João Silva PDF");
        docItem.addProperty("url", "https://exemplo.com/curriculo.pdf");
        docItem.addProperty("snippet", "Currículo do João Silva");
        docItem.addProperty("domain", "exemplo.com");
        docResults.add(docItem);

        when(openSerpSearch.searchPerson(anyString(), anyInt())).thenReturn(new JsonArray());
        when(openSerpSearch.searchDocuments(anyString(), anyInt())).thenReturn(docResults);
        when(openSerpSearch.searchSocialMedia(anyString(), anyInt())).thenReturn(new JsonArray());
        when(openSerpSearch.searchProfessional(anyString(), anyInt())).thenReturn(new JsonArray());
        when(openSerpSearch.searchContact(anyString(), anyInt())).thenReturn(new JsonArray());
        when(openSerpSearch.searchNews(anyString(), anyInt())).thenReturn(new JsonArray());

        Lead lead = Lead.builder().build();
        openSerpEnricherService.enrich(lead, "João Silva");

        assertTrue(lead.getFoundDocuments().contains("https://exemplo.com/curriculo.pdf"));
    }

    @Test
    void enrich_comResultadosQueNaoMencionamNome_deveFiltrar() {
        when(socialDiscoveryService.getSocialDomains()).thenReturn(List.of());

        JsonArray generalResults = new JsonArray();
        JsonObject item1 = new JsonObject();
        item1.addProperty("title", "Site de vendas");
        item1.addProperty("url", "https://loja.com");
        item1.addProperty("snippet", "Promoção de produtos");
        item1.addProperty("domain", "loja.com");
        generalResults.add(item1);

        JsonObject item2 = new JsonObject();
        item2.addProperty("title", "João Silva - Site");
        item2.addProperty("url", "https://joaosilva.com");
        item2.addProperty("snippet", "Site do João Silva");
        item2.addProperty("domain", "joaosilva.com");
        generalResults.add(item2);

        when(openSerpSearch.searchPerson(anyString(), anyInt())).thenReturn(generalResults);
        when(openSerpSearch.searchDocuments(anyString(), anyInt())).thenReturn(new JsonArray());
        when(openSerpSearch.searchSocialMedia(anyString(), anyInt())).thenReturn(new JsonArray());
        when(openSerpSearch.searchProfessional(anyString(), anyInt())).thenReturn(new JsonArray());
        when(openSerpSearch.searchContact(anyString(), anyInt())).thenReturn(new JsonArray());
        when(openSerpSearch.searchNews(anyString(), anyInt())).thenReturn(new JsonArray());

        Lead lead = Lead.builder().build();
        openSerpEnricherService.enrich(lead, "João Silva");

        // O item da loja não menciona o nome e deve ser filtrado
        assertTrue(lead.getDiscoveredUrls().contains("https://joaosilva.com"));
        // O item da loja pode ou não estar na lista dependendo do filtro
        // (o filtro é por nome, então loja.com não deve estar presente)
        assertFalse(lead.getDiscoveredUrls().contains("https://loja.com"));
    }
}
