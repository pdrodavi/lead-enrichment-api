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
LeadService (Orquestrador)
     │
     ├──▶ DnsValidationService   (dnsjava)
     ├──▶ TechScraperService     (Jsoup)
     ├──▶ SocialDiscoveryService (Jsoup)
     ├──▶ RdapService           (HTTP)
     └──▶ OpenSerpSearch        (OkHttp)
```

### Serviços e Responsabilidades

| Serviço | Tecnologia | Dados Obtidos | Isolamento |
|---|---|---|---|
| `DnsValidationService` | dnsjava 3.6 | MX, A, AAAA, CNAME, TXT | try-catch próprio |
| `TechScraperService` | Jsoup 1.17 | ~90 assinaturas de tecnologia, e-mails expostos, menções | try-catch próprio |
| `SocialDiscoveryService` | Jsoup 1.17 | Links para 31 plataformas, perfis com título/descrição | try-catch próprio |
| `RdapService` | HTTP Client | Identity Digital + Registro.br (CPF/CNPJ .com.br) | try-catch próprio |
| `OpenSerpSearch` | OkHttp 4.12 | Google Search API self-hosted (até 30 resultados) | try-catch próprio |

### Fluxo de Decisão

```
LeadService.enrich()
     │
     ├── Extrair domínio do e-mail
     ├── Buscar lead existente por hash SHA-256
     ├── Se domínio válido:
     │     ├── DnsValidationService.lookupDomain()
     │     ├── TechScraperService.scrapeAndDetect()
     │     ├── SocialDiscoveryService.discoverSocialLinks()
     │     ├── SocialDiscoveryService.scrapeSocialProfiles()
     │     ├── RdapService.lookup()
     │     └── OpenSerpSearch.searchPerson()
     └── Se sem domínio:
           └── OpenSerpSearch.searchPerson()
     │
     └── Persistir (LeadRepository.save())
```

### Isolamento de Falhas

Cada chamada a serviço externo é envolvida em try-catch individual. Se um serviço falha (ex: RDAP offline), os demais continuam processando. O lead é salvo com os dados parciais disponíveis.

## Consequências

- Positivas:
  - Resiliência: falhas isoladas não quebram o fluxo
  - Extensibilidade: novos serviços são adicionados sem modificar os existentes
  - Clareza: cada serviço tem responsabilidade única (SRP)
  - Testabilidade: cada serviço pode ser testado isoladamente

- Negativas:
  - Chamadas síncronas aumentam latência total (pode chegar a 30s+)
  - Sem cache distribuído implementado (apenas `@EnableCaching` declarado)
  - Orquestração sequencial — serviços poderiam ser paralelizados com `CompletableFuture`

## Referências

- [SRP — Single Responsibility Principle](https://blog.cleancoder.com/uncle-bob/2014/05/08/SingleReponsibilityPrinciple.html)
- [Spring @Service](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config.html)
