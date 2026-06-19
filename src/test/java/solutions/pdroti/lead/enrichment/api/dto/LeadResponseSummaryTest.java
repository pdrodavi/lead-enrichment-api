package solutions.pdroti.lead.enrichment.api.dto;

import org.junit.jupiter.api.Test;
import solutions.pdroti.lead.enrichment.api.model.Lead;
import solutions.pdroti.lead.enrichment.api.util.EmailUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LeadResponseSummaryTest {

    @Test
    void fromEntity_deveCriarResumo() {
        Lead lead = Lead.builder()
                .id(1L)
                .email("joao@exemplo.com")
                .emailHash(EmailUtils.hash("joao@exemplo.com"))
                .domain("exemplo.com")
                .name("João Silva")
                .status("ACTIVE")
                .mxStatus(true)
                .dorkFindings(3)
                .technologies(List.of("React", "Nginx"))
                .socialLinks(List.of("https://linkedin.com/in/joaosilva"))
                .foundDocuments(List.of("https://exemplo.com/doc.pdf"))
                .nameMentions(List.of("Menção 1", "Menção 2"))
                .createdAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                .build();

        LeadResponseSummary summary = LeadResponseSummary.fromEntity(lead);

        assertEquals(1L, summary.id());
        assertEquals(EmailUtils.mask("joao@exemplo.com"), summary.emailMasked());
        assertEquals("João Silva", summary.name());
        assertEquals("exemplo.com", summary.domain());
        assertEquals("ACTIVE", summary.status());
        assertTrue(summary.mxStatus());
        assertEquals(3, summary.dorkFindings());
        assertEquals(2, summary.technologiesCount());
        assertEquals(1, summary.socialLinksCount());
        assertEquals(1, summary.documentsCount());
        assertEquals(2, summary.mentionsCount());
        assertEquals(LocalDateTime.of(2026, 1, 1, 10, 0), summary.createdAt());
    }

    @Test
    void fromEntity_comListasNull_deveRetornarContagensZero() {
        Lead lead = Lead.builder()
                .id(1L)
                .name("Teste")
                .email("teste@exemplo.com")
                .build();

        LeadResponseSummary summary = LeadResponseSummary.fromEntity(lead);

        assertEquals(0, summary.technologiesCount());
        assertEquals(0, summary.socialLinksCount());
        assertEquals(0, summary.documentsCount());
        assertEquals(0, summary.mentionsCount());
        assertFalse(summary.mxStatus());
    }
}
