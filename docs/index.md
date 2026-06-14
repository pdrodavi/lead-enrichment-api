# Documentação da Lead Enrichment API

> [⬅ Voltar ao README principal](../README.md)

## Sobre

API para enriquecimento de leads com dados públicos da internet. A partir de um nome e e-mail, descobre informações sobre o domínio, tecnologias utilizadas, presença em redes sociais e dados de registro de domínio — tudo em conformidade com a LGPD.

**Stack:** Java 21 (Virtual Threads) + Spring Boot 3.3.13 + PostgreSQL 16 + Redis (cache L2) + OpenSERP (Google Search API)

## Índice da Documentação

| Documento | Descrição |
|---|---|
| [📐 Arquitetura](./architecture.md) | Diagramas, fluxos, stack tecnológica e estrutura do projeto |
| [📡 Guia da API](./api-guide.md) | Endpoints, parâmetros, exemplos de requisição/resposta e erros |
| [🚀 Guia de Deploy](./deployment.md) | Docker, variáveis de ambiente, produção e troubleshooting |
| [🔒 Segurança e LGPD](./security-lgpd.md) | Criptografia, mascaramento, autenticação, hard delete e compliance |
| [📜 OpenAPI Spec (YAML)](./openapi.yaml) | Documentação OpenAPI 3.0 completa para geração de clientes |
| [🔧 Referência Técnica](./TECHNICAL_REFERENCE.md) | Arquitetura detalhada, camadas, pipeline, performance, dependências |
| [👋 Guia de Onboarding](./ONBOARDING.md) | Configuração do ambiente, fluxo de desenvolvimento, troubleshooting |

## Architecture Decision Records (ADRs)

| ID | Título | Decisão Principal |
|---|---|---|
| [ADR-001](./adr/ADR-001-stack-tecnologica.md) | Stack Tecnológica | Java 21 + Spring Boot 3.3 + Maven + Lombok |
| [ADR-002](./adr/ADR-002-postgresql-jpa.md) | PostgreSQL + Spring Data JPA | PostgreSQL 16 com ddl-auto=update e @ElementCollection |
| [ADR-003](./adr/ADR-003-criptografia-pii-aes-gcm.md) | Criptografia de PII (LGPD) | AES-128-GCM via AttributeConverter + SHA-256 hash |
| [ADR-004](./adr/ADR-004-soft-delete-lgpd.md) | Hard Delete para LGPD | Exclusão física em 1 query via deleteById |
| [ADR-005](./adr/ADR-005-api-key-autenticacao.md) | Autenticação via API Key | Servlet Filter com validação de header X-API-KEY |
| [ADR-006](./adr/ADR-006-arquitetura-enriquecimento.md) | Arquitetura de Enriquecimento | Orquestração com 12+ serviços, merge seguro e cache L1+L2 |
| [ADR-007](./adr/ADR-007-springdoc-openapi.md) | Documentação com SpringDoc/OpenAPI | Swagger UI auto-gerado com schema de segurança |
| [ADR-008](./adr/ADR-008-mascaramento-dados-lgpd.md) | Mascaramento de Dados (LGPD) | EmailUtils com mascaramento centralizado |
| [ADR-009](./adr/ADR-009-tratamento-global-erros.md) | Tratamento Global de Erros | @RestControllerAdvice com JSON padronizado |
| [ADR-010](./adr/ADR-010-configuracao-externalizada.md) | Configuração Externalizada | @ConfigurationProperties para TechScraper, SocialDiscovery e OpenSerpProxy |

## Diagramas

### Mermaid (renderização nativa no GitHub/VS Code)

| Diagrama | Arquivo | Conteúdo |
|---|---|---|
| [🟦 Componentes + Sequência](./diagrams/mermaid-componentes-sequencia.md) | `docs/diagrams/mermaid-componentes-sequencia.md` | Diagrama de componentes (4 camadas + cache Redis), diagrama de classes (modelo de domínio) e diagrama de sequência (enriquecimento com cache L1+L2) |
| [🔀 Fluxo de Enriquecimento](./diagrams/mermaid-fluxo-enriquecimento.md) | `docs/diagrams/mermaid-fluxo-enriquecimento.md` | Diagrama de estados do Lead (PENDING → ENRICHED → DELETED), fluxograma completo vs. reduzido, merge seguro e cache |
| [⚙️ Fluxo de Processamento](./diagrams/mermaid-fluxo-processamento-api.md) | `docs/diagrams/mermaid-fluxo-processamento-api.md` | Fluxograma detalhado de requisição/resposta para todos os 6 endpoints, diagrama de contexto, @Cacheable, LeadResponseSummary |

> 💡 **Dica:** Os diagramas Mermaid renderizam automaticamente no GitHub e no VS Code (com extensão Mermaid).

## Melhorias e Refatorações Realizadas

Ao longo de ciclos de revisão de código, diversas melhorias foram implementadas:

| Categoria | Principais correções |
|---|---|
| 🔴 Segurança | Credenciais removidas para `.env`, criptografia sem fallback, API Key via filter |
| 🟢 Java 21 | Migração JDK 17 → 21 com Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`) |
| 🟢 Observabilidade | OpenTelemetry + Jaeger com captura de request/response body |
| 🟢 Performance | Cache Caffeine + Redis L2, ContentTracker (hash SHA-256), HTTP Connection Pooling, compressão Gzip, paginação |
| 🔴 Performance | Consultas DNS paralelas (5 tipos), 6 buscas OpenSERP em paralelo, merge seguro, cópia defensiva de cache |
| 🟡 Infra | Docker Compose com Jaeger, Redis, 3 OpenSERP, proxy rotation + circuit breaker |
| 🟡 Arquitetura | `LeadService` extraído em `OpenSerpEnricher`, `DomainEnricher`, `LeadDeletionService`, `RedisCacheService` |
| 🟡 JPA | `@Fetch(FetchMode.SUBSELECT)` para N+1, `@Version` para lock otimista, `@BatchSize` |
| 🔵 Manutenibilidade | `@ConfigurationPropertiesScan`, `@EnableCaching`, `LeadResponseSummary` (listagem leve) |
| 📚 Documentação | 10 ADRs, diagramas Mermaid atualizados, guias revisados |

## Quick Start

```bash
# 1. Configure as variáveis de ambiente
cp .env.example .env
# Edite .env com suas credenciais reais

# 2. Execute com Docker Compose
docker compose up --build

# 3. Acesse a API
curl -H "X-API-KEY: $(grep API_KEY .env | cut -d= -f2)" \
  -H "Content-Type: application/json" \
  -X POST http://localhost:${PORT:-8081}/api/v1/leads/enrich \
  -d '{"email":"contato@exemplo.com","name":"João Silva"}'

# 4. Swagger UI
# Abra http://localhost:${PORT:-8081}/swagger-ui.html
```

## Stack Principal

```mermaid
mindmap
  root((Lead Enrichment API))
    Java 21 (Virtual Threads)
    Spring Boot 3.3
      Web REST
      JPA / Hibernate
      Actuator
      Validation
    PostgreSQL 16
    Redis (Cache L2)
    Serviços
      LeadService - orquestrador
      OpenSerpEnricher - 6 buscas
      DomainEnricher - merge seguro
      RedisCacheService - L2
      DnsValidation - 5 tipos DNS
      TechScraper - 90+ assinaturas
      SocialDiscovery
      RdapService
      OpenSerpSearch - L1+L2 cache
      EncryptionService - AES-GCM
    Otimizações
      Cache Caffeine (L1)
      Cache Redis (L2)
      ContentTracker - hash SHA-256
      HTTP Connection Pooling
      Cópia defensiva (Gson)
      Compressão Gzip
      LeadResponseSummary
    Segurança
      AES-128-GCM
      SHA-256 hash
      API Key (X-API-KEY)
      Hard Delete LGPD
    Infra
      Docker Compose
      PostgreSQL
      Redis
      Jaeger (tracing)
      3x OpenSERP
      Proxy rotation
      Circuit breaker
```

## Licença

Copyright © 2026 pdroti.solutions
