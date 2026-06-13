# Arquitetura do Sistema

## Visão Geral

A **Lead Enrichment API** é uma aplicação Spring Boot que enriquece dados de leads a partir de informações públicas disponíveis na internet. Utiliza uma arquitetura de microsserviço monolítico com componentes bem separados por responsabilidade.

```mermaid
graph TB
    subgraph "Camada de Apresentação"
        CLIENT[Cliente HTTP] -->|"X-API-KEY"| API[API REST /api/v1/leads]
    end

    subgraph "Camada de Segurança"
        API --> AKF[ApiKeyFilter]
        AKF -->|"401 se inválida"| ERR[Erro]
        AKF -->|"OK"| CTRL[LeadController]
    end

    subgraph "Camada de Serviços"
        CTRL --> LS[LeadService<br/>Orquestrador]
        LS --> OSE[OpenSerpEnricher]
        LS --> DE[DomainEnricher]
        LS --> LDS[LeadDeletionService]
        OSE --> OSS[OpenSerpSearch]
        OSE --> SDS[SocialDiscoveryService]
        DE --> DNS[DnsValidationService]
        DE --> TSS[TechScraperService]
        DE --> SDS[SocialDiscoveryService]
        DE --> RS[RdapService]
    end

    subgraph "Camada de Persistência"
        LS --> LR[LeadRepository]
        LR --> PG[(PostgreSQL 16)]
    end

    subgraph "Serviços Externos"
        DNS -->|"dnsjava"| NS[Nameservers Públicos]
        TSS -->|"Jsoup"| WEB[Site do Domínio]
        SDS -->|"Jsoup"| SOCIAL[Redes Sociais]
        RS -->|"HTTP"| RDAP[Identity Digital / Registro.br]
        OSS -->|"RestTemplate"| OS[OpenSERP Self-hosted]
    end

    subgraph "Criptografia"
        PG --> EEC[EncryptedEmailConverter]
        EEC --> ES[EncryptionService<br/>AES-128-GCM<br/>packages: service/]
    end
```

## Stack Tecnológica Atualizada

| Tecnologia | Versão | Função | Gerenciamento |
|---|---|---|---|
| **Java** | **21** | Runtime (Virtual Threads) | — |
| **Spring Boot** | 3.3.13 | Framework web, JPA, Actuator | Spring |
| **Spring Data JPA** | 3.3.x | ORM + Hibernate 6.x | Spring |
| **PostgreSQL** | 16 | Banco de dados relacional | Docker |
| **dnsjava** | 3.6.0 | Consultas DNS (MX, A, AAAA, CNAME, TXT) | Maven |
| **Jsoup** | 1.17.2 | Scraping HTML | Maven |
| **RestTemplate** | (Spring) | Cliente HTTP (OpenSERP) | Spring Bean (AppConfig) |
| **Gson** | 2.11.0 | Parse de JSON (OpenSERP) | Maven |
| **SpringDoc OpenAPI** | 2.5.0 | Swagger UI | Maven |
| **Lombok** | - | Redução de boilerplate | Maven |

> **Migrações relevantes:** OkHttp 4.12 foi removido — `RestTemplate` com connection pooling (Apache HttpClient 5) substitui o cliente HTTP manual. Migração Java 17 → 21 com Virtual Threads. Configurações de tecnologia, redes sociais e proxies OpenSERP externalizadas via `@ConfigurationProperties`.
>
> **Otimização de performance:** OpenSERP (6 frentes) + DomainEnricher (4 serviços) executam **em paralelo** via `CompletableFuture.allOf()` com pool de Virtual Threads. Consultas DNS paralelas (5 tipos simultâneos). Cache Caffeine (TTL 1h) para DNS, tecnologias e links sociais. HTTP Connection Pooling (200 conexões). Compressão Gzip. Paginação com `Page`/`Pageable`.

## Diagrama de Componentes

```mermaid
graph LR
    subgraph "Config"
        AKF[ApiKeyFilter]
        OAC[OpenApiConfig]
        GHE[GlobalExceptionHandler]
        EEC[EncryptedEmailConverter]
        APP[AppConfig<br/>RestTemplate Bean]
    end

    subgraph "Properties"
        TCP[TechScraperProperties<br/>signatures, scriptDetectors, metaGenerators]
        SDP[SocialDiscoveryProperties<br/>socialDomains, platformNames]
    end

    subgraph "Controller"
        LC[LeadController]
    end

    subgraph "Services"
        LS[LeadService]
        OSE[OpenSerpEnricher]
        DE[DomainEnricher]
        LDS[LeadDeletionService]
        DVS[DnsValidationService]
        TSS[TechScraperService]
        SDS[SocialDiscoveryService]
        RS[RdapService]
        OSS[OpenSerpSearch]
        ES[EncryptionService]
    end

    subgraph "Repository"
        LREPO[LeadRepository]
    end

    subgraph "Model"
        L[Lead]
    end

    subgraph "DTOs"
        LRQ[LeadRequest]
        LRSP[LeadResponse]
        DNSREC[DnsRecords<br/>sub-record]
        DISCV[DiscoveryData<br/>sub-record]
        RD[RdapData]
        SPD[SocialProfileData]
        SCRDATA[ScrapedPageData]
        SSR[SerpSearchResult]
        SRI[SerpResultItem]
        DR[DnsResult]
        SCR[ScrapeResult<br/>inner record]
    end

    LC --> LS
    LS --> DVS
    LS --> TSS
    LS --> SDS
    LS --> RS
    LS --> OSS
    LS --> LREPO
    LREPO --> L
    LC --> LRQ
    LC --> LRSP
    LRSP --> DNSREC
    LRSP --> DISCV
    LRSP --> RD
    TSS --> TCP
    SDS --> SDP
    OSS --> APP
```

## Fluxo de Enriquecimento (otimizado)

```mermaid
sequenceDiagram
    participant C as Cliente
    participant AK as ApiKeyFilter
    participant LC as LeadController
    participant LS as LeadService
    participant TSS as TechScraperService
    participant DNS as DnsValidation
    participant SDS as SocialDiscovery
    participant RS as RdapService
    participant OSS as OpenSerpSearch
    participant DB as PostgreSQL

    C->>AK: POST /api/v1/leads/enrich
    AK->>AK: Valida X-API-KEY
    alt Chave inválida
        AK-->>C: 401 Unauthorized
    end
    AK->>LC: Encaminha requisição
    LC->>LC: @Valid — valida campos
    alt Dados inválidos
        LC-->>C: 400 Bad Request
    end
    LC->>LS: enrich(email, domain, name)
    LS->>LS: Extrai domínio do e-mail
    LS->>LS: Gera hash SHA-256(email)
    LS->>DB: findByEmailHash(hash)
    DB-->>LS: Lead existente (ou null)

    alt Domínio válido
        LS->>DNS: lookupDomain(domain)
        DNS-->>LS: DnsResult (MX, A, AAAA, CNAME, TXT)

        Note over LS,TSS: ⚡ UMA requisição HTTP combinada
        LS->>TSS: scrapeTechnologiesAndCheckName(domain, name)
        TSS-->>LS: ScrapeResult(technologies, nameMentions)

        LS->>SDS: discoverSocialLinks(domain)
        SDS-->>LS: socialLinks
        LS->>SDS: scrapeSocialProfiles(socialLinks)
        SDS-->>LS: SocialProfileData

        LS->>RS: lookup(domain)
        RS-->>LS: RdapData

        LS->>OSS: searchPerson(name, 30)
        OSS-->>LS: JsonArray (via RestTemplate)
    else Sem domínio
        LS->>OSS: searchPerson(name, 30)
        OSS-->>LS: JsonArray
    end

    LS->>DB: save(lead) — AES-GCM
    DB-->>LS: Lead persistido

    LS-->>LC: Lead enriquecido
    LC-->>C: 200 OK + List~LeadResponse~
```

## Novos Componentes (pós-refatoração)

| Componente | Tipo | Função |
|---|---|---|
| `AppConfig` | `@Configuration` | Bean de `RestTemplate` com timeouts (5s connect, 20s read) |
| `TechScraperProperties` | `@ConfigurationProperties` | ~115 assinaturas tecnológicas carregadas do `application.yml` |
| `SocialDiscoveryProperties` | `@ConfigurationProperties` | 31 domínios sociais + 30 nomes de plataforma do `application.yml` |
| `DnsRecords` | `record` | Sub-record que agrupa mxStatus + 5 listas DNS |
| `DiscoveryData` | `record` | Sub-record com tecnologias, redes sociais, menções, OpenSERP |
| `ScrapeResult` | `record` | Resultado combinado de scraping + verificação de nome (1 HTTP) |

## Estrutura do Projeto

```
lead-enrichment-api/
├── Dockerfile                    # Imagem Docker multi-stage
├── docker-compose.yml            # Orquestração (PostgreSQL + OpenSERP + API)
├── pom.xml                       # Dependências Maven
├── README.md                     # Documentação principal
├── docs/
│   ├── adr/                      # Architecture Decision Records
│   │   ├── ADR-001-cache-com-redis.md
│   │   ├── ADR-002-lgpd-soft-delete.md
│   │   └── ADR-003-criptografia-pii.md
│   ├── architecture.md           # Este documento
│   ├── api-guide.md              # Guia da API
│   ├── deployment.md             # Guia de deploy
│   └── security-lgpd.md          # Segurança e LGPD
└── src/
    └── main/
        ├── java/solutions/pdroti/lead/enrichment/api/
        │   ├── LeadEnrichmentApplication.java
        │   ├── config/
        │   │   ├── ApiKeyFilter.java
        │   │   ├── EncryptedEmailConverter.java
        │   │   ├── EncryptionService.java
        │   │   ├── GlobalExceptionHandler.java
        │   │   └── OpenApiConfig.java
        │   ├── controller/
        │   │   └── LeadController.java
        │   ├── dto/
        │   │   ├── DnsResult.java
        │   │   ├── LeadRequest.java
        │   │   ├── LeadResponse.java
        │   │   ├── RdapData.java
        │   │   ├── ScrapedPageData.java
        │   │   ├── SerpResultItem.java
        │   │   ├── SerpSearchResult.java
        │   │   └── SocialProfileData.java
        │   ├── model/
        │   │   └── Lead.java
        │   ├── repository/
        │   │   └── LeadRepository.java
        │   ├── service/
        │   │   ├── DnsValidationService.java
        │   │   ├── LeadService.java
        │   │   ├── OpenSerpSearch.java
        │   │   ├── RdapService.java
        │   │   ├── SocialDiscoveryService.java
        │   │   └── TechScraperService.java
        │   └── util/
        │       └── EmailUtils.java
        └── resources/
            └── application.yml
```

## Decisões de Arquitetura (ADRs)

| ADR | Título | Decisão |
|---|---|---|
| [ADR-001](./adr/ADR-001-cache-com-redis.md) | Cache-Aside com Redis | Cache distribuído com Redis para reduzir latência |
| [ADR-002](./adr/ADR-002-lgpd-soft-delete.md) | Soft Delete para LGPD | Exclusão lógica com retenção de 365 dias |
| [ADR-003](./adr/ADR-003-criptografia-pii.md) | Criptografia de PII | AES-128-GCM para e-mails em repouso |
