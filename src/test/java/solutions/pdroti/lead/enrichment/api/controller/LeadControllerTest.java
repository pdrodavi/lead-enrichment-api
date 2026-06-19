package solutions.pdroti.lead.enrichment.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import solutions.pdroti.lead.enrichment.api.dto.LeadRequest;
import solutions.pdroti.lead.enrichment.api.dto.LeadResponse;
import solutions.pdroti.lead.enrichment.api.dto.LeadResponseSummary;
import solutions.pdroti.lead.enrichment.api.model.Lead;
import solutions.pdroti.lead.enrichment.api.service.LeadDeletionService;
import solutions.pdroti.lead.enrichment.api.service.LeadService;
import solutions.pdroti.lead.enrichment.api.util.EmailUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadControllerTest {

    @Mock
    private LeadService leadService;
    @Mock
    private LeadDeletionService leadDeletionService;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private Cache cache;

    @InjectMocks
    private LeadController leadController;

    private ObjectMapper objectMapper;
    private Lead sampleLead;
    private LeadRequest sampleRequest;
    private LeadService.EnrichResult enrichResult;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // para suporte a Jackson

        sampleLead = Lead.builder()
                .id(1L)
                .email("joao@exemplo.com")
                .emailHash(EmailUtils.hash("joao@exemplo.com"))
                .domain("exemplo.com")
                .name("João Silva")
                .status("ACTIVE")
                .mxStatus(true)
                .dnsMxRecords(List.of("mx1.exemplo.com"))
                .dnsARecords(List.of("192.168.1.1"))
                .technologies(List.of("React"))
                .socialLinks(List.of("https://linkedin.com/in/joaosilva"))
                .exposedEmails(List.of("joao@exemplo.com"))
                .exposedPhones(List.of("11999998888"))
                .consentGiven(true)
                .consentDate(LocalDateTime.now())
                .dataRetentionUntil(LocalDateTime.now().plusDays(365))
                .createdAt(LocalDateTime.now())
                .version(0L)
                .build();

        sampleRequest = new LeadRequest();
        sampleRequest.setEmail("joao@exemplo.com");
        sampleRequest.setDomain("exemplo.com");
        sampleRequest.setName("João Silva");

        enrichResult = new LeadService.EnrichResult(sampleLead, List.of());
    }

    // ========== POST /enrich ==========

    @Test
    void enrichLead_deveRetornar200ComLeadEnriquecido() {
        when(leadService.enrichWithDomainLeads(
                "joao@exemplo.com", "exemplo.com", "João Silva"))
                .thenReturn(enrichResult);

        ResponseEntity<List<LeadResponse>> response = leadController.enrichLead(sampleRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void enrichLead_comLeadsDoDominio_deveRetornarTodos() {
        Lead lead2 = Lead.builder().id(2L).email("maria@exemplo.com").domain("exemplo.com")
                .name("Maria Silva").status("ACTIVE").build();
        var resultWithDomain = new LeadService.EnrichResult(sampleLead, List.of(sampleLead, lead2));

        when(leadService.enrichWithDomainLeads(anyString(), anyString(), anyString()))
                .thenReturn(resultWithDomain);

        ResponseEntity<List<LeadResponse>> response = leadController.enrichLead(sampleRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
    }

    // ========== GET / ==========

    @Test
    void listAll_deveRetornarPagina() {
        Page<Lead> page = new PageImpl<>(List.of(sampleLead));
        when(leadService.listAll(any())).thenReturn(page);

        ResponseEntity<Page<LeadResponseSummary>> response = leadController.listAll(PageRequest.of(0, 20));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getTotalElements());
    }

    // ========== GET /domain/{domain} ==========

    @Test
    void getLeadsByDomain_comResultados_deveRetornar200() {
        Page<Lead> page = new PageImpl<>(List.of(sampleLead));
        when(leadService.findByDomain(eq("exemplo.com"), any())).thenReturn(page);

        ResponseEntity<Page<LeadResponse>> response = leadController.getLeadsByDomain(
                "exemplo.com", PageRequest.of(0, 20));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getTotalElements());
    }

    @Test
    void getLeadsByDomain_semResultados_deveRetornar204() {
        Page<Lead> emptyPage = Page.empty();
        when(leadService.findByDomain(eq("inexistente.com"), any())).thenReturn(emptyPage);

        ResponseEntity<Page<LeadResponse>> response = leadController.getLeadsByDomain(
                "inexistente.com", PageRequest.of(0, 20));

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    // ========== PUT /{id} ==========

    @Test
    void updateLead_deveAtualizarERetornar200() {
        when(leadService.findById("1")).thenReturn(Optional.of(sampleLead));
        when(cacheManager.getCache("enrich-result")).thenReturn(cache);
        when(leadService.update(eq("1"), anyString(), anyString(), anyString()))
                .thenReturn(sampleLead);

        ResponseEntity<LeadResponse> response = leadController.updateLead("1", sampleRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(cache, times(2)).evict(anyString()); // old + new email
    }

    @Test
    void updateLead_semCache_naoDeveLancarExcecao() {
        when(leadService.findById("1")).thenReturn(Optional.of(sampleLead));
        when(cacheManager.getCache("enrich-result")).thenReturn(null);
        when(leadService.update(eq("1"), anyString(), anyString(), anyString()))
                .thenReturn(sampleLead);

        ResponseEntity<LeadResponse> response = leadController.updateLead("1", sampleRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // ========== GET /{id} ==========

    @Test
    void getLeadById_leadExistente_deveRetornar200() {
        when(leadService.findById("1")).thenReturn(Optional.of(sampleLead));

        ResponseEntity<LeadResponse> response = leadController.getLeadById("1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void getLeadById_leadInexistente_deveRetornar404() {
        when(leadService.findById("99")).thenReturn(Optional.empty());

        ResponseEntity<LeadResponse> response = leadController.getLeadById("99");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // ========== DELETE /{id} ==========

    @Test
    void deleteLead_comSucesso_deveRetornar200() {
        when(leadService.findById("1")).thenReturn(Optional.of(sampleLead));
        when(cacheManager.getCache("enrich-result")).thenReturn(cache);
        when(leadDeletionService.hardDelete("1")).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = leadController.deleteLead("1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("1", response.getBody().get("id"));
        verify(cache).evict(anyString());
    }

    @Test
    void deleteLead_naoEncontrado_deveRetornar404() {
        when(leadService.findById("99")).thenReturn(Optional.empty());
        when(leadDeletionService.hardDelete("99")).thenReturn(false);

        ResponseEntity<Map<String, Object>> response = leadController.deleteLead("99");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
