package solutions.pdroti.lead.enrichment.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionTemplate;
import solutions.pdroti.lead.enrichment.api.model.Lead;
import solutions.pdroti.lead.enrichment.api.repository.LeadRepository;
import solutions.pdroti.lead.enrichment.api.util.EmailUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeadServiceTest {

    @Mock
    private LeadRepository leadRepository;
    @Mock
    private OpenSerpEnricherService openSerpEnricherService;
    @Mock
    private DomainEnricherService domainEnricher;
    @Mock
    private DotComScrapingService dotComScrapingService;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private Executor enrichmentExecutor;

    @InjectMocks
    private LeadService leadService;

    @Captor
    private ArgumentCaptor<Lead> leadCaptor;

    private Lead existingLead;

    @BeforeEach
    void setUp() {
        existingLead = Lead.builder()
                .id(1L)
                .email("joao@exemplo.com")
                .emailHash(EmailUtils.hash("joao@exemplo.com"))
                .domain("exemplo.com")
                .name("João Silva")
                .status("ACTIVE")
                .consentGiven(true)
                .consentDate(LocalDateTime.now())
                .dataRetentionUntil(LocalDateTime.now().plusDays(365))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .version(0L)
                .build();

        // Mock enrichmentExecutor to run tasks inline
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(enrichmentExecutor).execute(any());
    }

    // ========== enrich() ==========

    @Test
    void enrich_novoLead_deveCriarEPersistir() {
        when(leadRepository.findByEmailHash(anyString())).thenReturn(Optional.empty());
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            var callback = invocation.getArgument(0);
            Lead lead = Lead.builder()
                    .id(1L)
                    .email("novo@exemplo.com")
                    .domain("exemplo.com")
                    .name("Novo Lead")
                    .status("ACTIVE")
                    .build();
            // Simula o save
            return lead;
        });

        Lead result = leadService.enrich("novo@exemplo.com", "exemplo.com", "Novo Lead");

        assertNotNull(result);
        verify(openSerpEnricherService).enrich(any(Lead.class), eq("Novo Lead"));
        verify(domainEnricher).enrich(any(Lead.class), eq("exemplo.com"), eq("Novo Lead"));
        verify(transactionTemplate).execute(any());
    }

    @Test
    void enrich_leadExistente_deveReenriquecer() {
        when(leadRepository.findByEmailHash(anyString())).thenReturn(Optional.of(existingLead));
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            var callback = invocation.getArgument(0);
            return existingLead;
        });

        Lead result = leadService.enrich("joao@exemplo.com", "exemplo.com", "João Silva");

        assertNotNull(result);
        verify(openSerpEnricherService).enrich(any(Lead.class), eq("João Silva"));
        verify(domainEnricher).enrich(any(Lead.class), eq("exemplo.com"), eq("João Silva"));
    }

    @Test
    void enrich_dominioPessoal_devePularDomainEnricher() {
        when(leadRepository.findByEmailHash(anyString())).thenReturn(Optional.of(existingLead));
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> existingLead);

        leadService.enrich("joao@gmail.com", "gmail.com", "João Silva");

        verify(openSerpEnricherService).enrich(any(Lead.class), eq("João Silva"));
        verify(domainEnricher, never()).enrich(any(), anyString(), anyString());
    }

    @Test
    void enrich_semDominio_deveRodarDotComScraping() {
        when(leadRepository.findByEmailHash(anyString())).thenReturn(Optional.empty());
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            Lead lead = Lead.builder().id(2L).name("Teste").status("ACTIVE").build();
            return lead;
        });

        leadService.enrich("teste@exemplo.com", null, "Teste");

        verify(dotComScrapingService).scrapeDotComSites(any(Lead.class), eq("Teste"));
    }

    // ========== findById() ==========

    @Test
    void findById_leadAtivo_deveRetornarLead() {
        when(leadRepository.findById(1L)).thenReturn(Optional.of(existingLead));

        Optional<Lead> result = leadService.findById("1");

        assertTrue(result.isPresent());
        assertEquals("João Silva", result.get().getName());
    }

    @Test
    void findById_leadDeletado_deveRetornarVazio() {
        Lead deletedLead = Lead.builder().id(2L).status("DELETED").build();
        when(leadRepository.findById(2L)).thenReturn(Optional.of(deletedLead));

        Optional<Lead> result = leadService.findById("2");

        assertFalse(result.isPresent());
    }

    @Test
    void findById_leadInexistente_deveRetornarVazio() {
        when(leadRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Lead> result = leadService.findById("99");

        assertFalse(result.isPresent());
    }

    @Test
    void findById_idInvalido_deveRetornarVazio() {
        Optional<Lead> result = leadService.findById("abc");
        assertFalse(result.isPresent());
        verify(leadRepository, never()).findById(any());
    }

    // ========== listAll() ==========

    @Test
    void listAll_deveRetornarPagina() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Lead> page = new PageImpl<>(List.of(existingLead));
        when(leadRepository.findByStatus("ACTIVE", pageable)).thenReturn(page);

        Page<Lead> result = leadService.listAll(pageable);

        assertEquals(1, result.getTotalElements());
        verify(leadRepository).findByStatus("ACTIVE", pageable);
    }

    // ========== findByDomain() ==========

    @Test
    void findByDomain_comDominioValido_deveRetornarPagina() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Lead> page = new PageImpl<>(List.of(existingLead));
        when(leadRepository.findByDomainAndStatus("exemplo.com", "ACTIVE", pageable)).thenReturn(page);

        Page<Lead> result = leadService.findByDomain("exemplo.com", pageable);

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void findByDomain_dominioVazio_deveRetornarPaginaVazia() {
        Page<Lead> result = leadService.findByDomain("", PageRequest.of(0, 20));
        assertTrue(result.isEmpty());
    }

    // ========== update() ==========

    @Test
    void update_deveAtualizarEReenriquecer() {
        when(leadRepository.findById(1L)).thenReturn(Optional.of(existingLead));
        when(leadRepository.findByEmailHash(anyString())).thenReturn(Optional.empty());
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> existingLead);

        Lead result = leadService.update("1", "novo@exemplo.com", "novo.com", "João Atualizado");

        assertNotNull(result);
        assertEquals("João Atualizado", result.getName());
        verify(openSerpEnricherService).enrich(any(Lead.class), eq("João Atualizado"));
    }

    @Test
    void update_leadInexistente_deveLancarExcecao() {
        when(leadRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
            leadService.update("99", "email@teste.com", "teste.com", "Nome"));
    }

    @Test
    void update_emailJaExistenteEmOutroLead_deveLancarExcecao() {
        Lead outroLead = Lead.builder().id(2L).build();
        when(leadRepository.findById(1L)).thenReturn(Optional.of(existingLead));
        when(leadRepository.findByEmailHash(EmailUtils.hash("outro@exemplo.com")))
                .thenReturn(Optional.of(outroLead));

        assertThrows(IllegalArgumentException.class, () ->
            leadService.update("1", "outro@exemplo.com", "exemplo.com", "João Silva"));
    }

    // ========== enrichWithDomainLeads() ==========

    @Test
    void enrichWithDomainLeads_deveRetornarLeadsDoDominio() {
        when(leadRepository.findByEmailHash(anyString())).thenReturn(Optional.of(existingLead));
        when(leadRepository.findByDomainAndStatus("exemplo.com", "ACTIVE"))
                .thenReturn(List.of(existingLead));
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> existingLead);

        var result = leadService.enrichWithDomainLeads("joao@exemplo.com", "exemplo.com", "João Silva");

        assertNotNull(result.enriched());
        assertFalse(result.domainLeads().isEmpty());
        assertEquals(1, result.domainLeads().size());
    }

    // ========== filterSocialLinksByPerson (via enrich internamente) ==========

    @Test
    void enrich_socialLinksDevemSerFiltradosPorNome() {
        // Lead com socialLinks que não correspondem ao nome
        Lead leadComLinks = Lead.builder()
                .id(1L)
                .email("joao@exemplo.com")
                .name("João Silva")
                .domain("exemplo.com")
                .status("ACTIVE")
                .socialLinks(new ArrayList<>(List.of(
                    "https://linkedin.com/in/joaosilva",
                    "https://facebook.com/pessoas",
                    "https://github.com/joaosilva"
                )))
                .build();

        when(leadRepository.findByEmailHash(anyString())).thenReturn(Optional.of(leadComLinks));
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> leadComLinks);

        leadService.enrich("joao@exemplo.com", "exemplo.com", "João Silva");

        // LinkedIn contém "joaosilva" (nome completo sem espaço) → deve manter
        // GitHub contém "joaosilva" → deve manter
        // Facebook "pessoas" não contém termos do nome → deve ser removido
        assertEquals(2, leadComLinks.getSocialLinks().size());
    }
}
