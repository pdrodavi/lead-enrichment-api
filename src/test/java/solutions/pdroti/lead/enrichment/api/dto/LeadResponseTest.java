package solutions.pdroti.lead.enrichment.api.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import solutions.pdroti.lead.enrichment.api.model.Lead;
import solutions.pdroti.lead.enrichment.api.util.EmailUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LeadResponseTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    @Test
    void fromEntity_deveCriarResponseComDadosBasicos() {
        Lead lead = createSampleLead();
        LeadResponse response = LeadResponse.fromEntity(lead, objectMapper);

        assertEquals(lead.getId(), response.id());
        assertEquals(EmailUtils.mask(lead.getEmail()), response.emailMasked());
        assertEquals(lead.getName(), response.name());
        assertEquals(lead.getDomain(), response.domain());
        assertEquals(lead.getStatus(), response.status());
    }

    @Test
    void fromEntity_deveCriarDnsRecords() {
        Lead lead = createSampleLead();
        LeadResponse response = LeadResponse.fromEntity(lead, objectMapper);

        assertNotNull(response.dns());
        assertTrue(response.dns().mxStatus());
        assertFalse(response.dns().mxRecords().isEmpty());
        assertFalse(response.dns().aRecords().isEmpty());
    }

    @Test
    void fromEntity_deveCriarDiscoveryData() {
        Lead lead = createSampleLead();
        LeadResponse response = LeadResponse.fromEntity(lead, objectMapper);

        assertNotNull(response.discovery());
        assertFalse(response.discovery().technologies().isEmpty());
        assertFalse(response.discovery().socialLinks().isEmpty());
        assertFalse(response.discovery().exposedEmails().isEmpty());
        assertFalse(response.discovery().exposedPhones().isEmpty());
        assertFalse(response.discovery().nameMentionUrls().isEmpty());
    }

    @Test
    void fromEntity_comRdapVazio_deveRetornarRdapEmpty() {
        Lead lead = Lead.builder().id(1L).name("Teste").build();
        LeadResponse response = LeadResponse.fromEntity(lead, objectMapper);

        assertNotNull(response.rdap());
        assertNull(response.rdap().rawJson());
        assertNull(response.rdap().registrar());
    }

    @Test
    void fromEntity_comListasNull_deveRetornarListasVazias() {
        Lead lead = Lead.builder()
                .id(1L)
                .name("Teste")
                .email("teste@exemplo.com")
                .domain("exemplo.com")
                .status("ACTIVE")
                .build();

        LeadResponse response = LeadResponse.fromEntity(lead, objectMapper);

        assertNotNull(response.dns().mxRecords());
        assertTrue(response.dns().mxRecords().isEmpty());
        assertNotNull(response.discovery().technologies());
        assertTrue(response.discovery().technologies().isEmpty());
    }

    private Lead createSampleLead() {
        return Lead.builder()
                .id(1L)
                .email("joao@exemplo.com")
                .emailHash(EmailUtils.hash("joao@exemplo.com"))
                .domain("exemplo.com")
                .name("João Silva")
                .status("ACTIVE")
                .mxStatus(true)
                .dnsMxRecords(List.of("mx1.exemplo.com"))
                .dnsARecords(List.of("192.168.1.1"))
                .dnsTxtRecords(List.of("v=spf1 include:_spf.google.com"))
                .technologies(List.of("React", "Nginx"))
                .socialLinks(List.of("https://linkedin.com/in/joaosilva"))
                .exposedEmails(List.of("joao@exemplo.com"))
                .exposedPhones(List.of("11999998888"))
                .nameMentions(List.of(
                    "João Silva encontrado em https://exemplo.com/sobre",
                    "João Silva mencionado em https://exemplo.com/contato"))
                .openSerpRawData("{\"query\":\"João Silva\",\"totalResults\":1,\"items\":[]}")
                .consentGiven(true)
                .consentDate(LocalDateTime.now())
                .dataRetentionUntil(LocalDateTime.now().plusDays(365))
                .createdAt(LocalDateTime.now())
                .version(0L)
                .build();
    }
}
