package solutions.pdroti.lead.enrichment.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void customOpenApi_deveConterTituloEVersao() {
        OpenAPI api = config.customOpenAPI();

        Info info = api.getInfo();
        assertEquals("Lead Enrichment API", info.getTitle());
        assertEquals("1.0", info.getVersion());
        assertNotNull(info.getDescription());
    }

    @Test
    void customOpenApi_deveConterDoisServers() {
        OpenAPI api = config.customOpenAPI();

        assertEquals(2, api.getServers().size());
        assertEquals("https://api-lead-enrichment.pdroti.solutions", api.getServers().get(0).getUrl());
        assertEquals("http://localhost:8081", api.getServers().get(1).getUrl());
    }

    @Test
    void customOpenApi_deveConterSecuritySchemeApiKey() {
        OpenAPI api = config.customOpenAPI();

        SecurityRequirement securityReq = api.getSecurity().get(0);
        assertTrue(securityReq.containsKey("ApiKeyAuth"));

        SecurityScheme scheme = api.getComponents().getSecuritySchemes().get("ApiKeyAuth");
        assertNotNull(scheme);
        assertEquals("X-API-KEY", scheme.getName());
        assertEquals(SecurityScheme.Type.APIKEY, scheme.getType());
        assertEquals(SecurityScheme.In.HEADER, scheme.getIn());
    }
}
