# Diagrama UML — Fluxo de Enriquecimento — Lead Enrichment API

## Diagrama de Estado do Lead

```mermaid
stateDiagram-v2
    [*] --> PENDING : Novo lead recebido
    PENDING --> ENRICHING : Iniciar enriquecimento

    ENRICHING --> PARALLEL : ⚡ Execução paralela
    PARALLEL --> DNS_CHECK : Domain: 1. Validar DNS
    PARALLEL --> OPENSERP_SEARCH : OpenSERP: 1. Buscar no Google (6 frentes)
    DNS_CHECK --> TECH_SCRAPE : Domain: 2. Detectar tecnologias
    TECH_SCRAPE --> SOCIAL_DISCOVERY : Domain: 3. Descobrir redes sociais
    SOCIAL_DISCOVERY --> RDAP_QUERY : Domain: 4. Consultar RDAP
    OPENSERP_SEARCH --> SERP_PROCESS : OpenSERP: 2. Processar resultados
    SERP_PROCESS --> MERGE : 🔀 Merge seguro (LinkedHashSet)
    TECH_SCRAPE --> MERGE
    RDAP_QUERY --> MERGE
    MERGE --> PERSISTING : 7. Persistir dados

    PERSISTING --> ENRICHED : Salvo com sucesso
    ENRICHED --> [*]

    ENRICHING --> ERROR : Falha em qualquer etapa
    ERROR --> [*]

    ENRICHED --> DELETED : DELETE /api/v1/leads/{id}
    DELETED --> [*] : Hard delete (registro removido fisicamente)
```

## Diagrama de Fluxo — Enriquecimento Completo

```mermaid
flowchart TD
    START(["INÍCIO"]) --> VALIDATE{"LeadRequest @Valid?"}
    VALIDATE -->|"Não"| BAD_REQUEST["400 Bad Request"]
    VALIDATE -->|"Sim"| EXTRACT_DOMAIN["Extrair domínio do e-mail"]
    
    EXTRACT_DOMAIN --> HASH_EMAIL["Gerar SHA-256 hash do e-mail"]
    HASH_EMAIL --> FIND_EXISTING{"Lead existe por hash?"}
    FIND_EXISTING -->|"Sim - reenriquecer"| DOMAIN_CHECK
    FIND_EXISTING -->|"Não - criar novo"| DOMAIN_CHECK

    DOMAIN_CHECK{"Domínio válido?"}
    
    DOMAIN_CHECK -->|"Sim"| FLUXO_COMPLETO
    DOMAIN_CHECK -->|"Não"| FLUXO_OPENSERP

    subgraph FLUXO_COMPLETO["Fluxo Completo (com domínio) — DomainEnricher"]
        direction TB
        A1["1. DNS Validation (dnsjava)"] --> A2["2. Tech Scraper + Name Check (Jsoup)<br/>⚡ UMA requisição HTTP"]
        A2 --> A3["3. Social Discovery (Jsoup)"]
        A3 --> A4["4. Social Scraping (Jsoup)"]
        A4 --> A5["5. RDAP Query (HTTP)"]
    end

    subgraph FLUXO_OPENSERP_ENRICH["OpenSERP — OpenSerpEnricher<br/>(sempre executado)"]
        direction TB
        C1["1. fetchResults — busca páginas web"] --> C2["2. fetchDocuments — busca PDFs"]
        C2 --> C3["3. processResults — filtra por nome<br/>extrai links, sociais, e-mails, menções"]
        C3 --> C4["4. serializeResult — armazena como JSON"]
    end

    FLUXO_COMPLETO -.-> MERGE
    FLUXO_OPENSERP_ENRICH --> MERGE

    subgraph FLUXO_OPENSERP["Fluxo Reduzido (sem domínio ou pessoal)"]
        direction TB
        B1["1. OpenSERP Search (6 buscas)"] --> B2["2. Filtrar por nome exato"]
        B2 --> B3["3. Extrair links, sociais, e-mails"]
        B3 --> B4["4. Resultado: SerpSearchResult"]
    end

    FLUXO_OPENSERP --> MERGE

    MERGE["🔀 Merge seguro (LinkedHashSet)<br/>socialLinks + nameMentions + exposedEmails + exposedPhones<br/>foundDocuments + discoveredUrls"]

    MERGE["Mesclar todos os dados coletados"] --> ENCRYPT["Criptografar e-mail (AES-128-GCM)"]
    ENCRYPT --> PERSIST["Persistir Lead no PostgreSQL<br/>FetchType: LAZY"]
    PERSIST --> FORMAT_RESP["Converter para LeadResponse<br/>ObjectMapper injetado (Spring)"]
    FORMAT_RESP --> MASK["Mascarar e-mail (LGPD)"]
    MASK --> MAPPING["Agrupar em sub-records<br/>DnsRecords + DiscoveryData + RdapData"]
    MAPPING --> SUCCESS(["FIM - 200 OK"])
    BAD_REQUEST --> FAIL(["FIM - 400"])
```

## Diagrama de Pacotes

```mermaid
graph TB
    subgraph "solutions.pdroti.lead.enrichment.api"
        direction TB
        subgraph "config"
            AKF[ApiKeyFilter]
            EEC[EncryptedEmailConverter]
            GEH[GlobalExceptionHandler]
            OAC[OpenApiConfig]
            APP[AppConfig]
            TCP[TechScraperProperties]
            SDP[SocialDiscoveryProperties]
        end
        subgraph "controller"
            LC[LeadController]
        end
        subgraph "dto"
            LRQ[LeadRequest]
            LRS[LeadResponse]
            DR[DnsResult]
            RD[RdapData]
            SSR[SerpSearchResult]
            SRI[SerpResultItem]
            SPD[SocialProfileData]
            SCR[ScrapedPageData]
            DNR[DnsRecords]
            DCD[DiscoveryData]
        end
        subgraph "model"
            L[Lead]
        end
        subgraph "repository"
            LREPO[LeadRepository]
        end
        subgraph "service"
            LS[LeadService]
            OSE[OpenSerpEnricher]
            DE[DomainEnricher]
            LDS[LeadDeletionService]
            ES[EncryptionService]
            DNS[DnsValidationService]
            TSS[TechScraperService]
            SDS[SocialDiscoveryService]
            RS[RdapService]
            OSS[OpenSerpSearch]
            DCS[DotComScrapingService]
            ESM[EnrichmentSnapshotManager]
            SERR[ScrapeError]
        end
        subgraph "util"
            EU[EmailUtils]
            DP[DataParser]
        end
    end
    
    LC --> LS
    LC --> LDS
    LS --> OSE
    LS --> DE
    LS --> LDS
    LS --> DP
    LS --> EU
    LS --> ESM
    OSE --> OSS
    OSE --> SDS
    DE --> DNS
    DE --> TSS
    DE --> SDS
    DE --> RS
    DE --> DCS
    LREPO --> L
    
    classDef package fill:#e7f5ff,stroke:#1971c2,stroke-width:1px
    class AKF,EEC,ES,GEH,OAC,APP,TCP,SDP,LC,LRQ,LRS,DR,RD,SSR,SRI,SPD,DNR,DCD,L,LREPO,LS,DNS,TSS,SDS,RS,OSS,SERR,EU package
```

## Diagrama de Atividades — Enriquecimento de Lead

```mermaid
flowchart LR
    subgraph "Entrada"
        REQ[Requisição HTTP<br/>POST /enrich]
    end

    subgraph "Processamento Síncrono"
        direction TB
        VAL[Validar entrada<br/>@Valid]
        AUTH[Autenticar<br/>X-API-KEY]
        ENR["Enriquecer<br/>Chamar 5+ serviços"]
        PERSIST["Persistir<br/>AES-GCM + PostgreSQL"]
    end

    subgraph "Serviços de Enriquecimento"
        direction TB
        S1[DNS<br/>dnsjava]
        S2[Scraping<br/>Jsoup]
        S3[Sociais<br/>Jsoup]
        S4[RDAP<br/>HTTP]
        S5[Google<br/>RestTemplate]
    end

    subgraph "Saída"
        RESP[Resposta HTTP<br/>200 + JSON]
    end

    REQ --> AUTH
    AUTH --> VAL
    VAL --> ENR
    
    ENR --> S1
    ENR --> S2
    ENR --> S3
    ENR --> S4
    ENR --> S5
    
    S1 --> PERSIST
    S2 --> PERSIST
    S3 --> PERSIST
    S4 --> PERSIST
    S5 --> PERSIST
    
    PERSIST --> RESP
```
