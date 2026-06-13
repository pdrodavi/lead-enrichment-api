# ADR-001: Stack Tecnológica — Java 21, Spring Boot 3.3 e Maven

## Status

Atualizado (Jun/2026) — Migração de Java 17 → 21

## Contexto

A aplicação de enriquecimento de leads precisa de um framework web moderno, seguro e com bom suporte da comunidade para implementar uma API REST que consuma serviços externos (DNS, HTTP, RDAP). As principais necessidades técnicas incluem:

- Framework REST com suporte a validação de beans
- ORM para persistência relacional
- Cliente HTTP para APIs externas
- Parsing de HTML para scraping
- Documentação automática de API
- Configuração externalizada via @ConfigurationProperties
- Observabilidade com tracing distribuído (OpenTelemetry + Jaeger)

## Decisão

Adotar a seguinte stack tecnológica:

| Tecnologia | Versão | Função | Justificativa |
|---|---|---|---|
| **Java** | **21** | Runtime | Virtual Threads nativas para I/O-bound; records, pattern matching |
| **Spring Boot** | 3.3.13 | Framework principal | Maturidade, ecossistema, suporte a Virtual Threads e OTel |
| **Maven** | 3.9+ | Build & dependências | Necessário para compatibilidade com JDK 21 |
| **Lombok** | - | Boilerplate | Redução de código (Slf4j, Builder, RequiredArgsConstructor) |
| **SpringDoc OpenAPI** | 2.5.0 | Documentação | Swagger UI automático com anotações @Schema, @Operation |
| **Spring Actuator** | - | Monitoramento | Health checks, métricas, probes de Kubernetes |
| **Spring Validation** | - | Validação | @Valid + @NotBlank/@Email nos DTOs |
| **OpenTelemetry** | 1.38+ | Tracing | Exportação OTLP para Jaeger (HTTP/protobuf na porta 4318) |
| **Micrometer Tracing** | 1.3+ | Bridge OTel | Integração automática com Spring Boot |
| **Caffeine** | - | Cache | Cache em memória (DNS, tecnologias, links sociais) com TTL 1h |
| **Apache HttpClient 5** | - | HTTP Pooling | Connection pooling para RestTemplate (200 conexões máx) |
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
