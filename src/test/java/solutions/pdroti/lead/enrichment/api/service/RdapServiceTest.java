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

    private static final String DOMAIN = "exemplo.com";
    private static final String DOMAIN_COM_BR = "exemplo.com.br";

    @Mock
    private Cache<String, RdapData> rdapCache;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private RdapService rdapService;

    @BeforeEach
    void setUp() {
        rdapService = new RdapService(new ObjectMapper(), rdapCache, httpClient);
    }

    // ========== lookup - validação de entrada ==========

    @Test
    void lookupWithNullDomainReturnsEmpty() {
        RdapData result = rdapService.lookup(null);
        assertNull(result.rawJson());
        verify(rdapCache, never()).getIfPresent(any());
    }

    @Test
    void lookupWithBlankDomainReturnsEmpty() {
        assertNull(rdapService.lookup("   ").rawJson());
    }

    // ========== lookup - cache ==========

    @Test
    void lookupWithCacheHitReturnsCached() {
        RdapData cached = new RdapData(null, "Registrador X", null, null, null, null, null, null, null, null);
        when(rdapCache.getIfPresent(DOMAIN)).thenReturn(cached);

        RdapData result = rdapService.lookup(DOMAIN);

        assertEquals("Registrador X", result.registrar());
        verifyNoInteractions(httpClient);
    }

    @Test
    void lookupWithCacheMissStoresInCache() throws Exception {
        when(rdapCache.getIfPresent(DOMAIN)).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new java.io.IOException("Timeout"));

        rdapService.lookup(DOMAIN);

        verify(rdapCache).put(eq(DOMAIN), any(RdapData.class));
    }

    // ========== lookup - Identity Digital ==========

    @Test
    void lookupWithIdentityDigitalSuccessReturnsRdapData() throws Exception {
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

        when(rdapCache.getIfPresent(DOMAIN)).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(rdapJson);

        RdapData result = rdapService.lookup(DOMAIN);

        assertNotNull(result.rawJson());
        assertEquals("HOSTINGER", result.registrar());
        assertEquals("2020-01-01T00:00:00Z", result.registrationDate());
        assertEquals("2025-01-01T00:00:00Z", result.expirationDate());
        assertEquals("identitydigital", result.source());
    }

    @Test
    void lookupWithIdentityDigitalFailureReturnsEmpty() throws Exception {
        when(rdapCache.getIfPresent(DOMAIN)).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        RdapData result = rdapService.lookup(DOMAIN);

        assertNull(result.rawJson());
    }

    @Test
    void lookupWithHttp404ReturnsEmpty() throws Exception {
        when(rdapCache.getIfPresent(DOMAIN)).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(404);

        assertNull(rdapService.lookup(DOMAIN).rawJson());
    }

    @Test
    void lookupWithInvalidJsonReturnsEmpty() throws Exception {
        when(rdapCache.getIfPresent(DOMAIN)).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("json-invalido");

        assertNull(rdapService.lookup(DOMAIN).rawJson());
    }

    // ========== lookup - .com.br com Registro.br ==========

    @Test
    void lookupWithComBrDomainQueriesBoth() throws Exception {
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

        when(rdapCache.getIfPresent(DOMAIN_COM_BR)).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(identityJson)
                .thenReturn(registroBrJson);

        RdapData result = rdapService.lookup(DOMAIN_COM_BR);

        assertEquals("Empresa Ltda", result.registrantName());
        assertEquals("contato@empresa.com", result.registrantEmail());
        assertEquals("12.345.678/0001-90", result.taxpayerId());
        assertEquals("registrobr", result.source());
        assertTrue(result.nameservers().contains("ns1.registrobr.com"));
    }

    @Test
    void lookupWithComBrAndEmptyIdentityUsesRegistroBr() throws Exception {
        when(rdapCache.getIfPresent(DOMAIN_COM_BR)).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn("{}")
                .thenReturn("{\"entities\":[{\"roles\":[\"registrant\"],\"vcardArray\":[\"vcard\",[[\"fn\",{},\"text\",\"Titular\"]]]}]}");

        RdapData result = rdapService.lookup(DOMAIN_COM_BR);

        assertEquals("Titular", result.registrantName());
        assertEquals("registrobr", result.source());
    }

    @Test
    void lookupWithComBrAndEmptyRegistroBrUsesIdentity() throws Exception {
        when(rdapCache.getIfPresent(DOMAIN_COM_BR)).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse)
                .thenThrow(new RuntimeException("Registro.br indisponível"));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn("{\"entities\":[{\"roles\":[\"registrant\"],\"vcardArray\":[\"vcard\",[[\"fn\",{},\"text\",\"Only ID\"]]]}]}");

        RdapData result = rdapService.lookup(DOMAIN_COM_BR);

        assertEquals("Only ID", result.registrantName());
        assertEquals("identitydigital", result.source());
    }

    @Test
    void lookupWithComBrAndRegistroBrFailureUsesIdentity() throws Exception {
        when(rdapCache.getIfPresent(DOMAIN_COM_BR)).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn("{\"entities\":[{\"roles\":[\"registrant\"],\"vcardArray\":[\"vcard\",[[\"fn\",{},\"text\",\"Only ID\"]]]}]}")
                .thenThrow(new RuntimeException("Registro.br timeout"));

        RdapData result = rdapService.lookup(DOMAIN_COM_BR);

        assertEquals("Only ID", result.registrantName());
    }

    // ========== processEntities - combinações de roles ==========

    @Test
    void lookupWithRegistrarAndAbuseEmailExtracts() throws Exception {
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
        when(rdapCache.getIfPresent(DOMAIN)).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        RdapData result = rdapService.lookup(DOMAIN);

        assertEquals("GoDaddy", result.registrar());
        assertEquals("abuse@godaddy.com", result.registrantEmail());
    }

    @Test
    void lookupWithAdministrativeEntityExtracts() throws Exception {
        String json = """
                {"entities": [{
                    "roles": ["administrative"],
                    "vcardArray": ["vcard", [
                        ["fn", {}, "text", "Admin User"],
                        ["email", {}, "text", "admin@exemplo.com"]
                    ]]
                }]}
                """;
        when(rdapCache.getIfPresent(DOMAIN)).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        RdapData result = rdapService.lookup(DOMAIN);

        assertEquals("Admin User", result.registrantName());
        assertEquals("admin@exemplo.com", result.registrantEmail());
    }

    @Test
    void lookupWithTechnicalEntityExtracts() throws Exception {
        String json = """
                {"entities": [{
                    "roles": ["technical"],
                    "vcardArray": ["vcard", [
                        ["fn", {}, "text", "Tech Contact"],
                        ["email", {}, "text", "tech@exemplo.com"]
                    ]]
                }]}
                """;
        when(rdapCache.getIfPresent(DOMAIN)).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        RdapData result = rdapService.lookup(DOMAIN);

        assertEquals("Tech Contact", result.registrantName());
        assertEquals("tech@exemplo.com", result.registrantEmail());
    }

    @Test
    void lookupWithMultipleRolesExtracts() throws Exception {
        String json = """
                {"entities": [
                    {"roles": ["registrar"], "vcardArray": ["vcard", [["fn", {}, "text", "Registrar"]]]},
                    {"roles": ["registrant"], "vcardArray": ["vcard", [["fn", {}, "text", "Registrant"]]]}
                ]}
                """;
        when(rdapCache.getIfPresent(DOMAIN)).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        RdapData result = rdapService.lookup(DOMAIN);

        assertEquals("Registrar", result.registrar());
        assertEquals("Registrant", result.registrantName());
    }

    // ========== VCard parsing edge cases ==========

    @Test
    void lookupWithMalformedVcardArrayIgnores() throws Exception {
        String json = """
                {"entities": [{
                    "roles": ["registrant"],
                    "vcardArray": "invalido"
                }]}
                """;
        when(rdapCache.getIfPresent(DOMAIN)).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        RdapData result = rdapService.lookup(DOMAIN);

        assertNull(result.registrantName());
    }

    @Test
    void lookupWithVcardWithoutArrayIgnores() throws Exception {
        String json = """
                {"entities": [{
                    "roles": ["registrant"]
                }]}
                """;
        when(rdapCache.getIfPresent(DOMAIN)).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        assertNull(rdapService.lookup(DOMAIN).registrantName());
    }

    @Test
    void lookupWithVcardEmptyPropsIgnores() throws Exception {
        String json = """
                {"entities": [{
                    "roles": ["registrant"],
                    "vcardArray": ["vcard", []]
                }]}
                """;
        when(rdapCache.getIfPresent(DOMAIN)).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        assertNull(rdapService.lookup(DOMAIN).registrantName());
    }

    @Test
    void lookupWithVcardPropWithoutField3Ignores() throws Exception {
        String json = """
                {"entities": [{
                    "roles": ["registrant"],
                    "vcardArray": ["vcard", [["fn", {}, "text"]]]
                }]}
                """;
        when(rdapCache.getIfPresent(DOMAIN)).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        assertNull(rdapService.lookup(DOMAIN).registrantName());
    }

    // ========== PublicIds/CPF-CNPJ edge cases ==========

    @Test
    void lookupWithPublicIdCpfExtracts() throws Exception {
        String json = """
                {"entities": [{
                    "roles": ["registrant"],
                    "vcardArray": ["vcard", [["fn", {}, "text", "Pessoa Física"]]],
                    "publicIds": [{"type": "cpf", "identifier": "123.456.789-00"}]
                }]}
                """;
        when(rdapCache.getIfPresent(DOMAIN)).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        assertEquals("123.456.789-00", rdapService.lookup(DOMAIN).taxpayerId());
    }

    @Test
    void lookupWithPublicIdOtherTypeIgnores() throws Exception {
        String json = """
                {"entities": [{
                    "roles": ["registrant"],
                    "vcardArray": ["vcard", [["fn", {}, "text", "Pessoa"]]],
                    "publicIds": [{"type": "passport", "identifier": "AB123456"}]
                }]}
                """;
        when(rdapCache.getIfPresent(DOMAIN)).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        assertNull(rdapService.lookup(DOMAIN).taxpayerId());
    }

    @Test
    void lookupWithoutPublicIdsReturnsNull() throws Exception {
        String json = """
                {"entities": [{
                    "roles": ["registrant"],
                    "vcardArray": ["vcard", [["fn", {}, "text", "Pessoa"]]]
                }]}
                """;
        when(rdapCache.getIfPresent(DOMAIN)).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        assertNull(rdapService.lookup(DOMAIN).taxpayerId());
    }

    // ========== Eventos RDAP edge cases ==========

    @Test
    void lookupWithoutEventsReturnsNullDates() throws Exception {
        String json = """
                {"nameservers": [], "events": []}
                """;
        when(rdapCache.getIfPresent(DOMAIN)).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        RdapData result = rdapService.lookup(DOMAIN);

        assertNull(result.registrationDate());
        assertNull(result.expirationDate());
    }

    @Test
    void lookupWithEventsNoMatchReturnsNullDates() throws Exception {
        String json = """
                {"events": [
                    {"eventAction": "transfer", "eventDate": "2023-01-01T00:00:00Z"}
                ]}
                """;
        when(rdapCache.getIfPresent(DOMAIN)).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        RdapData result = rdapService.lookup(DOMAIN);

        assertNull(result.registrationDate());
        assertNull(result.expirationDate());
    }

    // ========== Nameservers e Status vazios ==========

    @Test
    void lookupWithoutNameserversReturnsEmptyList() throws Exception {
        String json = "{}";
        when(rdapCache.getIfPresent(DOMAIN)).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        assertTrue(rdapService.lookup(DOMAIN).nameservers().isEmpty());
        assertTrue(rdapService.lookup(DOMAIN).status().isEmpty());
    }

    // ========== Identidade Digital com character case ==========

    @Test
    void lookupIgnoresDomainCase() {
        RdapData cached = new RdapData(null, "Case Insensitive", null, null, null, null, null, null, null, null);
        when(rdapCache.getIfPresent(DOMAIN)).thenReturn(cached);

        RdapData result = rdapService.lookup("EXEMPLO.COM");

        assertEquals("Case Insensitive", result.registrar());
        verify(rdapCache).getIfPresent(DOMAIN);
    }

    // ========== Registro.br com sub-entities sem abuse ==========

    @Test
    void lookupWithSubEntitiesNoAbuseIgnores() throws Exception {
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
        when(rdapCache.getIfPresent(DOMAIN)).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);

        RdapData result = rdapService.lookup(DOMAIN);

        assertEquals("Reg", result.registrar());
        assertNotEquals("tech@reg.com", result.registrantEmail());
    }

    // ========== mergeResults - fallback fields ==========

    @Test
    void lookupWithComBrAndIdentityHasRegistrarFallsBack() throws Exception {
        String identityJson = """
                {"entities": [{
                    "roles": ["registrar"],
                    "vcardArray": ["vcard", [["fn", {}, "text", "Fallback Registrar"]]]
                }]}
                """;
        String registroBrJson = """
                {"entities": [{
                    "roles": ["registrant"],
                    "vcardArray": ["vcard", [["fn", {}, "text", "Registrant Only"]]]
                }]}
                """;
        when(rdapCache.getIfPresent(DOMAIN_COM_BR)).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(identityJson)
                .thenReturn(registroBrJson);

        RdapData result = rdapService.lookup(DOMAIN_COM_BR);

        // Registrar deve vir do fallback (Identity) pois Registro.br não tem registrar
        assertEquals("Fallback Registrar", result.registrar());
        // RegistrantName deve vir do preferred (Registro.br)
        assertEquals("Registrant Only", result.registrantName());
    }

    @Test
    void lookupWithComBrAndIdentityHasNameserversFallsBack() throws Exception {
        String identityJson = """
                {"nameservers": [{"ldhName": "ns1.fallback.com"}],
                 "status": ["client hold"],
                 "events": [
                    {"eventAction": "registration", "eventDate": "2020-01-01T00:00:00Z"},
                    {"eventAction": "expiration", "eventDate": "2025-01-01T00:00:00Z"}
                 ],
                 "entities": [{
                    "roles": ["registrar"],
                    "vcardArray": ["vcard", [["fn", {}, "text", "Registrar"]]]
                 }]}
                """;
        String registroBrJson = """
                {"entities": [{
                    "roles": ["registrant"],
                    "vcardArray": ["vcard", [["fn", {}, "text", "Titular"]]]
                }]}
                """;
        when(rdapCache.getIfPresent(DOMAIN_COM_BR)).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(identityJson)
                .thenReturn(registroBrJson);

        RdapData result = rdapService.lookup(DOMAIN_COM_BR);

        assertTrue(result.nameservers().contains("ns1.fallback.com"));
        assertTrue(result.status().contains("client hold"));
        assertEquals("2020-01-01T00:00:00Z", result.registrationDate());
        assertEquals("2025-01-01T00:00:00Z", result.expirationDate());
    }

    // ========== InterruptedException em fetchJson ==========

    @Test
    void lookupComInterruptedException_deveReinterromperERetornarEmpty() throws Exception {
        when(rdapCache.getIfPresent(DOMAIN)).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("Thread foi interrompida"));

        RdapData result = rdapService.lookup(DOMAIN);

        assertNull(result.rawJson());
    }

    @Test
    void lookupComBrInterruptedException_deveReinterromperERetornarEmpty() throws Exception {
        String identityJson = """
                {"entities": [{"roles": ["registrar"], "vcardArray": ["vcard", [["fn", {}, "text", "Reg"]]]}]}
                """;
        when(rdapCache.getIfPresent(DOMAIN_COM_BR)).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse)
                .thenThrow(new InterruptedException("Registro.br interrompido"));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(identityJson);

        RdapData result = rdapService.lookup(DOMAIN_COM_BR);

        // Identity Digital funcionou, mas Registro.br foi interrompido
        assertEquals("Reg", result.registrar());
    }
}
