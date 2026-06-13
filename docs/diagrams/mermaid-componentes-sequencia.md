# Diagrama de Componentes e Sequência

## Diagrama de Componentes

```mermaid
graph TB
    subgraph "Camada de Apresentação"
        LC["«Controller» LeadController"]
        OAC["OpenApiConfig Swagger"]
        GEH["GlobalExceptionHandler"]
    end

    subgraph "Camada de Segurança"
        AKF["«SecurityFilter» ApiKeyFilter<br/>Valida X-API-KEY"]
    end

    subgraph "Camada de Configuração"
        APP["AppConfig<br/>RestTemplate Beans<br/>padrão: 5s/20s | OpenSERP: 10s/30s"]
        TCP["TechScraperProperties<br/>(assinaturas YAML)"]
        SDP["SocialDiscoveryProperties<br/>(domínios + plataformas)"]
    end

    subgraph "Camada de Serviços"
        LS["«Orquestrador» LeadService<br/>~140 linhas<br/>⚡ CompletableFuture.allOf"]
        OSE["OpenSerpEnricher<br/>⚡ fetchResults + fetchDocuments<br/>em paralelo (15 resultados)"]
        DE["DomainEnricher<br/>DNS, RDAP, scraping, redes sociais"]
        LDS["LeadDeletionService<br/>Hard delete (1 query) + soft delete"]
        DNS["DnsValidationService<br/>dnsjava — MX, A, AAAA, CNAME, TXT"]
        TSS["TechScraperService<br/>Jsoup — 1 chamada HTTP combinada"]
        SDS["SocialDiscoveryService<br/>Jsoup — 33 plataformas"]
        RS["RdapService<br/>Identity Digital + Registro.br"]
        OSS["OpenSerpSearch<br/>RestTemplate — Google Search API"]
    end

    subgraph "Camada Utilitária"
        EU["EmailUtils<br/>SHA-256 + Mascaramento LGPD"]
        DP["DataParser<br/>Parsers estáticos: data, email, nome"]
    end

    subgraph "Camada de Persistência"
        LREPO["LeadRepository<br/>Spring Data JPA"]
        EEC["EncryptedEmailConverter<br/>+ EncryptionService<br/>AES-128-GCM"]
        DB[("PostgreSQL 16<br/>Tabela: leads<br/>FetchType: LAZY")]
    end

    subgraph "Serviços Externos"
        NS["Nameservers Públicos"]
        WEB["Site do Domínio"]
        SOCIAL["Redes Sociais<br/>31 plataformas"]
        RDAP_API["Identity Digital API<br/>+ Registro.br RDAP"]
        OPENSERP["OpenSERP Self-hosted"]
    end

    %% Conexões
    AKF -->|401 se inválida| GEH
    AKF -->|passa requisição| LC
    LC -->|chama| LS

    LS --> OSE
    LS --> DE
    LS --> LDS
    LS --> DP
    LS --> EU

    OSE --> OSS
    OSE --> SDS
    DE --> DNS
    DE --> TSS
    DE --> SDS
    DE --> RS

    TSS --> TCP
    SDS --> SDP
    OSS --> APP

    LS -->|persiste/consulta| LREPO
    LREPO --> EEC
    EEC --> DB

    DNS -->|consulta| NS
    TSS -->|scraping| WEB
    SDS -->|scraping| SOCIAL
    RS -->|HTTP RDAP| RDAP_API
    OSS -->|HTTP RestTemplate| OPENSERP

    %% Estilo
    classDef controller fill:#e7f5ff,stroke:#1971c2,stroke-width:2px
    classDef security fill:#fff5f5,stroke:#e03131,stroke-width:2px
    classDef service fill:#ebfbee,stroke:#2f9e44,stroke-width:2px
    classDef persistence fill:#fff4e6,stroke:#e8590c,stroke-width:2px
    classDef external fill:#f3f0ff,stroke:#6741d9,stroke-width:2px

    class LC,OAC,GEH controller
    class AKF security
    class APP,TCP,SDP config
    class LS,OSE,DE,LDS,DNS,TSS,SDS,RS,OSS service
    class EU,DP service
    class LREPO,EEC,DB persistence
    class NS,WEB,SOCIAL,RDAP_API,OPENSERP external
```

## Diagrama de Classes (Modelo de Domínio)

```mermaid
classDiagram
    class Lead {
        +Long id
        +String email (criptografado AES-GCM)
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
        +String rdapRegistrationDate
        +String rdapExpirationDate
        +List~String~ rdapNameservers
        +List~String~ rdapStatus
        +String rdapTaxpayerId
        +String openSerpRawData
        +List~String~ foundDocuments
        +List~String~ discoveredUrls
        +Boolean consentGiven
        +LocalDateTime consentDate
        +LocalDateTime dataRetentionUntil
        +LocalDateTime createdAt
        +LocalDateTime updatedAt
        +LocalDateTime deletedAt
    }

    class LeadRequest {
        +String email (obrigatório)
        +String domain (opcional)
        +String name (obrigatório)
    }

    class LeadResponse {
        +Long id
        +String emailMasked
        +String name
        +String domain
        +String status
        +DnsRecords dns
        +DiscoveryData discovery
        +SerpSearchResult rdap
        +RdapData rdap
        +fromEntity(Lead, ObjectMapper) LeadResponse
        +~~fromEntity(Lead) LeadResponse (deprecated)
    }

    class DnsRecords {
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
