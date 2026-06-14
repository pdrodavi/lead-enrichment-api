# Fluxo de Processamento da API — Lead Enrichment API

## Fluxograma de Requisição/Resposta

```mermaid
flowchart TD
    START(["INICIO"]) --> FILTER{"ApiKeyFilter shouldNotFilter?"}
    
    FILTER -->|"Path: /actuator, /swagger-ui"| BYPASS["Pula autenticacao"]
    FILTER -->|"Path: /api/v1/leads"| CHECK_KEY{"X-API-KEY presente e valida?"}

    CHECK_KEY -->|"Nao"| RESP_401(["401 Unauthorized"])
    CHECK_KEY -->|"Sim"| ROUTE["Router: LeadController"]

    BYPASS --> ROUTE

    ROUTE --> METHOD{"Rota"}

    METHOD -->|"POST /enrich"| ENRICH["enrichLead"]
    METHOD -->|"GET /"| LIST["listAll"]
    METHOD -->|"GET /{id}"| GET_BY_ID["getLeadById"]
    METHOD -->|"GET /domain/{domain}"| GET_BY_DOMAIN["getLeadsByDomain"]
    METHOD -->|"PUT /{id}"| UPDATE["updateLead"]
    METHOD -->|"DELETE /{id}"| DELETE["deleteLead"]

    ENRICH --> VALIDATE{"@Valid LeadRequest"}
    VALIDATE -->|"Invalido"| RESP_400(["400 Bad Request"])
    VALIDATE -->|"Valido"| CALL_SERVICE

    LIST --> CALL_SERVICE_LIST
    GET_BY_ID --> CALL_SERVICE_FIND
    GET_BY_DOMAIN --> CALL_SERVICE_DOMAIN
    UPDATE --> VALIDATE_UPDATE{"@Valid LeadRequest"}
    VALIDATE_UPDATE -->|"Invalido"| RESP_400
    VALIDATE_UPDATE -->|"Valido"| CALL_SERVICE_UPDATE
    DELETE --> CALL_SERVICE_DELETE

    subgraph "LeadService - Orquestração"
        CALL_SERVICE["enrich(email, domain, name)"]
        CALL_SERVICE_LIST["listAll<br/>→ LeadResponseSummary (leve)"]
        CALL_SERVICE_FIND["findById(id)"]
        CALL_SERVICE_DOMAIN["findByDomain(domain)"]
        CALL_SERVICE_UPDATE["update(id, email, domain, name)<br/>→ evict cache old email"]
        CALL_SERVICE_DELETE["hardDelete(id)"]

        CALL_SERVICE --> RESET_ENRICH["domainEnricher.resetEnrichmentData()"]
        RESET_ENRICH --> PARALLEL_BLOCK

        PARALLEL_BLOCK --> OPENSERP_ENRICH["openSerpEnricher.enrich(lead, name)"]
        PARALLEL_BLOCK --> HAS_DOMAIN{"Domínio presente<br/>e não pessoal?"}

        OPENSERP_ENRICH --> OPENSERP_FLOW
        subgraph OPENSERP_FLOW["OpenSerpEnricher (sempre)"]
            direction TB
            S1["6 buscas paralelas (cache L1+L2)"] --> S2["SerpProcessingContext"]
            S2 --> S3["Merge seguro com Domain<br/>(LinkedHashSet)"]
        end

        HAS_DOMAIN -->|"Sim"| FULL_FLOW
        HAS_DOMAIN -->|"Não (pessoal)"| MERGE_DONE

        subgraph FULL_FLOW["DomainEnricher.enrich()"]
            D_CALL["DnsValidationService lookupDomain<br/>5 tipos DNS paralelos"]
            T_CALL["TechScraperService<br/>scrapeTechnologiesAndCheckName<br/>+ cache Caffeine 1h"]
            S_CALL["SocialDiscoveryService discoverSocialLinks<br/>+ cache Caffeine 1h"]
            R_CALL["RdapService lookup<br/>+ cache Caffeine 1h"]
        end

        FULL_FLOW --> MERGE_DONE
        OPENSERP_FLOW --> MERGE_DONE

        MERGE_DONE["🔀 Merge automático<br/>socialLinks + nameMentions + discoveredUrls<br/>exposedEmails + foundDocuments"]
        MERGE_DONE --> SAVE["save/update no PostgreSQL<br/>TransactionTemplate curto"]

        CALL_SERVICE_LIST --> DB_LIST["findByStatus ACTIVE<br/>+ @Fetch(SUBSELECT)"]
        CALL_SERVICE_FIND --> DB_FIND["findById + status != DELETED"]
        CALL_SERVICE_DOMAIN --> DB_DOMAIN["findByDomainAndStatus"]
        CALL_SERVICE_UPDATE --> DB_UPDATE["findById + save"]
        CALL_SERVICE_DELETE --> DB_DELETE["deleteById (hard delete — 1 query)"]
    end

    SAVE --> CHECK_CACHE{"@Cacheable<br/>enrich-result?"}
    CHECK_CACHE -->|"Cache miss"| FORMAT_RESP
    CHECK_CACHE -->|"Cache hit"| CACHED["Retornar resposta cacheada<br/>(Caffeine 24h)"]
    CACHED --> RESP_200

    FORMAT_RESP["Converter Lead para LeadResponse"]

    FORMAT_RESP --> DOMAIN_LEADS{"Buscar todos leads do dominio?"}
    DOMAIN_LEADS -->|"Encontrados"| ALL["Retornar lista completa"]
    DOMAIN_LEADS -->|"Vazios"| SINGLE["Retornar apenas o lead"]

    ALL --> RESP_200(["200 OK"])
    SINGLE --> RESP_200
    
    DB_LIST --> RESP_200_LIST(["200 OK"])
    DB_FIND -->|"Encontrado"| RESP_200_ONE(["200 OK"])
    DB_FIND -->|"Nao encontrado"| RESP_404(["404 Not Found"])
    DB_DOMAIN -->|"Encontrados"| RESP_200_DOMAIN(["200 OK"])
    DB_DOMAIN -->|"Vazios"| RESP_204(["204 No Content"])
    DB_UPDATE --> RESP_200_UPD(["200 OK"])
    DB_DELETE -->|"Sucesso"| RESP_200_DEL(["200 OK"])
    DB_DELETE -->|"Nao encontrado"| RESP_404_DEL(["404 Not Found"])

    RESP_200 --> END(["FIM"])
    RESP_200_LIST --> END
    RESP_200_ONE --> END
    RESP_200_DOMAIN --> END
    RESP_200_UPD --> END
    RESP_200_DEL --> END
    RESP_204 --> END
    RESP_400 --> END
    RESP_401 --> END
    RESP_404 --> END
    RESP_404_DEL --> END

    style FULL_FLOW fill:#ebfbee,stroke:#2f9e44,stroke-width:2px
```

> Desde a refatoração, o `LeadService` passou a ser um orquestrador puro (~140 linhas) que delega para `OpenSerpEnricher` (busca Google), `DomainEnricher` (DNS + RDAP + scraping + sociais) e `LeadDeletionService` (hard delete em 1 query). O fluxo completo de tecnologias + verificação de nome é feito em **uma única requisição HTTP** via `scrapeTechnologiesAndCheckName()`. O `OpenSerpSearch` usa `RestTemplate` (gerenciado pelo Spring) em vez de `OkHttpClient` manual.

## Diagrama de Contexto da API

```mermaid
graph LR
    subgraph "Cliente Externo"
        CLI["Cliente HTTP<br/>curl / Postman / App"]
    end

    subgraph "Lead Enrichment API"
        direction TB
        GW["ApiKeyFilter<br/>(Portão de Segurança)"]
        API["REST API<br/>:8081"]
        SVC["Camada de Serviços"]
        DB[("PostgreSQL<br/>:5433")]
    end

    subgraph "Infraestrutura"
        OS["OpenSERP<br/>:7000"]
        PG["PostgreSQL Server<br/>16"]
    end

    CLI -->|"X-API-KEY + JSON"| GW
    GW -->|"Autorizado"| API
    API -->|"GET/POST/PUT/DELETE"| SVC
    SVC -->|"CRUD"| DB
    SVC -->|"HTTP /google/search"| OS
    SVC -->|"DNS lookup"| DNS_NS["Nameservers<br/>Públicos"]
    SVC -->|"HTTP scraping"| WEB["Sites Externos"]
    SVC -->|"RDAP query"| RDAP["Identity Digital<br/>Registro.br"]

    DB --> PG

    style CLI fill:#e7f5ff,stroke:#1971c2
    style GW fill:#fff5f5,stroke:#e03131
    style API fill:#e7f5ff,stroke:#1971c2
    style SVC fill:#ebfbee,stroke:#2f9e44
```

## Diagrama de Estados do Endpoint DELETE (Hard Delete)

```mermaid
stateDiagram-v2
    [*] --> ACTIVE : Lead criado/enriquecido

    ACTIVE --> ENRICHED : Reenriquecimento
    ENRICHED --> ACTIVE : Dados atualizados

    ACTIVE --> DELETED : DELETE /api/v1/leads/{id}
    ENRICHED --> DELETED : DELETE /api/v1/leads/{id}

    DELETED --> [*] : Registro removido fisicamente<br/>deleteById (1 query)

    note right of DELETED
        Hard delete via
        LeadDeletionService
        deleteById + try-catch
        EmptyResultDataAccessException
        Log: "Lead hard deleted: ID=X"
    end note

    note right of ACTIVE
        Status: "ACTIVE"
        Retornado em:
        GET /leads
        GET /leads/{id}
        GET /leads/domain/{domain}
    end note
```

## Mapa de Endpoints

```mermaid
flowchart LR
    subgraph "API: /api/v1/leads"
        POST_ENRICH["POST /enrich"]
        GET_ALL["GET /"]
        GET_ID["GET /{id}"]
        GET_DOMAIN["GET /domain/{domain}"]
        PUT_ID["PUT /{id}"]
        DELETE_ID["DELETE /{id}"]
    end

    subgraph "Actuator: /actuator"
        HEALTH["GET /health"]
        LIVENESS["GET /health/liveness"]
        READINESS["GET /health/readiness"]
        METRICS["GET /metrics"]
    end

    subgraph "Swagger: documentacao"
        SWAGGER["/swagger-ui/index.html"]
        API_DOCS["/v3/api-docs"]
    end

    POST_ENRICH -->|"Autenticado + @Valid"| ENRICH_FLOW["200 + List<LeadResponse>"]
    GET_ALL -->|"Autenticado"| LIST_FLOW["200 + List<LeadResponse>"]
    GET_ID -->|"Autenticado"| FIND_FLOW["200 ou 404"]
    GET_DOMAIN -->|"Autenticado"| DOMAIN_FLOW["200 ou 204"]
    PUT_ID -->|"Autenticado + @Valid"| UPDATE_FLOW["200 ou 404"]
    DELETE_ID -->|"Autenticado"| DELETE_FLOW["200 + LGPD ou 404"]

    HEALTH -->|"Publico"| OK_HEALTH["200 + status UP"]
    LIVENESS --> OK_HEALTH
    READINESS --> OK_HEALTH
    METRICS -->|"Publico"| OK_METRICS["Metricas JVM + HTTP"]
    SWAGGER -->|"Publico"| OK_SWAGGER["UI Interativa"]
    API_DOCS -->|"Publico"| OK_DOCS["JSON OpenAPI"]
```
