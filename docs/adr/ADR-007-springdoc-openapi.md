# ADR-007: Documentação de API com SpringDoc OpenAPI 2.5 + Swagger UI

## Status

Aceito

## Contexto

A API REST precisa de documentação interativa e padronizada para consumo por integradores e testadores. Requisitos:

- Documentação auto-gerada a partir do código (evitar sincronização manual)
- UI interativa para testes (Swagger UI)
- Schema OpenAPI 3.0 exportável para geração de clientes
- Documentação de esquemas de segurança (X-API-KEY)
- Descrição de servidores (produção e desenvolvimento)

## Decisão

Utilizar **SpringDoc OpenAPI** (`springdoc-openapi-starter-webmvc-ui` v2.5.0) com configuração declarativa via `@Bean`.

### Configuração

```java
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
                new Server().url("http://localhost:${PORT:8081}")
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
```

### Anotações nos DTOs

```java
@Schema(description = "Requisição para enriquecimento de um lead")
public class LeadRequest {
    @Schema(description = "Email do lead", example = "contato@exemplo.com")
    private String email;
}
```

### Endpoints Disponíveis

| URL | Descrição |
|---|---|
| `/swagger-ui.html` | UI interativa do Swagger |
| `/swagger-ui/index.html` | UI interativa (URL canônica) |
| `/v3/api-docs` | JSON OpenAPI 3.0 |
| `/v3/api-docs.yaml` | YAML OpenAPI 3.0 |

## Consequências

- Positivas:
  - Documentação sempre sincronizada com o código
  - UI interativa dispensa Postman/curl para testes rápidos
  - Schema OpenAPI exportável (integração com geradores de cliente)
  - Documentação do esquema de segurança (X-API-KEY) automaticamente

- Negativas:
  - Dependência adicional (`springdoc-openapi-starter-webmvc-ui`)
  - Endpoints Swagger expostos em produção (deveriam ser restritos via proxy reverso)
  - Memória adicional para renderização da UI

## Referências

- [SpringDoc OpenAPI](https://springdoc.org/)
- [OpenAPI 3.0 Specification](https://spec.openapis.org/oas/v3.0.3)
- [Swagger UI](https://swagger.io/tools/swagger-ui/)
