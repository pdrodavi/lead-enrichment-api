# ADR-001: Stack Tecnológica — Java 17, Spring Boot 3.3 e Maven

## Status

Aceito

## Contexto

A aplicação de enriquecimento de leads precisa de um framework web moderno, seguro e com bom suporte da comunidade para implementar uma API REST que consuma serviços externos (DNS, HTTP, RDAP). As principais necessidades técnicas incluem:

- Framework REST com suporte a validação de beans
- ORM para persistência relacional
- Cliente HTTP para APIs externas
- Parsing de HTML para scraping
- Documentação automática de API
- Configuração externalizada via @ConfigurationProperties

## Decisão

Adotar a seguinte stack tecnológica:

| Tecnologia | Versão | Função | Justificativa |
|---|---|---|---|
| **Java** | 17 | Runtime | LTS mais recente com suporte estendido; records, pattern matching, sealed classes |
| **Spring Boot** | 3.3.13 | Framework principal | Maturidade, ecossistema, suporte a Spring Data JPA, Actuator, Validation |
| **Maven** | 3.8+ | Build & dependências | Declarativo, consistente, amplamente documentado |
| **Lombok** | - | Boilerplate | Redução de código (Slf4j, Builder, RequiredArgsConstructor) |
| **SpringDoc OpenAPI** | 2.5.0 | Documentação | Swagger UI automático com anotações @Schema, @Operation |
| **Spring Actuator** | - | Monitoramento | Health checks, métricas, probes de Kubernetes |
| **Spring Validation** | - | Validação | @Valid + @NotBlank/@Email nos DTOs |
| **Jackson** | - | Serialização | Padrão Spring Boot, ObjectMapper injetado via Spring |
| **Spring RestTemplate** | - | Cliente HTTP | Bean gerenciado pelo Spring em AppConfig (timeouts 5s/20s) |
| **@ConfigurationProperties** | - | Config externalizada | Prefixos YAML para TechScraperProperties e SocialDiscoveryProperties |

## Consequências

- Positivas:
  - Aplicação facilmente executável com `run.bat` ou `Ctrl+Shift+B` no VS Code
  - Documentação Swagger disponível em `/swagger-ui.html` sem configuração adicional
  - Health checks prontos para orquestração (Docker Compose, Kubernetes)
  - Código reduzido com Lombok
  - Configurações externalizadas em YAML + `.env` permitem ajustes sem recompilar
  - Segredos removidos do repositório (`.env.example` documenta variáveis obrigatórias)

- Negativas:
  - Java 17 como dependência de runtime obrigatória
  - Spring Boot 3.3.x exige Jakarta EE 10 (jakarta.* em vez de javax.*)
  - Lombok requer configuração na IDE para suporte completo
  - `.env` deve ser configurado antes da primeira execução

## Referências

- [Spring Boot 3.3 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.3-Release-Notes)
- [Java 17 Features](https://openjdk.org/projects/jdk/17/)
- [SpringDoc OpenAPI](https://springdoc.org/)
