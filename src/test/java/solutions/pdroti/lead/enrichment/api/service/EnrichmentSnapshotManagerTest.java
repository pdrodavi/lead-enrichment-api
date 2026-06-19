package solutions.pdroti.lead.enrichment.api.service;

import org.junit.jupiter.api.Test;
import solutions.pdroti.lead.enrichment.api.model.Lead;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnrichmentSnapshotManagerTest {

    @Test
    void takeSnapshot_deveCapturarCampos() {
        Lead lead = createFullLead();
        var snapshot = EnrichmentSnapshotManager.takeSnapshot(lead);

        assertNotNull(snapshot);
    }

    @Test
    void restoreIfEmpty_comLeadVazio_deveRestaurarTodosCampos() {
        Lead lead = createFullLead();
        var snapshot = EnrichmentSnapshotManager.takeSnapshot(lead);

        // Limpa todos os campos enriquecidos
        Lead emptyLead = Lead.builder().build();

        snapshot.restoreIfEmpty(emptyLead);

        assertEquals(List.of("mx1.com"), emptyLead.getDnsMxRecords());
        assertEquals(List.of("192.168.1.1"), emptyLead.getDnsARecords());
        assertEquals(List.of("React"), emptyLead.getTechnologies());
        assertEquals(List.of("https://linkedin.com/in/joao"), emptyLead.getSocialLinks());
        assertEquals(List.of("joao@exemplo.com"), emptyLead.getExposedEmails());
        assertEquals(List.of("11999998888"), emptyLead.getExposedPhones());
        assertNotNull(emptyLead.getOpenSerpRawData());
        assertNotNull(emptyLead.getRdapRawData());
        assertEquals("Registrador Teste", emptyLead.getRdapRegistrar());
        assertEquals("João Silva", emptyLead.getRdapRegistrantName());
        assertEquals("contato@registro.com", emptyLead.getRdapRegistrantEmail());
        assertNotNull(emptyLead.getRdapRegistrationDate());
        assertNotNull(emptyLead.getRdapExpirationDate());
    }

    @Test
    void restoreIfEmpty_comDadosExistentes_naoDeveSobrescrever() {
        Lead lead = createFullLead();
        var snapshot = EnrichmentSnapshotManager.takeSnapshot(lead);

        // Cria um lead com dados NOVOS
        Lead newLead = Lead.builder()
                .dnsMxRecords(List.of("novo-mx.com"))
                .technologies(List.of("Vue.js"))
                .rdapRegistrar("Novo Registrador")
                .build();

        snapshot.restoreIfEmpty(newLead);

        // Dados existentes NÃO devem ser sobrescritos
        assertEquals(List.of("novo-mx.com"), newLead.getDnsMxRecords());
        assertEquals(List.of("Vue.js"), newLead.getTechnologies());
        assertEquals("Novo Registrador", newLead.getRdapRegistrar());
    }

    @Test
    void restoreIfEmpty_campoStringVazio_deveRestaurar() {
        Lead lead = createFullLead();
        var snapshot = EnrichmentSnapshotManager.takeSnapshot(lead);

        Lead leadComCampoVazio = Lead.builder()
                .rdapRegistrar("")
                .rdapSource("")
                .build();

        snapshot.restoreIfEmpty(leadComCampoVazio);

        assertEquals("Registrador Teste", leadComCampoVazio.getRdapRegistrar());
        assertEquals("registro.br", leadComCampoVazio.getRdapSource());
    }

    @Test
    void restoreIfEmpty_campoDateNull_deveRestaurar() {
        Lead lead = createFullLead();
        var snapshot = EnrichmentSnapshotManager.takeSnapshot(lead);

        Lead leadSemDatas = Lead.builder().build();
        snapshot.restoreIfEmpty(leadSemDatas);

        assertNotNull(leadSemDatas.getRdapRegistrationDate());
        assertNotNull(leadSemDatas.getRdapExpirationDate());
    }

    private Lead createFullLead() {
        return Lead.builder()
                .dnsMxRecords(List.of("mx1.com"))
                .dnsARecords(List.of("192.168.1.1"))
                .dnsAaaaRecords(List.of("::1"))
                .dnsCnameRecords(List.of("alias.exemplo.com"))
                .dnsTxtRecords(List.of("v=spf1 include:_spf.google.com"))
                .technologies(List.of("React"))
                .socialLinks(List.of("https://linkedin.com/in/joao"))
                .socialProfileSummaries(List.of("Resumo do perfil"))
                .exposedEmails(List.of("joao@exemplo.com"))
                .exposedPhones(List.of("11999998888"))
                .nameMentions(List.of("João Silva encontrado em https://exemplo.com"))
                .foundDocuments(List.of("https://exemplo.com/doc.pdf"))
                .discoveredUrls(List.of("https://exemplo.com"))
                .openSerpRawData("{\"query\":\"João Silva\"}")
                .rdapRawData("{\"registrar\":\"Teste\"}")
                .rdapRegistrar("Registrador Teste")
                .rdapRegistrantName("João Silva")
                .rdapRegistrantEmail("contato@registro.com")
                .rdapRegistrationDate(LocalDateTime.of(2020, 1, 1, 0, 0))
                .rdapExpirationDate(LocalDateTime.of(2026, 12, 31, 0, 0))
                .rdapNameservers(List.of("ns1.exemplo.com"))
                .rdapStatus(List.of("client transfer prohibited"))
                .rdapTaxpayerId("12345678901234")
                .rdapSource("registro.br")
                .build();
    }
}
