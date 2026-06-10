# Diagrama UML — Fluxo de Enriquecimento

## Diagrama de Estado do Lead

```mermaid
stateDiagram-v2
    [*] --> PENDING : Novo lead recebido
    PENDING --> ENRICHING : Iniciar enriquecimento

    ENRICHING --> DNS_CHECK : 1. Validar DNS
    DNS_CHECK --> TECH_SCRAPE : 2. Detectar tecnologias
    TECH_SCRAPE --> SOCIAL_DISCOVERY : 3. Descobrir redes sociais
    SOCIAL_DISCOVERY --> SOCIAL_SCRAPE : 4. Scraping de perfis sociais
    SOCIAL_SCRAPE --> RDAP_QUERY : 5. Consultar RDAP
    RDAP_QUERY --> OPENSERP_SEARCH : 6. Buscar no OpenSERP
    OPENSERP_SEARCH --> PERSISTING : 7. Persistir dados

    PERSISTING --> ENRICHED : Salvo com sucesso
    ENRICHED --> [*]

    ENRICHING --> ERROR : Falha em qualquer etapa
    ERROR --> [*]

    ENRICHED --> DELETED : DELETE /api/v1/leads/{id}
    DELETED --> [*] : Expurgo após 365 dias
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

    subgraph FLUXO_COMPLETO["Fluxo Completo (com domínio)"]
        direction TB
        A1["1. DNS Validation (dnsjava)"] --> A2["2. Tech Scraper + Name Check (Jsoup)<br/>⚡ UMA requisição HTTP"]
        A2 --> A3["3. Social Discovery (Jsoup)"]
        A3 --> A4["4. Social Scraping (Jsoup)"]
        A4 --> A5["5. RDAP Query (HTTP)"]
        A5 --> A6["6. OpenSERP Search (RestTemplate)"]
    end

    subgraph FLUXO_OPENSERP["Fluxo Reduzido (sem domínio)"]
        direction TB
        B1["1. OpenSERP Search (RestTemplate)"] --> B2["2. Extrair URLs dos resultados"]
        B2 --> B3["3. Classificar links sociais"]
        B3 --> B4["4. Extrair e-mails expostos"]
    end

    FLUXO_COMPLETO --> MERGE
    FLUXO_OPENSERP --> MERGE

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
            ES[EncryptionService]
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
            DNS[DnsValidationService]
            TSS[TechScraperService]
            SDS[SocialDiscoveryService]
            RS[RdapService]
            OSS[OpenSerpSearch]
        end
        subgraph "util"
            EU[EmailUtils]
        end
    end
    
    LC --> LS
    LS --> LREPO
    LS --> DNS
    LS --> TSS
    LS --> SDS
    LS --> RS
    LS --> OSS
    LS --> EU
    LREPO --> L
    
    classDef package fill:#e7f5ff,stroke:#1971c2,stroke-width:1px
    class AKF,EEC,ES,GEH,OAC,APP,TCP,SDP,LC,LRQ,LRS,DR,RD,SSR,SRI,SPD,SCR,DNR,DCD,L,LREPO,LS,DNS,TSS,SDS,RS,OSS,EU package
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
