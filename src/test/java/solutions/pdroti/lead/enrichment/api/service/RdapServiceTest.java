package solutions.pdroti.lead.enrichment.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import solutions.pdroti.lead.enrichment.api.dto.RdapData;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RdapServiceTest {

    @Mock
    private Cache<String, RdapData> rdapCache;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private ObjectMapper objectMapper;
    private RdapService rdapService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        rdapService = new RdapService(objectMapper, rdapCache, httpClient);
    }

    // ========== lookup - validação de entrada ==========

    @Test
    void lookup_comDomainNull_deveRetornarEmpty() {
        RdapData result = rdapService.lookup(null);
        assertNull(result.rawJson());
        verify(rdapCache, never()).getIfPresent(any());
    }

    @Test
    void lookup_comDomainBlank_deveRetornarEmpty() {
        assertNull(rdapService.lookup("   ").rawJson());
    }

    // ========== lookup - cache ==========

    @Test
    void lookup_comCacheHit_deveRetornarCache() {
        RdapData cached = new RdapData(null, "Registrador X", null, null, null, null, null, null, null, null);
        when(rdapCache.getIfPresent("exemplo.com")).thenReturn(cached);

        RdapData result = rdapService.lookup("exemplo.com");

        assertEquals("Registrador X", result.registrar());
        verifyNoInteractions(httpClient);
    }

    @Test
    void lookup_comCacheMiss_deveArmazenarNoCache() throws Exception {
        when(rdapCache.getIfPresent("exemplo.com")).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new java.io.IOException("Timeout"));

        rdapService.lookup("exemplo.com");

        verify(rdapCache).put(eq("exemplo.com"), any(RdapData.class));
    }

    // ========== lookup - Identity Digital ==========

    @Test
    void lookup_comSucessoIdentityDigital_deveRetornarRdapData() throws Exception {
        String rdapJson = """
                {
                    "nameservers": [{"ldhName": "ns1.exemplo.com"}],
                    "status": ["client transfer prohibited"],
                    "events": [
                        {"eventAction": "registration", "eventDate": "2020-01-01T00:00:00Z"},
                        {"eventAction": "expiration", "eventDate": "2025-01-01T00:00:00Z"}
                    ],
                    "entities": [{
                        "roles": ["registrar"],
                        "vcardArray": ["vcard", [
                            ["fn", {}, "text", "HOSTINGER"],
                            ["email", {}, "text", "abuse@hostinger.com"]
                        ]]
                    }]
                }
                """;

        when(rdapCache.getIfPresent("exemplo.com")).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(rdapJson);

        RdapData result = rdapService.lookup("exemplo.com");

        assertNotNull(result.rawJson());
        assertEquals("HOSTINGER", result.registrar());
        assertEquals("2020-01-01T00:00:00Z", result.registrationDate());
        assertEquals("2025-01-01T00:00:00Z", result.expirationDate());
        assertEquals("identitydigital", result.source());
    }

    @Test
    void lookup_comIdentityDigitalFalha_deveRetornarEmpty() throws Exception {
        when(rdapCache.getIfPresent("exemplo.com")).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        RdapData result = rdapService.lookup("exemplo.com");

        assertNull(result.rawJson());
    }

    @Test
    void lookup_comHttp404_deveRetornarEmpty() throws Exception {
        when(rdapCache.getIfPresent("exemplo.com")).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(404);

        assertNull(rdapService.lookup("exemplo.com").rawJson());
    }

    @Test
    void lookup_comJsonInvalido_deveRetornarEmpty() throws Exception {
        when(rdapCache.getIfPresent("exemplo.com")).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("json-invalido");

        assertNull(rdapService.lookup("exemplo.com").rawJson());
    }

    // ========== lookup - .com.br com Registro.br ==========

    @Test
    void lookup_dominioComBr_deveConsultarIdentityDigitalERegistroBr() throws Exception {
        String identityJson = """
                {"nameservers": [{"ldhName": "ns1.exemplo.com.br"}], "status": [], "events": []}
                """;
        String registroBrJson = """
                {
                    "nameservers": [{"ldhName": "ns1.registrobr.com"}],
                    "status": ["published"],
                    "events": [
                        {"eventAction": "registration", "eventDate": "2021-06-15T00:00:00Z"},
                        {"eventAction": "expiration", "eventDate": "2026-06-15T00:00:00Z"}
                    ],
                    "entities": [{
                        "roles": ["registrant"],
                        "vcardArray": ["vcard", [
                            ["fn", {}, "text", "Empresa Ltda"],
                            ["email", {}, "text", "contato@empresa.com"]
                        ]],
                        "publicIds": [{"type": "cnpj", "identifier": "12.345.678/0001-90"}]
                    }]
                }
                """;

        when(rdapCache.getIfPresent("exemplo.com.br")).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(identityJson)
                .thenReturn(registroBrJson);

        RdapData result = rdapService.lookup("exemplo.com.br");

        assertEquals("Empresa Ltda", result.registrantName());
        assertEquals("contato@empresa.com", result.registrantEmail());
        assertEquals("12.345.678/0001-90", result.taxpayerId());
        assertEquals("registrobr", result.source());
        assertTrue(result.nameservers().contains("ns1.registrobr.com"));
    }

    @Test
    void lookup_dominioComBr_comIdentityDigitalVazio_deveUsarRegistroBr() throws Exception {
        when(rdapCache.getIfPresent("exemplo.com.br")).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        // Identity retorna JSON sem entidades relevantes
        when(httpResponse.body())
                .thenReturn("{}")  // Identity: sem nomeservers/status
                .thenReturn("{\"entities\":[{\"roles\":[\"registrant\"],\"vcardArray\":[\"vcard\",[[\"fn\",{},\"text\",\"Titular\"]]]}]}");

        RdapData result = rdapService.lookup("exemplo.com.br");

        assertEquals("Titular", result.registrantName());
        assertEquals("registrobr", result.source());
    }

    @Test
    void lookup_dominioComBr_comRegistroBrVazio_deveUsarIdentityDigital() throws Exception {
        when(rdapCache.getIfPresent("exemplo.com.br")).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse)        // Identity Digital OK
                .thenThrow(new RuntimeException("Registro.br indisponível")); // Registro.br falha
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn("{\"entities\":[{\"roles\":[\"registrant\"],\"vcardArray\":[\"vcard\",[[\"fn\",{},\"text\",\"Only ID\"]]]}]}");

        RdapData result = rdapService.lookup("exemplo.com.br");

        assertEquals("Only ID", result.registrantName());
        assertEquals("identitydigital", result.source());
    }

    @Test
    void lookup_dominioComBr_comRegistroBrFalha_deveUsarIdentityDigital() throws Exception {
        when(rdapCache.getIfPresent("exemplo.com.br")).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn("{\"entities\":[{\"roles\":[\"registrant\"],\"vcardArray\":[\"vcard\",[[\"fn\",{},\"text\",\"Only ID\"]]]}]}")
                .thenThrow(new RuntimeException("Registro.br timeout"));

        RdapData result = rdapService.lookup("exemplo.com.br");

        assertEquals("Only ID", result.registrantName());
    }

    // ========== processEntities - combinações de roles ==========

    @Test
    void lookup_comEntidadeRegistrarEAbuseEmail_deveExtrair() throws Exception {
        String json = """
                {"entities": [{
                    "roles": ["registrar"],
                    "vcardArray": ["vcard", [
                        ["fn", {}, "text", "GoDaddy"],
                        ["email", {}, "text", "noreply@godaddy.com"]
                    ]],
                    "entities": [{
                        "roles": ["abuse"],
                        "vcardArray": ["vcard", [
                            ["fn", {}, "text", "Abuse"],
                            ["email", {}, "text", "abuse@godaddy.com"]
                        ]]
                    }]
                }]}
                """;
        when(rdapCache.getIfPresent("exemplo.com")).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        RdapData result = rdapService.lookup("exemplo.com");

        assertEquals("GoDaddy", result.registrar());
        assertEquals("abuse@godaddy.com", result.registrantEmail());
    }

    @Test
    void lookup_comEntidadeAdministrativa_deveExtrair() throws Exception {
        String json = """
                {"entities": [{
                    "roles": ["administrative"],
                    "vcardArray": ["vcard", [
                        ["fn", {}, "text", "Admin User"],
                        ["email", {}, "text", "admin@exemplo.com"]
                    ]]
                }]}
                """;
        when(rdapCache.getIfPresent("exemplo.com")).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        RdapData result = rdapService.lookup("exemplo.com");

        assertEquals("Admin User", result.registrantName());
        assertEquals("admin@exemplo.com", result.registrantEmail());
    }

    @Test
    void lookup_comEntidadeTechnical_deveExtrair() throws Exception {
        String json = """
                {"entities": [{
                    "roles": ["technical"],
                    "vcardArray": ["vcard", [
                        ["fn", {}, "text", "Tech Contact"],
                        ["email", {}, "text", "tech@exemplo.com"]
                    ]]
                }]}
                """;
        when(rdapCache.getIfPresent("exemplo.com")).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        RdapData result = rdapService.lookup("exemplo.com");

        assertEquals("Tech Contact", result.registrantName());
        assertEquals("tech@exemplo.com", result.registrantEmail());
    }

    @Test
    void lookup_comEntidadesMultiplasRoles_deveExtrair() throws Exception {
        String json = """
                {"entities": [
                    {"roles": ["registrar"], "vcardArray": ["vcard", [["fn", {}, "text", "Registrar"]]]},
                    {"roles": ["registrant"], "vcardArray": ["vcard", [["fn", {}, "text", "Registrant"]]]}
                ]}
                """;
        when(rdapCache.getIfPresent("exemplo.com")).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        RdapData result = rdapService.lookup("exemplo.com");

        assertEquals("Registrar", result.registrar());
        assertEquals("Registrant", result.registrantName());
    }

    // ========== VCard parsing edge cases ==========

    @Test
    void lookup_comVcardArrayMalformado_deveIgnorar() throws Exception {
        String json = """
                {"entities": [{
                    "roles": ["registrant"],
                    "vcardArray": "invalido"
                }]}
                """;
        when(rdapCache.getIfPresent("exemplo.com")).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        RdapData result = rdapService.lookup("exemplo.com");

        assertNull(result.registrantName());
    }

    @Test
    void lookup_comVcardSemVcardArray_deveIgnorar() throws Exception {
        String json = """
                {"entities": [{
                    "roles": ["registrant"]
                }]}
                """;
        when(rdapCache.getIfPresent("exemplo.com")).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        assertNull(rdapService.lookup("exemplo.com").registrantName());
    }

    @Test
    void lookup_comVcardComPropsVazias_deveIgnorar() throws Exception {
        String json = """
                {"entities": [{
                    "roles": ["registrant"],
                    "vcardArray": ["vcard", []]
                }]}
                """;
        when(rdapCache.getIfPresent("exemplo.com")).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        assertNull(rdapService.lookup("exemplo.com").registrantName());
    }

    @Test
    void lookup_comVcardComPropSemCampo3_deveIgnorar() throws Exception {
        String json = """
                {"entities": [{
                    "roles": ["registrant"],
                    "vcardArray": ["vcard", [["fn", {}, "text"]]]
                }]}
                """;
        when(rdapCache.getIfPresent("exemplo.com")).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        assertNull(rdapService.lookup("exemplo.com").registrantName());
    }

    // ========== PublicIds/CPF-CNPJ edge cases ==========

    @Test
    void lookup_comPublicIdCpf_deveExtrair() throws Exception {
        String json = """
                {"entities": [{
                    "roles": ["registrant"],
                    "vcardArray": ["vcard", [["fn", {}, "text", "Pessoa Física"]]],
                    "publicIds": [{"type": "cpf", "identifier": "123.456.789-00"}]
                }]}
                """;
        when(rdapCache.getIfPresent("exemplo.com")).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        assertEquals("123.456.789-00", rdapService.lookup("exemplo.com").taxpayerId());
    }

    @Test
    void lookup_comPublicIdDeOutroTipo_deveIgnorar() throws Exception {
        String json = """
                {"entities": [{
                    "roles": ["registrant"],
                    "vcardArray": ["vcard", [["fn", {}, "text", "Pessoa"]]],
                    "publicIds": [{"type": "passport", "identifier": "AB123456"}]
                }]}
                """;
        when(rdapCache.getIfPresent("exemplo.com")).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        assertNull(rdapService.lookup("exemplo.com").taxpayerId());
    }

    @Test
    void lookup_semPublicIds_deveRetornarTaxpayerIdNull() throws Exception {
        String json = """
                {"entities": [{
                    "roles": ["registrant"],
                    "vcardArray": ["vcard", [["fn", {}, "text", "Pessoa"]]]
                }]}
                """;
        when(rdapCache.getIfPresent("exemplo.com")).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        assertNull(rdapService.lookup("exemplo.com").taxpayerId());
    }

    // ========== Eventos RDAP edge cases ==========

    @Test
    void lookup_semEventos_deveRetornarDatasNull() throws Exception {
        String json = """
                {"nameservers": [], "events": []}
                """;
        when(rdapCache.getIfPresent("exemplo.com")).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        RdapData result = rdapService.lookup("exemplo.com");

        assertNull(result.registrationDate());
        assertNull(result.expirationDate());
    }

    @Test
    void lookup_comEventosSemMatch_deveRetornarDatasNull() throws Exception {
        String json = """
                {"events": [
                    {"eventAction": "transfer", "eventDate": "2023-01-01T00:00:00Z"}
                ]}
                """;
        when(rdapCache.getIfPresent("exemplo.com")).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        RdapData result = rdapService.lookup("exemplo.com");

        assertNull(result.registrationDate());
        assertNull(result.expirationDate());
    }

    // ========== Nameservers e Status vazios ==========

    @Test
    void lookup_semNameservers_deveRetornarListaVazia() throws Exception {
        String json = "{}";
        when(rdapCache.getIfPresent("exemplo.com")).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        assertTrue(rdapService.lookup("exemplo.com").nameservers().isEmpty());
        assertTrue(rdapService.lookup("exemplo.com").status().isEmpty());
    }

    // ========== Identidade Digital com character case ==========

    @Test
    void lookup_deveIgnorarCaseDoDominio() throws Exception {
        RdapData cached = new RdapData(null, "Case Insensitive", null, null, null, null, null, null, null, null);
        when(rdapCache.getIfPresent("exemplo.com")).thenReturn(cached);

        RdapData result = rdapService.lookup("EXEMPLO.COM");

        assertEquals("Case Insensitive", result.registrar());
        verify(rdapCache).getIfPresent("exemplo.com");
    }

    // ========== Registro.br com sub-entities sem abuse ==========

    @Test
    void lookup_comSubEntidadesSemAbuse_deveIgnorar() throws Exception {
        String json = """
                {"entities": [{
                    "roles": ["registrar"],
                    "vcardArray": ["vcard", [["fn", {}, "text", "Reg"]]],
                    "entities": [{
                        "roles": ["technical"],
                        "vcardArray": ["vcard", [["email", {}, "text", "tech@reg.com"]]]
                    }]
                }]}
                """;
        when(rdapCache.getIfPresent("exemplo.com")).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        RdapData result = rdapService.lookup("exemplo.com");

        assertEquals("Reg", result.registrar());
        // E-mail da sub-entity sem role "abuse" não deve ser capturado
        assertNotEquals("tech@reg.com", result.registrantEmail());
    }
}
