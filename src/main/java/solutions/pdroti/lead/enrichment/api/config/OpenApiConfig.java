package solutions.pdroti.lead.enrichment.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuração do OpenAPI/Swagger para documentação da API.
 * <p>
 * Define metadados da API (nome, versão, descrição), o esquema
 * de segurança {@code X-API-KEY} para autenticação via header,
 * e o servidor com suporte a HTTPS.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Lead Enrichment API")
                        .version("1.0")
                        .description("API robusta para validação de leads e enriquecimento de dados"))
                .servers(List.of(
                        new Server().url("https://api-lead-enrichment.pdroti.solutions")
                                .description("Produção (HTTPS)"),
                        new Server().url("http://localhost:8081")
                                .description("Desenvolvimento (HTTP local)")
                ))
                .addSecurityItem(new SecurityRequirement().addList("ApiKeyAuth"))
                .components(new Components()
                        .addSecuritySchemes("ApiKeyAuth", new SecurityScheme()
                                .name("X-API-KEY")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)));
    }
}
