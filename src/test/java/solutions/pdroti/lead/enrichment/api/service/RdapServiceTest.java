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

    @Test
    void lookup_comDomainNull_deveRetornarEmpty() {
        RdapData result = rdapService.lookup(null);
        assertNull(result.rawJson());
        verify(rdapCache, never()).getIfPresent(any());
    }

    @Test
    void lookup_comDomainBlank_deveRetornarEmpty() {
        RdapData result = rdapService.lookup("   ");
        assertNull(result.rawJson());
    }

    @Test
    void lookup_comCacheHit_deveRetornarCache() {
        RdapData cached = new RdapData(null, "Registrador X", null, null, null, null, null, null, null, null);
        when(rdapCache.getIfPresent("exemplo.com")).thenReturn(cached);

        RdapData result = rdapService.lookup("exemplo.com");

        assertEquals("Registrador X", result.registrar());
        verify(rdapCache).getIfPresent("exemplo.com");
        verifyNoInteractions(httpClient);
    }

    @Test
    void lookup_comCacheMissEHttpFalha_deveRetornarEmpty() throws Exception {
        when(rdapCache.getIfPresent("exemplo.com")).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new java.io.IOException("Timeout"));

        RdapData result = rdapService.lookup("exemplo.com");

        assertNull(result.rawJson());
        verify(rdapCache).put(eq("exemplo.com"), any(RdapData.class));
    }

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
        assertTrue(result.nameservers().contains("ns1.exemplo.com"));
        assertTrue(result.status().contains("client transfer prohibited"));
        verify(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

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
                .thenReturn(identityJson)   // 1ª chamada: Identity Digital
                .thenReturn(registroBrJson); // 2ª chamada: Registro.br

        RdapData result = rdapService.lookup("exemplo.com.br");

        assertNotNull(result.rawJson());
        assertEquals("Empresa Ltda", result.registrantName());
        assertEquals("contato@empresa.com", result.registrantEmail());
        assertEquals("12.345.678/0001-90", result.taxpayerId());
        assertEquals("2021-06-15T00:00:00Z", result.registrationDate());
        assertEquals("registrobr", result.source());
        assertTrue(result.nameservers().contains("ns1.registrobr.com"));
    }

    @Test
    void lookup_comHttp404_deveRetornarEmpty() throws Exception {
        when(rdapCache.getIfPresent("exemplo.com")).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(404);

        RdapData result = rdapService.lookup("exemplo.com");

        assertNull(result.rawJson());
    }

    @Test
    void lookup_comJsonInvalido_deveRetornarEmpty() throws Exception {
        when(rdapCache.getIfPresent("exemplo.com")).thenReturn(null);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("json-invalido");

        RdapData result = rdapService.lookup("exemplo.com");

        assertNull(result.rawJson());
    }
}
