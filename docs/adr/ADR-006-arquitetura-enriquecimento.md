# ADR-006: Arquitetura de Enriquecimento — Orquestração com Serviços Especializados

## Status

Aceito

## Contexto

O enriquecimento de leads envolve múltiplas fontes de dados externas (DNS, RDAP, scraping web, redes sociais, OpenSERP). A aplicação precisa:

- Orquestrar chamadas concorrentes a serviços externos
- Garantir que falhas isoladas não interrompam o fluxo completo
- Suportar dois modos de operação (com e sem domínio)
- Ser extensível para novas fontes de dados
- Manter responsabilidades bem definidas entre componentes

## Decisão

Adotar uma arquitetura de **orquestração centralizada** com serviços especializados:

```
LeadController
     │
     ▼
LeadService (Orquestrador — Virtual Threads)
     │
     ├──▶ OpenSerpEnricher (6 buscas paralelas — merge seguro)
     │       ├── searchPerson()         → busca geral (cache L1+L2)
     │       ├── searchDocuments()      → PDF, DOC, DOCX (cache L1+L2)
     │       ├── searchSocialMedia()    → redes sociais (cache L1+L2)
     │       ├── searchProfessional()   → LinkedIn, GitHub, CV (cache L1+L2)
     │       ├── searchContact()        → email, telefone (cache L1+L2)
     │       ├── searchNews()           → notícias (cache L1+L2)
     │       ├── OpenSerpSearch         (RestTemplate + HttpClient 5 + RedisCacheService)
     │       ├── RedisCacheService      (L2 cache distribuído)
     │       └── SocialDiscoveryService (domínios sociais)
     │
     ├──▶ DomainEnricher
     │       ├── DnsValidationService    (5 consultas DNS paralelas + cache Caffeine)
     │       ├── TechScraperService      (Jsoup + 60+ assinaturas + cache)
     │       ├── SocialDiscoveryService  (scraping paralelo + cache)
     │       └── RdapService            (HTTP — Identity Digital / Registro.br)
     │
     └──▶ LeadDeletionService
     │       └── LeadRepository (hard delete)
     │
     📦 DataParser (util — email, phone, name parsers)
     📦 EmailUtils (util — mascaramento e hash)
     📦 AppConfig  (RestTemplate + Connection Pooling + Caffeine caches + CacheManager)
     📦 RedisConfig (Redis condicional + LettuceConnectionFactory)
     📦 RedisCacheService (L2 cache distribuído com fallback)
     📦 ContentTracker (hash SHA-256 para detecção de mudanças)
```

### Serviços e Responsabilidades

| Serviço | Tecnologia | Dados Obtidos | Isolamento |
|---|---|---|---|
| `OpenSerpEnricher` | — | Orquestra 6 buscas Google paralelas + extrai dados (emails, telefones, menções) | `supplySearch()` com try-catch |
| `DomainEnricher` | — | Orquestra DNS + RDAP + scraping + sociais com cache Caffeine | `executeSafely` próprio |
| `LeadDeletionService` | Spring Data JPA | Hard delete (1 query) | `parseNumericId` |
| `DnsValidationService` | dnsjava 3.6 | MX, A, AAAA, CNAME, TXT | try-catch via `executeSafely` |
| `TechScraperService` | Jsoup 1.17 | ~90 assinaturas de tecnologia (externalizadas em YAML), e-mails expostos, menções de nome | try-catch próprio |
| `SocialDiscoveryService` | Jsoup 1.17 | Links para 31 plataformas (externalizadas em YAML), perfis com título/descrição | try-catch próprio |
| `RdapService` | RestTemplate | Identity Digital + Registro.br (CPF/CNPJ .com.br) | try-catch próprio |
| `OpenSerpSearch` | RestTemplate | Google Search API self-hosted (até 15 resultados, timeout 30s, proxy rotation) | try-catch próprio |

### Camada de Configuração Externalizada

Também foram criados utilitários estáticos (`DataParser`) e serviços auxiliares (`LeadDeletionService`) para manter o `LeadService` como orquestrador puro (~140 linhas).

A refatoração introduziu classes `@ConfigurationProperties` para centralizar parâmetros antes hardcoded:

| Classe | Prefixo YAML | Propriedades |
|---|---|---|
| `TechScraperProperties` | `techscraper.signatures` | Mapa de tecnologia → assinatura HTML (90+ entradas) |
| `SocialDiscoveryProperties` | `social-discovery.domains` / `social-discovery.platform-names` | Domínios de 31 redes sociais + nomes amigáveis das plataformas |
| `AppConfig` | — (bean `@Bean`) | RestTemplate com timeouts configurados (connectTimeout: 5s, readTimeout: 20s) |

### Fluxo de Decisão

```
LeadService.enrich()
     │
     ├── Extrair domínio do e-mail (DataParser.extractDomainFromEmail)
     ├── Buscar lead existente por hash SHA-256
     ├── DomainEnricher.resetEnrichmentData()
     ├── OpenSerpEnricher.enrich()  ← SEMPRE executado
     │     ├── fetchResults() + fetchDocuments()
     │     └── processResults() com SerpProcessingContext
     ├── Se domínio válido:
     │     └── DomainEnricher.enrich()
     │           ├── DnsValidationService.lookupDomain()
     │           ├── TechScraperService.scrapeTechnologiesAndCheckName()
     │           ├── SocialDiscoveryService.discoverSocialLinks()
     │           └── RdapService.lookup()
     │
     ├── Converter Lead → LeadResponse (ObjectMapper injetado)
     │     ├── DnsRecords  (sub-record)
     │     ├── DiscoveryData (sub-record)
     │     └── RdapData (com rawJson: JsonNode)
     └── Persistir (LeadRepository.save())
```

### Otimização 1: Chamada HTTP Combinada

O `TechScraperService` unificou duas chamadas HTTP separadas em uma única requisição:

```
Antes:                        Agora:
  scrapeAndDetect(domain)      scrapeTechnologiesAndCheckName(domain, name)
  findNameInPage(domain, name) ──────────────────────────────────────────►
      2 requisições HTTP                 1 requisição HTTP
```

Isso reduziu o tempo de scraping em ~50% e eliminou uma conexão duplicada.

### Otimização 2: Execução Paralela com CompletableFuture

O `LeadService` foi otimizado para executar o OpenSERP e o DomainEnricher **em paralelo** via `CompletableFuture.allOf()`:

```
Antes (sequencial — ~soma dos tempos):          Agora (paralelo — ~max dos tempos):
  OpenSerpEnricher.enrich()  ──┐                  OpenSerpEnricher.enrich()  ──┐
                               ├── tempo total    DomainEnricher.enrich()    ──┤── allOf
  DomainEnricher.enrich()   ──┘                                                │
                                                                  ambas finalizam ─┘
```

Também dentro do `OpenSerpEnricher`, as duas chamadas HTTP (`fetchResults` + `fetchDocuments`) foram paralelizadas:

```java
CompletableFuture<JsonArray> resultsFuture = CompletableFuture.supplyAsync(() -> fetchResults(name));
CompletableFuture<JsonArray> docsFuture = CompletableFuture.supplyAsync(() -> fetchDocuments(name));
CompletableFuture.allOf(resultsFuture, docsFuture).join();
```

### Otimização 3: Timeouts Ajustados

| Parâmetro | Antes | Depois | Motivo |
|---|---|---|---|
| OpenSERP read timeout | 30s | 30s | Ajustado via AppConfig (socketTimeout) |
| OpenSERP max results | 30 | 15 | Reduz tráfego e processamento |
| Tomcat connection-timeout | 300s | 60s | Libera threads mais cedo |
| Spring async request-timeout | 300s | 60s | Consistente com timeout HTTP |

### Isolamento de Falhas

Cada chamada a serviço externo é envolvida em try-catch individual. Se um serviço falha (ex: RDAP offline), os demais continuam processando. O lead é salvo com os dados parciais disponíveis.

## Consequências

- Positivas:
  - Resiliência: falhas isoladas não quebram o fluxo
  - Extensibilidade: novos serviços são adicionados sem modificar os existentes
  - Clareza: cada serviço tem responsabilidade única (SRP)
  - Testabilidade: cada serviço pode ser testado isoladamente
  - Configurações externalizadas em YAML permitem ajustes sem recompilar
  - RestTemplate gerenciado pelo Spring elimina gerenciamento manual de conexões
  - Sub-records (`DnsRecords`, `DiscoveryData`) reduziram `LeadResponse` de 22 campos para 10

- Negativas:
  - Chamadas síncronas aumentam latência total (pode chegar a 30s+)
  - Sem cache distribuído implementado (apenas `@EnableCaching` declarado)
  - Orquestração sequencial — serviços poderiam ser paralelizados com `CompletableFuture`
  - `@ConfigurationProperties` exige atualização do YAML se novas plataformas forem adicionadas

## Referências

- [SRP — Single Responsibility Principle](https://blog.cleancoder.com/uncle-bob/2014/05/08/SingleReponsibilityPrinciple.html)
- [Spring @Service](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config.html)
