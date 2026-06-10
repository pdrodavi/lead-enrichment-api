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
        CTRL --> LS[LeadService]
        LS --> DNS[DnsValidationService]
        LS --> TSS[TechScraperService]
        LS --> SDS[SocialDiscoveryService]
        LS --> RS[RdapService]
        LS --> OSS[OpenSerpSearch]
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
        OSS -->|"OkHttp"| OS[OpenSERP Self-hosted]
    end

    subgraph "Criptografia"
        PG --> EEC[EncryptedEmailConverter]
        EEC --> ES[EncryptionService<br/>AES-128-GCM]
    end
```

## Diagrama de Componentes

```mermaid
graph LR
    subgraph "Config"
        AKF[ApiKeyFilter]
        OAC[OpenApiConfig]
        GHE[GlobalExceptionHandler]
        EEC[EncryptedEmailConverter]
        ES[EncryptionService]
    end

    subgraph "Controller"
        LC[LeadController]
    end

    subgraph "Services"
        LS[LeadService]
        DVS[DnsValidationService]
        TSS[TechScraperService]
        SDS[SocialDiscoveryService]
        RS[RdapService]
        OSS[OpenSerpSearch]
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
        DR[DnsResult]
        RD[RdapData]
        SPD[SocialProfileData]
        SPD2[ScrapedPageData]
        SSR[SerpSearchResult]
        SRI[SerpResultItem]
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
```

## Fluxo de Enriquecimento

```mermaid
sequenceDiagram
    participant C as Cliente
    participant AK as ApiKeyFilter
    participant LC as LeadController
    participant LS as LeadService
    participant DNS as DnsValidationService
    participant TSS as TechScraperService
    participant SDS as SocialDiscoveryService
    participant RS as RdapService
    participant OSS as OpenSerpSearch
    participant DB as PostgreSQL

    C->>AK: POST /api/v1/leads/enrich<br/>X-API-KEY + JSON
    AK->>AK: Valida API Key
    AK->>LC: Encaminha requisição

    LC->>LS: enrich(email, domain, name)

    LS->>LS: Extrai domínio do e-mail (se não informado)
    LS->>LS: Gera hash SHA-256 do e-mail
    LS->>DB: findByEmailHash(hash)
    DB-->>LS: Lead existente ou null

    alt Domínio válido
        LS->>DNS: lookupDomain(domain)
        DNS-->>LS: DnsResult (MX, A, AAAA, CNAME, TXT)

        LS->>TSS: scrapeAndDetect(domain)
        TSS-->>LS: List<tech>, List<exposedEmails>,<br/>List<nameMentions>, List<allUrls>

        LS->>SDS: discoverSocialLinks(domain)
        SDS-->>LS: List<socialLinks>

        LS->>SDS: scrapeSocialProfiles(socialLinks)
        SDS-->>LS: List<SocialProfileData>

        LS->>RS: lookup(domain)
        RS-->>LS: RdapData

        LS->>OSS: searchPerson(name, limit)
        OSS-->>LS: JsonArray (resultados Google)
    else Sem domínio
        LS->>OSS: searchPerson(name, limit)
        OSS-->>LS: JsonArray (resultados Google)
    end

    LS->>DB: save(lead)
    DB-->>LS: Lead persistido

    LS-->>LC: Lead enriquecido
    LC-->>C: 200 + List<LeadResponse>
```

## Stack Tecnológica

| Tecnologia | Versão | Função |
|---|---|---|
| **Java** | 17 | Runtime |
| **Spring Boot** | 3.3.13 | Framework web, JPA, Actuator |
| **Spring Data JPA** | 3.3.x | ORM + Hibernate 6.x |
| **PostgreSQL** | 16 | Banco de dados relacional |
| **dnsjava** | 3.6.0 | Consultas DNS (MX, A, AAAA, CNAME, TXT) |
| **Jsoup** | 1.17.2 | Scraping HTML |
| **OkHttp** | 4.12.0 | Cliente HTTP para OpenSERP |
| **Gson** | 2.11.0 | Parse de JSON |
| **SpringDoc OpenAPI** | 2.5.0 | Swagger UI |
| **Lombok** | - | Redução de boilerplate |
| **Docker** | - | Containerização |
| **OpenSERP** | latest | Google Search API self-hosted |

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
