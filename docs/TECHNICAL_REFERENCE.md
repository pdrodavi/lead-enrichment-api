# Referência Técnica — Lead Enrichment API

> **Versão:** 1.0.0  |  **Stack:** Java 21 + Spring Boot 3.3.13 + PostgreSQL 16  |  **Pacote:** `solutions.pdroti.lead.enrichment.api`

---

## Índice

1. [Visão Geral da Arquitetura](#1-visão-geral-da-arquitetura)
2. [Estrutura do Projeto](#2-estrutura-do-projeto)
3. [Camadas e Responsabilidades](#3-camadas-e-responsabilidades)
4. [Pipeline de Enriquecimento](#4-pipeline-de-enriquecimento)
5. [Modelo de Dados](#5-modelo-de-dados)
6. [API REST](#6-api-rest)
7. [Configuração Externalizada](#7-configuração-externalizada)
8. [Segurança e Criptografia](#8-segurança-e-criptografia)
9. [Observabilidade](#9-observabilidade)
10. [Performance e Otimizações](#10-performance-e-otimizações)
11. [Dependências Externas](#11-dependências-externas)
12. [Tratamento de Erros](#12-tratamento-de-erros)

---

## 1. Visão Geral da Arquitetura

A **Lead Enrichment API** é uma aplicação Spring Boot monolítica que enriquece dados de leads a partir de fontes públicas da internet. Utiliza uma arquitetura em camadas com serviços especializados e orquestração centralizada.

```
┌─────────────────────────────────────────────────────────────┐
│                    Camada de Apresentação                    │
│  LeadController → OpenApiConfig → GlobalExceptionHandler    │
├─────────────────────────────────────────────────────────────┤
│                    Camada de Segurança                       │
│              ApiKeyFilter (valida X-API-KEY)                │
├─────────────────────────────────────────────────────────────┤
│                     Camada de Serviços                       │
│  ┌───────────────┐  ┌────────────────┐  ┌────────────────┐  │
│  │   LeadService │  │OpenSerpEnricher│  │DomainEnricher  │  │
│  │  (orquestrador)│  │                │  │                │  │
│  └───────┬───────┘  └───────┬────────┘  └───────┬────────┘  │
│          │                  │                    │           │
│          │    ┌─────────────┼────────────────────┘           │
│          │    │             │                                │
│          ▼    ▼             ▼                                │
│  ┌───────────────┐  ┌────────────────┐  ┌────────────────┐  │
│  │DnsValidation  │  │TechScraper     │  │SocialDiscovery │  │
│  │(dnsjava)      │  │(Jsoup)         │  │(Jsoup)         │  │
│  └───────────────┘  └────────────────┘  └────────────────┘  │
│  ┌───────────────┐  ┌────────────────┐  ┌────────────────┐  │
│  │RdapService    │  │OpenSerpSearch  │  │LeadDeletion    │  │
│  │(HTTP)         │  │(RestTemplate)  │  │                │  │
│  └───────────────┘  └────────────────┘  └────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│                   Camada de Persistência                     │
│  LeadRepository (Spring Data JPA) → PostgreSQL 16            │
│  EncryptedEmailConverter (AES-128-GCM)                       │
├─────────────────────────────────────────────────────────────┤
│                   Serviços Externos                          │
│  Nameservers Públicos → Sites Web → Redes Sociais           │
│  Identity Digital / Registro.br → OpenSERP Self-hosted      │
└─────────────────────────────────────────────────────────────┘
```

### Princípios Arquiteturais

| Princípio | Aplicação |
|---|---|
| **Separação de Responsabilidades** | Cada serviço tem uma única responsabilidade (DNS, scraping, RDAP, etc.) |
| **Orquestração Centralizada** | `LeadService` coordena todo o pipeline; serviços não se conhecem |
| **Paralelismo** | OpenSERP e Domain Enricher executam em paralelo via `CompletableFuture` |
| **Tolerância a Falhas** | Falha em um serviço não interrompe os demais; dados parciais são persistidos |
| **Externalização** | Configurações sensíveis e regras de negócio em `application.yml` e `.env` |

---

## 2. Estrutura do Projeto

```
lead-enrichment-api/
├── pom.xml                          # Maven + dependências
├── Dockerfile                       # Multi-stage build (JDK 21 → JRE Alpine)
├── docker-compose.yml               # PostgreSQL + 3x OpenSERP + Jaeger + App
├── build-jdk21.bat                  # Script de build com JDK 21 (Windows)
├── run.bat                          # Script de execução local
├── .env.example                     # Template de variáveis de ambiente
├── README.md                        # Documentação principal
│
├── docs/
│   ├── TECHNICAL_REFERENCE.md       # ← Este documento
│   ├── ONBOARDING.md                # Guia de onboarding para devs
│   ├── architecture.md              # Arquitetura do sistema
│   ├── api-guide.md                 # Guia completo da API
│   ├── deployment.md                # Guia de deploy
│   ├── security-lgpd.md             # Segurança e LGPD
│   ├── openapi.yaml                 # OpenAPI 3.0 Spec
│   ├── index.md                     # Índice da documentação
│   ├── adr/                         # Architecture Decision Records (10 ADRs)
│   └── diagrams/                    # Diagramas Mermaid
│
└── src/main/java/solutions/pdroti/lead/enrichment/api/
    ├── LeadEnrichmentApplication.java   # Entry point
    ├── config/
    │   ├── ApiKeyFilter.java            # Filtro de autenticação
    │   ├── AppConfig.java               # Beans RestTemplate, Executor, Cache
    │   ├── EncryptedEmailConverter.java # JPA AttributeConverter AES-GCM
    │   ├── GlobalExceptionHandler.java  # Handler global de erros
    │   ├── OpenApiConfig.java           # Configuração Swagger/OpenAPI
    │   ├── OpenSerpProxyProperties.java # Configuração OpenSERP endpoints
    │   ├── SocialDiscoveryProperties.java
    │   ├── TechScraperProperties.java
    │   ├── RedisConfig.java             # Redis condicional (Lettuce)
    │   └── TracingFilter.java           # Filtro OpenTelemetry
    ├── controller/
    │   └── LeadController.java          # Endpoints REST
    ├── dto/                             # Records de requisição/resposta
    │   ├── LeadRequest.java
    │   ├── LeadResponse.java
    │   ├── LeadResponseSummary.java     # Resumo leve para listagens
    │   ├── DnsRecords.java
    │   ├── DiscoveryData.java
    │   ├── DnsResult.java
    │   ├── RdapData.java
    │   ├── SerpSearchResult.java
    │   ├── SerpResultItem.java
    │   ├── SocialProfileData.java
    │   └── ScrapedPageData.java
    ├── model/
    │   └── Lead.java                    # Entidade JPA
    ├── repository/
    │   └── LeadRepository.java          # Spring Data JPA
    ├── service/
    │   ├── LeadService.java             # Orquestrador principal
    │   ├── OpenSerpEnricherService.java        # Enriquecimento via OpenSERP
    │   ├── DomainEnricherService.java          # DNS + RDAP + scraping + sociais
    │   ├── LeadDeletionService.java     # Hard delete
    │   ├── DnsValidationService.java    # Consultas DNS (dnsjava)
    │   ├── TechScraperService.java      # Detecção de tecnologias (Jsoup)
    │   ├── SocialDiscoveryService.java  # Descoberta de redes sociais (Jsoup)
    │   ├── RdapService.java             # Consulta RDAP (HTTP)
    │   ├── OpenSerpSearchService.java          # API OpenSERP (RestTemplate + cache L1+L2)
    │   ├── DotComScrapingService.java    # Scraping .com/.br (redes sociais, telefones, e-mails)
    │   ├── RedisCacheService.java       # Cache L2 Redis (async set + fallback)
    │   └── EncryptionService.java       # AES-128-GCM
    └── util/
        ├── ContentTracker.java          # Hash SHA-256 para detecção de mudanças
        ├── EmailUtils.java              # SHA-256 + mascaramento LGPD
        ├── DataParser.java              # Parsers estáticos + COMMON_EMAIL_PROVIDERS
        ├── ErrorMatcher.java            # Interface funcional para classificação de erros
        └── EnrichmentSnapshotManager.java # Snapshot/restore de campos em reenriquecimento

    enums/
        └── ScrapeError.java             # Classificação de erros de scraping
```

---

## 3. Camadas e Responsabilidades

### 3.1 Camada de Configuração (`config/`)

| Classe | Responsabilidade |
|---|---|
| `AppConfig` | Beans `RestTemplate` (5s/20s padrão, 10s/30s OpenSERP), `Executor` (Virtual Threads), `CacheManager` (Caffeine) |
| `ApiKeyFilter` | Filtro `OncePerRequestFilter` que valida header `X-API-KEY`; endpoints públicos: `/actuator`, `/swagger-ui`, `/v3/api-docs` |
| `GlobalExceptionHandler` | `@RestControllerAdvice` que padroniza erros: validação (400), argumento inválido (400), I/O (log apenas), genérico (500) |
| `OpenApiConfig` | Configuração SpringDoc OpenAPI com esquema de segurança `X-API-KEY` |
| `TracingFilter` | Filtro OpenTelemetry — captura corpo de request/response para tracing distribuído |
| `TimingFilter` | Filtro de timing — loga duração de cada requisição HTTP (ex: `▶ POST /api/v1/leads/enrich → 200 em 34.2s`) — **desabilitado** (`//@Component`) |
| `EncryptedEmailConverter` | `AttributeConverter` que criptografa/descriptografa e-mail com AES-128-GCM |
| `TechScraperProperties` | `@ConfigurationProperties` com 65+ assinaturas de tecnologia, detectores de script e meta-generators |
| `SocialDiscoveryProperties` | `@ConfigurationProperties` com 31 domínios de redes sociais e 23 nomes de plataforma |
| `OpenSerpProxyProperties` | `@ConfigurationProperties` para múltiplos endpoints OpenSERP com proxy rotation |
| `RedisConfig` | `@Configuration` | Factory `LettuceConnectionFactory` + `StringRedisTemplate` condicionais (`@ConditionalOnProperty`) |

### 3.2 Camada de Serviços (`service/`)

| Serviço | Tecnologia | Função | Cache |
|---|---|---|---|
| `LeadService` | Spring `@Service` | Orquestrador: coordena `OpenSerpEnricherService` + `DomainEnricherService` em paralelo | — |
| `DotComScrapingService` | Jsoup + RestTemplate | Scraping de sites .com/.br sem domínio: sociais, telefones, e-mails | — |
| `RedisCacheService` | Redis (Lettuce) | Cache L2 distribuído (get síncrono, setAsync fire-and-forget, fallback Caffeine) | Redis |
| `OpenSerpEnricherService` | Gson + RestTemplate | Busca Google via OpenSERP (6 frentes, mergeField seguro) | — |
| `DomainEnricherService` | Diversos | Orquestra DNS + TechScraper + Social + RDAP | — |
| `DnsValidationService` | dnsjava | Consulta 5 tipos de registro DNS (MX, A, AAAA, CNAME, TXT) em paralelo | Caffeine 1h |
| `TechScraperService` | Jsoup | Detecta tecnologias do site (~90 assinaturas) + verifica menção de nome | Caffeine 1h |
| `SocialDiscoveryService` | Jsoup | Descobre links de redes sociais (31 plataformas) + faz scraping de perfis | Caffeine 1h (2 caches) |
| `RdapService` | HTTP (HttpClient) | Consulta RDAP na Identity Digital e Registro.br | Caffeine 1h |
| `EncryptionService` | AES-128-GCM | Criptografia/descriptografia de e-mails | — |
| `LeadDeletionService` | Spring Data JPA | Hard delete em 1 query (`deleteById`) | — |

### 3.3 DTOs (`dto/`)

| DTO | Tipo | Descrição |
|---|---|---|
| `LeadRequest` | Record | Validação `@Valid`: `email` (obrigatório), `domain` (opcional), `name` (obrigatório) |
| `LeadResponse` | Record | Resposta com sub-records: `dns`, `discovery`, `rdap` + factory `fromEntity()` |
| `DnsRecords` | Record | Sub-record: mxRecords, aRecords, aaaaRecords, cnameRecords, txtRecords |
| `DiscoveryData` | Record | Sub-record: technologies, socialLinks, socialProfileSummaries, exposedEmails, etc. |
| `DnsResult` | Record | Resultado intermediário das consultas DNS |
| `RdapData` | Record | Dados RDAP: registrar, registrantName, registrationDate, expirationDate, taxpayerId, source |
| `SerpSearchResult` | Record | Resultado da busca OpenSERP com lista de `SerpResultItem` |
| `SerpResultItem` | Record | Item individual: title, url, snippet, domain, fileType |
| `SocialProfileData` | Record | Perfil social: platform, profileUrl, title, description + `toSummary()` |
| `ScrapedPageData` | Record | Dados de página: title, description, language, favicon, canonicalUrl, themeColor, charset, technologies, Open Graph, Twitter Cards, h1, socialLinks |
| `LeadResponseSummary` | Record | Resumo leve para listagens: sem parse de JSONs brutos, apenas contagens

---

## 4. Pipeline de Enriquecimento

### 4.1 Fluxo Principal

```
POST /api/v1/leads/enrich
         │
         ▼
┌─────────────────┐
│  ApiKeyFilter   │ ← Valida X-API-KEY
│  401 se inválida│
└────────┬────────┘
         ▼
┌─────────────────┐
│  LeadController │ ← @Valid LeadRequest
│  400 se inválido│
└────────┬────────┘
         ▼
┌─────────────────┐
│   LeadService   │ ← Extrai domínio, gera hash SHA-256
│  enrich()       │ ← Busca lead existente por hash
└────────┬────────┘
         │
         ▼
  ┌──────────────────────────────────────────┐
  │        CompletableFuture.allOf()         │
  │                                          │
  │  ┌─────────────────┐  ┌───────────────┐  │
  │  │ OpenSerpEnricher │  │DomainEnricher │  │
  │  │ (sempre executa) │  │(se houver     │  │
  │  │                  │  │ domínio)      │  │
  │  │ 1. fetchResults  │  │ 1. DNS (5x)  │  │
  │  │ 2. fetchDocuments│  │ 2. TechScrape │  │
  │  │ 3. processResults│  │ 3. SocialDisc │  │
  │  │ 4. serialize     │  │ 4. RDAP       │  │
  │  └─────────────────┘  └───────────────┘  │
  └──────────────────────────────────────────┘
         │
         ▼
┌─────────────────┐
│  LeadRepository │ ← Persiste (AES-GCM)
│  save(lead)     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  LeadController │ ← Retorna 200 + List<LeadResponse>
│  (email mascarado)│
└─────────────────┘
```

### 4.2 Execução Paralela

O enriquecimento usa `CompletableFuture.allOf()` com pool de **Virtual Threads** (Java 21):

```java
@Bean("enrichmentExecutor")
public Executor enrichmentExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
}
```

- **OpenSERP** (sempre): 2 chamadas REST paralelas (resultados + documentos)
- **Domain** (se houver domínio): DNS (5 consultas), TechScraper (1 HTTP), SocialDiscovery (1 HTTP), RDAP (1 HTTP)
- Ambos os blocos rodam simultaneamente; o tempo total ≈ duração do mais lento

### 4.3 Cache Distribuído (L1 Caffeine + L2 Redis)

| Cache | TTL | Capacidade | Tipo | Chave |
|---|---|---|---|---|
| DNS Records | 1 hora | 10.000 | Caffeine | domínio |
| Tecnologias | 1 hora | 10.000 | Caffeine | domínio |
| Links Sociais | 1 hora | 10.000 | Caffeine | domínio |
| RDAP | 1 hora | 10.000 | Caffeine | domínio |
| Perfis Sociais | 1 hora | 5.000 | Caffeine | URL |
| OpenSERP (L1) | 30 min | 5.000 | Caffeine | query:name:limit |
| OpenSERP (L2) | 30 min | — | Redis (String) | lead-enrich:query:name:limit |
| ContentTracker Hash | 2 horas | 5.000 | Caffeine | query:name:limit |
| @Cacheable enrich | 24 horas | 10.000 | Caffeine (CacheManager) | email |

**Redis** é opcional: se `spring.data.redis.host` não estiver configurado, a aplicação opera apenas com cache local Caffeine (fallback transparente).

**RedisCacheService**:
- Leitura síncrona com timeout de 5s (configurável)
- Escrita assíncrona (fire-and-forget em virtual thread) — nunca bloqueia a resposta
- Prefixo de chave: `lead-enrich:`
- Pool de conexões Lettuce (max 8, min 1)

**ContentTracker** compara hash SHA-256 do novo resultado com o hash anterior para detectar mudanças quando o cache expira.

---

## 5. Modelo de Dados

### 5.1 Entidade Lead

A entidade `Lead` possui ~40 campos organizados em grupos:

| Grupo | Campos | Descrição |
|---|---|---|
| **Identidade** | `id`, `email` (criptografado), `emailHash` (SHA-256, unique) | Identificação do lead |
| **Domínio** | `domain`, `name`, `mxStatus`, `status` | Dados básicos |
| **DNS** | `dnsMxRecords`, `dnsARecords`, `dnsAaaaRecords`, `dnsCnameRecords`, `dnsTxtRecords` | Registros DNS (LAZY) |
| **Tecnologias** | `technologies` | Tecnologias detectadas (LAZY) |
| **Social** | `socialLinks`, `socialProfileSummaries` | Redes sociais (LAZY) |
| **Dorks** | `exposedEmails`, `exposedPhones`, `dorkFindings`, `nameMentions` | Info exposta (LAZY) |
| **RDAP** | `rdapRegistrar`, `rdapRegistrantName`, `rdapRegistrationDate`, etc. | Registro de domínio |
| **OpenSERP** | `openSerpRawData`, `foundDocuments`, `discoveredUrls` | Busca Google |
| **LGPD** | `consentGiven`, `consentDate`, `dataRetentionUntil`, `deletedAt` | Compliance |

> `@ElementCollection(fetch = FetchType.LAZY)` + `@Fetch(FetchMode.SUBSELECT)` são usados em todas as 15 coleções para evitar N+1 sem causar `MultipleBagFetchException`.
> `@BatchSize(size = 10)` otimiza o carregamento de coleções LAZY.
> `@Version` com `@Builder.Default private Long version = 0L` garante lock otimista contra race conditions em reenriquecimento.

### 5.2 Mapeamento Lead → LeadResponse

```
Lead (entidade)                          LeadResponse (DTO)
─────────────────                       ───────────────────
id                                       id
email (AES-GCM)                          emailMasked (con***@exemplo.com)
name                                     name
domain                                   domain
status                                   status
dnsMxRecords ......  →  dns.mxRecords
dnsARecords  ......  →  dns.aRecords
technologies  ......  →  discovery.technologies
socialLinks   ......  →  discovery.socialLinks
exposedPhones ......  →  discovery.exposedPhones
rdapRegistrar ......  →  rdap.registrar
...                                      ...
openSerpRawData .....  →  discovery.serpRawData (se presente)
```

### 5.3 Comportamentos Especiais

- **Snapshot/Restore:** Se o reenriquecimento falhar (ex: CAPTCHA no OpenSERP), os dados anteriores são preservados automaticamente via `EnrichmentSnapshotManager`. Responsabilidade extraída do `LeadService` para classe dedicada.
- **Scraping sem Domínio:** Quando nenhum domínio é informado, `DotComScrapingService` percorre sites `.com`/`.com.br` encontrados pelo OpenSERP para extrair telefones, e-mails e redes sociais.
- **Deduplicação:** `nameMentions` são deduplicados por URL; `foundDocuments` e `discoveredUrls` são deduplicados mantendo a ordem; itens do OpenSERP são deduplicados por URL.
- **Filtragem de Links Sociais:** `socialLinks` são filtrados para manter apenas URLs que contenham o nome ou e-mail exato da pessoa.

---

## 6. API REST

### 6.1 Endpoints

| Método | Path | Descrição | Autenticação |
|---|---|---|---|
| `POST` | `/api/v1/leads/enrich` | Enriquecer lead | Obrigatória |
| `GET` | `/api/v1/leads` | Listar leads (paginado) | Obrigatória |
| `GET` | `/api/v1/leads/{id}` | Buscar por ID | Obrigatória |
| `GET` | `/api/v1/leads/domain/{domain}` | Buscar por domínio (paginado) | Obrigatória |
| `PUT` | `/api/v1/leads/{id}` | Atualizar e reenriquecer | Obrigatória |
| `DELETE` | `/api/v1/leads/{id}` | Excluir permanentemente | Obrigatória |
| `GET` | `/actuator/health` | Health check | Pública |
| `GET` | `/actuator/health/liveness` | Liveness probe | Pública |
| `GET` | `/actuator/health/readiness` | Readiness probe | Pública |
| `GET` | `/actuator/metrics` | Métricas | Pública |
| `GET` | `/swagger-ui/index.html` | Swagger UI | Pública |
| `GET` | `/v3/api-docs` | OpenAPI spec | Pública |

### 6.2 Paginação

Parâmetros de query comuns:
- `page` (int, default: 0)
- `size` (int, default: 20)
- `sort` (string, default: `createdAt`)

Retorno encapsulado em Spring `Page<LeadResponse>` com `content`, `page.size`, `page.totalElements`, `page.totalPages`, `page.number`.

### 6.3 Formato de Erro Padronizado

```json
{
  "error": "Tipo do Erro",
  "message": "Descrição amigável",
  "timestamp": "2026-06-13T10:30:00"
}
```

| Código | Causa |
|---|---|
| 400 | `@Valid` falhou, ou argumento inválido |
| 401 | API Key ausente ou incorreta |
| 404 | Lead não encontrado |
| 500 | Erro interno não esperado |

---

## 7. Configuração Externalizada

### 7.1 Variáveis de Ambiente (`.env`)

| Variável | Obrigatória | Descrição | Exemplo |
|---|---|---|---|
| `DB_URL` | ✅ | URL JDBC do PostgreSQL | `jdbc:postgresql://localhost:5433/postgres` |
| `DB_USERNAME` | ✅ | Usuário do banco | `postgres` |
| `DB_PASSWORD` | ✅ | Senha do banco | — |
| `API_KEY` | ✅ | Chave de autenticação | — |
| `ENCRYPTION_SECRET` | ✅ | Chave AES-128-GCM (16+ bytes) | `f44sGktPn25aHIuTfi9KbIwNnh8qO0xdbn+KmwwePz8=` |
| `OPENSERP_API_URL` | ✅ | URL do OpenSERP | `http://localhost:7000` |
| `PORT` | ❌ | Porta HTTP (default: 8081) | `8081` |
| `ENV` | ❌ | Sufixo de ambiente Docker | `dev` |

### 7.2 Configurações em `application.yml`

| Bloco | Propósito |
|---|---|
| `server.compression` | Gzip habilitado para JSON/XML > 1KB |
| `spring.threads.virtual.enabled` | Virtual Threads (Java 21) |
| `spring.jpa.hibernate.ddl-auto` | `update` — schema gerenciado pelo Hibernate |
| `open-serp.api.endpoints` | Lista de endpoints OpenSERP com proxy rotation |
| `techscraper.signatures` | 65+ assinaturas de tecnologia (WordPress, React, etc.) |
| `techscraper.script-detectors` | ~20 detectores de script (Facebook Pixel, etc.) |
| `social-discovery.social-domains` | 31 domínios de redes sociais |
| `social-discovery.platform-names` | 23 nomes amigáveis de plataforma |
| `management.tracing` | OpenTelemetry + Jaeger |
| `management.otlp.tracing.endpoint` | Endpoint OTLP para exportação de traces |

---

## 8. Segurança e Criptografia

### 8.1 Stack de Segurança

```
┌─────────────────────────────────────────────────────────┐
│  Autenticação                                            │
│  ApiKeyFilter (header X-API-KEY)                        │
│  Endpoints públicos: /actuator, /swagger-ui, /v3/api-docs│
├─────────────────────────────────────────────────────────┤
│  Criptografia em Repouso                                 │
│  AES-128-GCM (Galois/Counter Mode)                       │
│  IV aleatório de 12 bytes por operação                   │
│  Formato: ENC(Base64(IV + Ciphertext + Tag))             │
├─────────────────────────────────────────────────────────┤
│  Hash para Consulta                                      │
│  SHA-256(email.toLowerCase()) → emailHash (unique)       │
│  ThreadLocal para performance                            │
├─────────────────────────────────────────────────────────┤
│  Mascaramento LGPD                                       │
│  EmailUtils.mask(): "pedro@pdroti.com" → "ped***@pdroti.com"│
│  Aplicado em logs + respostas da API                     │
├─────────────────────────────────────────────────────────┤
│  Exclusão Permanente                                     │
│  Hard delete via deleteById (1 query)                    │
│  Log: "Lead hard deleted: ID=X"                          │
└─────────────────────────────────────────────────────────┘
```

### 8.2 Fluxo de Criptografia

| Etapa | Descrição |
|---|---|
| **Entrada** | E-mail em texto plano: `pedro@pdroti.com` |
| **Hash** | SHA-256 → `emailHash` (para consulta) |
| **Criptografia** | AES-128-GCM.encrypt() com IV aleatório |
| **Codificação** | Base64(IV + ciphertext + tag) |
| **Armazenamento** | `Lead.email` = texto criptografado |
| **Leitura** | Descriptografado apenas em memória; nunca retornado na API |
| **Resposta** | Apenas `emailMasked` é exposto |

---

## 9. Observabilidade

### 9.1 OpenTelemetry + Jaeger

| Componente | Configuração |
|---|---|
| **Tracing** | Micrometer Tracing Bridge OTel |
| **Exportador** | OTLP (`management.otlp.tracing.endpoint`) |
| **Sample Rate** | 100% (`sampling.probability: 1.0`) |
| **Body Capture** | `TracingFilter` captura request/response body |
| **Jaeger UI** | `http://localhost:16686` (via Docker) |

### 9.2 Actuator

| Endpoint | Função |
|---|---|
| `/actuator/health` | Health check completo |
| `/actuator/health/liveness` | Liveness probe (Kubernetes) |
| `/actuator/health/readiness` | Readiness probe (Kubernetes) |
| `/actuator/metrics` | Métricas da JVM e da aplicação |
| `/actuator/info` | Informações da aplicação |

### 9.3 Logging

- **Formato:** Logback padrão do Spring Boot
- **Mascaramento:** Todos os e-mails em log passam por `EmailUtils.mask()`
- **Níveis:** `INFO` para fluxo principal, `DEBUG` para detalhes, `WARN` para problemas, `ERROR` para exceções
- **Identificação:** Logs incluem `email` mascarado para rastreabilidade

---

## 10. Performance e Otimizações

| Otimização | Implementação | Benefício |
|---|---|---|
| **Virtual Threads** | `spring.threads.virtual.enabled=true` | Pool leve para I/O intensivo |
| **Paralelismo** | `CompletableFuture.allOf()` no `LeadService` | Reduz tempo total pela operação mais lenta |
| **Cache Caffeine** | DNS (1h/1000), Tech (1h/500), Social (1h/500) | Evita consultas repetidas a serviços externos |
| **HTTP Connection Pooling** | Apache HttpClient 5 (200 conexões) | Reaproveita conexões TCP |
| **Compressão Gzip** | `server.compression.enabled=true` | Reduz tráfego em ~70% para JSON |
| **FetchType LAZY** | `@ElementCollection(fetch = LAZY)` | Evita queries desnecessárias |
| **1 Chamada HTTP** | TechScraper combina tecnologias + verificação de nome | 1 request em vez de 2 |
| **DNS Paralelo** | 5 tipos de registro simultâneos | Reduz latência DNS |

---

## 11. Dependências Externas

| Serviço | Tecnologia | Porta | Função |
|---|---|---|---|
| **PostgreSQL** | 16 | 5433 (host) / 5432 (container) | Banco de dados |
| **OpenSERP** | karust/openserp | 7000, 7002, 7003 | Google Search API self-hosted (3 instâncias) |
| **Jaeger** | jaegertracing/all-in-one | 4317 (gRPC), 4318 (HTTP), 16686 (UI) | Tracing distribuído |
| **Nameservers** | Públicos | 53 (UDP) | Consultas DNS |
| **Sites Web** | HTTP/HTTPS | 80/443 | Scraping de tecnologias |
| **Redes Sociais** | HTTP/HTTPS | 443 | Scraping de perfis (31 plataformas) |
| **Identity Digital** | RDAP/HTTP | 443 | Consulta RDAP |
| **Registro.br** | RDAP/HTTP | 443 | Consulta RDAP (.br) |

---

## 12. Tratamento de Erros

### 12.1 Hierarquia de Exceções

```
Exception
├── MethodArgumentNotValidException  → 400 (validation errors)
├── IllegalArgumentException         → 400 (bad request)
├── IOException                      → log apenas (client disconnect)
└── Exception (genérico)             → 500 (internal error)
```

### 12.2 Respostas de Erro

| Situação | HTTP | Body |
|---|---|---|
| API Key inválida | 401 | `{"error":"Unauthorized","message":"Invalid or missing API key"}` |
| Validação `@Valid` | 400 | `{"error":"Validation Error","details":["email: must not be blank"],"timestamp":"..."}` |
| Lead não encontrado | 404 | `{"error":"Lead não encontrado","id":"999"}` |
| Erro interno | 500 | `{"error":"Internal Server Error","message":"Ocorreu um erro interno."}` |

### 12.3 Desconexão de Cliente

O `GlobalExceptionHandler` captura `IOException` e identifica padrões como `broken pipe`, `anulada`, `abort` e `reset`, apenas logando `WARN` sem poluir o stack trace.

---

> **Documentação mantida por:** pdroti.solutions  |  **Última atualização:** 2026-06-13
