# Diagrama de Componentes e Sequência — Lead Enrichment API

## Diagrama de Componentes (Atualizado)

```mermaid
graph TB
    subgraph "Camada de Apresentação"
        LC["«Controller» LeadController"]
        OAC["OpenApiConfig Swagger"]
        GEH["GlobalExceptionHandler"]
    end

    subgraph "Camada de Segurança"
        AKF["«SecurityFilter» ApiKeyFilter<br/>Valida X-API-KEY"]
        TF["TracingFilter<br/>OpenTelemetry<br/>Request/Response body capture"]
    end

    subgraph "Camada de Configuração"
        APP["AppConfig<br/>RestTemplate, Caffeine caches<br/>CacheManager (@Cacheable)<br/>Virtual Thread Executor"]
        RCFG["RedisConfig<br/>@ConditionalOnProperty<br/>LettuceConnectionFactory"]
        TCP["TechScraperProperties<br/>(assinaturas YAML)"]
        SDP["SocialDiscoveryProperties<br/>(domínios + plataformas)"]
        OSP["OpenSerpProxyProperties<br/>(endpoints + proxy rotation)"]
    end

    subgraph "Camada de Serviços"
        LS["«Orquestrador» LeadService<br/>⚡ CompletableFuture.allOf<br/>⏱ orTimeout 2min"]
        OSE["OpenSerpEnricher<br/>6 buscas paralelas<br/>Merge seguro com Domain"]
        DE["DomainEnricher<br/>DNS, RDAP, scraping<br/>executeSafely pattern"]
        LDS["LeadDeletionService<br/>Hard delete (1 query)"]
        DNS["DnsValidationService<br/>dnsjava — 5 tipos DNS<br/>Cache Caffeine 1h"]
        TSS["TechScraperService<br/>Jsoup — 90+ assinaturas<br/>Cache Caffeine 1h"]
        SDS["SocialDiscoveryService<br/>Jsoup — 31 plataformas<br/>2 caches Caffeine"]
        RS["RdapService<br/>Identity Digital + Registro.br<br/>Cache Caffeine 1h"]
        OSS["OpenSerpSearch<br/>Cache L1 Caffeine + L2 Redis<br/>Circuit breaker + rate limit"]
        RCS["RedisCacheService<br/>L2 distribuído<br/>Async set + fallback"]
        ES["EncryptionService<br/>AES-128-GCM"]
    end

    subgraph "Camada Utilitária"
        EU["EmailUtils<br/>SHA-256 + Mascaramento LGPD<br/>ThreadLocal MessageDigest"]
        DP["DataParser<br/>COMMON_EMAIL_PROVIDERS<br/>nameMatchesExactly"]
        CT["ContentTracker<br/>Hash SHA-256 para<br/>detecção de mudanças"]
    end

    subgraph "Camada de Persistência"
        LREPO["LeadRepository<br/>Spring Data JPA"]
        EEC["EncryptedEmailConverter<br/>AES-128-GCM"]
        DB[("PostgreSQL 16<br/>@Fetch(SUBSELECT)<br/>@Version otimista<br/>@BatchSize(10)")]
    end

    subgraph "Cache Distribuído"
        REDIS[("Redis<br/>Cache L2")]
    end

    subgraph "Serviços Externos"
        NS["Nameservers Públicos"]
        WEB["Site do Domínio"]
        SOCIAL["Redes Sociais"]
        RDAP_API["Identity Digital<br/>+ Registro.br"]
        OPENSERP["OpenSERP Self-hosted<br/>3 instâncias"]
    end

    AKF -->|401| GEH
    AKF --> LC
    LC --> LS

    LS --> OSE
    LS --> DE
    LS --> LDS
    LS --> DP
    LS --> EU

    OSE --> OSS
    OSE --> SDS
    OSS --> RCS
    DE --> DNS
    DE --> TSS
    DE --> SDS
    DE --> RS

    OSS --> OSP
    RCS --> REDIS

    LS --> LREPO
    LREPO --> EEC
    EEC --> DB

    DNS --> NS
    TSS --> WEB
    SDS --> SOCIAL
    RS --> RDAP_API
    OSS --> OPENSERP

    classDef controller fill:#e7f5ff,stroke:#1971c2,stroke-width:2px
    classDef security fill:#fff5f5,stroke:#e03131,stroke-width:2px
    classDef service fill:#ebfbee,stroke:#2f9e44,stroke-width:2px
    classDef persistence fill:#fff4e6,stroke:#e8590c,stroke-width:2px
    classDef external fill:#f3f0ff,stroke:#6741d9,stroke-width:2px
    classDef cache fill:#fff3bf,stroke:#f08c00,stroke-width:2px

    class LC,OAC,GEH controller
    class AKF,TF security
    class APP,RCFG,TCP,SDP,OSP config
    class LS,OSE,DE,LDS,DNS,TSS,SDS,RS,OSS,RCS,ES service
    class EU,DP,CT service
    class LREPO,EEC,DB persistence
    class REDIS cache
    class NS,WEB,SOCIAL,RDAP_API,OPENSERP external
```

## Diagrama de Classes (Modelo de Domínio — Atualizado)

```mermaid
classDiagram
    class Lead {
        +Long id
        +String email (AES-GCM)
        +String emailHash (SHA-256, unique)
        +String name
        +String domain
        +boolean mxStatus
        +String status
        +List~String~ dnsMxRecords
        +List~String~ dnsARecords
        +List~String~ dnsAaaaRecords
        +List~String~ dnsCnameRecords
        +List~String~ dnsTxtRecords
        +List~String~ technologies
        +List~String~ socialLinks
        +List~String~ socialProfileSummaries
        +List~String~ exposedEmails
        +int dorkFindings
        +List~String~ nameMentions
        +String rdapRawData
        +String rdapRegistrar
        +String rdapRegistrantName
        +String rdapRegistrantEmail
        +LocalDateTime rdapRegistrationDate
        +LocalDateTime rdapExpirationDate
        +List~String~ rdapNameservers
        +List~String~ rdapStatus
        +String rdapTaxpayerId
        +String rdapSource
        +String openSerpRawData
        +List~String~ foundDocuments
        +List~String~ discoveredUrls
        +Boolean consentGiven
        +LocalDateTime consentDate
        +LocalDateTime dataRetentionUntil
        +LocalDateTime createdAt
        +LocalDateTime updatedAt
        +LocalDateTime deletedAt
        +Long version (@Version)
    }

    class LeadRequest {
        +String email (obrigatório)
        +String domain (opcional)
        +String name (obrigatório, min 3)
    }

    class LeadResponse {
        +Long id
        +String emailMasked
        +String name
        +String domain
        +String status
        +DnsRecords dns
        +DiscoveryData discovery
        +RdapData rdap
        +fromEntity(Lead, ObjectMapper) LeadResponse
    }

    class LeadResponseSummary {
        +Long id
        +String emailMasked
        +String name
        +String domain
        +String status
        +boolean mxStatus
        +int dorkFindings
        +int technologiesCount
        +int socialLinksCount
        +int documentsCount
        +int mentionsCount
        +LocalDateTime createdAt
        +fromEntity(Lead) LeadResponseSummary
    }

    class DnsRecords {
        +boolean mxStatus
        +List~String~ mxRecords
        +List~String~ aRecords
        +List~String~ aaaaRecords
        +List~String~ cnameRecords
        +List~String~ txtRecords
    }

    class DiscoveryData {
        +List~String~ technologies
        +List~String~ socialLinks
        +List~String~ socialProfileSummaries
        +List~String~ exposedEmails
        +List~String~ nameMentions
        +List~String~ nameMentionUrls
        +int dorkFindings
        +List~String~ foundDocuments
        +List~String~ discoveredUrls
        +SerpSearchResult openSerpRawData
    }

    class DnsResult {
        +boolean hasMx
        +List~String~ mxRecords
        +List~String~ aRecords
        +List~String~ aaaaRecords
        +List~String~ cnameRecords
        +List~String~ txtRecords
        +empty() DnsResult
    }

    class RdapData {
        +JsonNode rawJson
        +String registrar
        +String registrantName
        +String registrantEmail
        +String registrationDate
        +String expirationDate
        +List~String~ nameservers
        +List~String~ status
        +String taxpayerId
        +String source
        +empty() RdapData
    }

    class SerpSearchResult {
        +String query
        +int totalResults
        +List~SerpResultItem~ items
        +empty(String query) SerpSearchResult
    }

    class SerpResultItem {
        +String title
        +String url
        +String snippet
        +String domain
    }

    class SocialProfileData {
        +String platform
        +String profileUrl
        +String title
        +String description
    }

    class LeadRepository {
        +findByEmailHash(String) Optional~Lead~
        +findByName(String) Optional~Lead~
        +findByStatus(String) List~Lead~
        +findByDomainAndStatus(String, String) List~Lead~
    }

    LeadRepository --> Lead : consulta
    LeadResponse --> Lead : fromEntity()
    LeadResponse --> DnsRecords : contém
    LeadResponse --> DiscoveryData : contém
    LeadResponse --> RdapData : contém
    LeadResponse --> SerpSearchResult : contém
    SerpSearchResult --> SerpResultItem : contém
```

## Diagrama de Sequência — Enriquecimento de Lead

```mermaid
sequenceDiagram
    participant C as Cliente
    participant AK as ApiKeyFilter
    participant LC as LeadController
    participant LS as LeadService
    participant DNS as DnsValidation
    participant TSS as TechScraper
    participant SDS as SocialDiscovery
    participant RS as RdapService
    participant OSS as OpenSerpSearch
    participant DB as PostgreSQL

    C->>AK: POST /api/v1/leads/enrich (X-API-KEY + JSON {email, name, domain?})
    
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

    LS->>LS: Extrai domínio do e-mail (se domain não informado)
    LS->>LS: Gera hash SHA-256(email)
    LS->>DB: findByEmailHash(hash)
    DB-->>LS: Lead existente (ou null)

    LS->>LS: Limpa dados de enrichment

    par Execucao Paralela (CompletableFuture.allOf)
        LS->>OSS: searchPerson(name, 15)
        OSS-->>LS: JsonArray (resultados Google)
        LS->>OSS: searchDocuments(name, 15)
        OSS-->>LS: JsonArray (documentos)
        LS->>LS: processResults + serializeResult
    and Dominio (se disponivel)
        alt Dominio valido
            LS->>DNS: lookupDomain(domain)
            DNS-->>LS: DnsResult
            LS->>TSS: scrapeTechnologiesAndCheckName(domain, name)
            TSS-->>LS: tecnologias, mencoes
            LS->>SDS: discoverSocialLinks(domain)
            SDS-->>LS: socialLinks
            LS->>RS: lookup(domain)
            RS-->>LS: RdapData
        else Sem dominio
            note over LS: Apenas OpenSERP executado
        end
    end
    LS-->>LC: Lead enriquecido
    LC-->>C: Resultado com lista de leads e email mascarado
```
