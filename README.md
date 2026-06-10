# Lead Enrichment API

API para enriquecimento de leads com dados públicos.  
A partir de um **nome** (obrigatório), e opcionalmente um e-mail e domínio, a API descobre:

- **Consultas DNS completas** — MX, A (IPv4), AAAA (IPv6), CNAME e TXT
- **Registro RDAP do domínio** — registrar, titular, datas, nameservers, CPF/CNPJ (.com.br)
- **Tecnologias** do site (CMS, frameworks, analytics, CDN, e-commerce) — ~90 assinaturas
- **Redes sociais** — LinkedIn, GitHub, Instagram, YouTube, TikTok, etc. (31 plataformas)
- **Dados de perfil** das redes sociais (título e descrição via scraping)
- **E-mails expostos** encontrados em resultados de busca
- **Menções ao nome** da pessoa em páginas e resultados de busca
- **Busca por nome via OpenSERP** (Google Search self-hosted) quando não há domínio

Tudo com conformidade LGPD (e-mails criptografados em repouso com AES-GCM, mascarados em logs/respostas, soft-delete com retenção de 365 dias).

---

## Stack

| Tecnologia | Função |
|---|---|
| **Java 17** | Runtime |
| **Spring Boot 3.3.x** | Framework web / JPA / Actuator |
| **PostgreSQL 16** | Banco de dados relacional |
| **Hibernate 6.x** | ORM com `ddl-auto: update` |
| **dnsjava 3.6.x** | Consultas DNS (MX, A, AAAA, CNAME, TXT) |
| **Jsoup 1.17.x** | Scraping HTML (site do domínio e redes sociais) |
| **OkHttp 4.12.x** | Cliente HTTP para OpenSERP |
| **Gson 2.11.x** | Parse de JSON do OpenSERP |
| **SpringDoc OpenAPI 2.5.x** | Swagger UI em `/swagger-ui.html` |
| **Spring Actuator** | Health check, métricas e probes |
| **Lombok** | Redução de boilerplate (`@Slf4j`, `@Builder`, `@RequiredArgsConstructor`) |
| **OpenSERP** | Self-hosted Google Search API |

---

## Requisitos

- JDK 17+
- Docker + Docker Compose
- Maven 3.8+
- OpenSERP self-hosted (opcional — necessário apenas para buscas sem domínio)

---

## Variáveis de Ambiente

| Variável | Descrição | Default |
|---|---|---|
| `DB_URL` | URL do PostgreSQL | `jdbc:postgresql://localhost:5433/postgres` |
| `DB_USERNAME` | Usuário do DB | `postgres` |
| `DB_PASSWORD` | Senha do DB | `pgsqldev` |
| `API_KEY` | Chave de API (header `X-API-KEY`) | `b6vxAgj5KG5HPGCKlQQ7` |
| `ENCRYPTION_SECRET` | Chave AES-128 para criptografia de e-mails (mín. 16 bytes) | `f44sGktPn25aHIuTfi9KbIwNnh8qO0xdbn+KmwwePz8=` |
| `SERPER_API_URL` | URL base da API OpenSERP | `http://localhost:7000` |
| `PORT` | Porta do servidor | `8081` |
| `PG_USER` | Usuário PostgreSQL (Docker) | `postgres` |
| `PG_PASSWORD` | Senha PostgreSQL (Docker) | `pgsqldev` |
| `PG_DB` | Nome do banco (Docker) | `postgres` |
| `PG_PORT` | Porta exposta do PostgreSQL | `5433` |
| `ENV` | Sufixo de ambiente | `dev` |

---

## Execução

### Docker Compose (recomendado)

Sobe PostgreSQL 16 + aplicação:

```bash
# Build + Start
docker compose up --build

# Modo detached (background)
docker compose up --build -d

# Acompanhar logs
docker compose logs -f app

# Parar tudo
docker compose down

# Parar tudo e remover dados do banco
docker compose down -v
```

### Local (sem Docker)

```bash
# PostgreSQL precisa estar rodando na porta 5433
mvn spring-boot:run -Dmaven.test.skip=true

# Ou com variáveis customizadas
API_KEY=... ENCRYPTION_SECRET=... mvn spring-boot:run -Dmaven.test.skip=true
```

---

## Autenticação

Todas as requisições exigem o header:

```http
X-API-KEY: b6vxAgj5KG5HPGCKlQQ7
```

Endpoints públicos (não exigem chave):
- `/actuator/health` — Health check
- `/swagger-ui/**` — Swagger UI
- `/v3/api-docs/**` — OpenAPI spec

---

## Endpoints

### `POST /api/v1/leads/enrich` — Enriquecer um lead

Enriquece um lead com dados públicos. Apenas o **nome** é obrigatório.

**Requisição:**

```json
{
  "name": "João Silva",
  "email": "joao@exemplo.com",
  "domain": "exemplo.com"
}
```

| Campo | Obrigatório | Descrição |
|---|---|---|
| `name` | Sim | Nome completo da pessoa |
| `email` | Não | E-mail do lead (dedup + extração automática de domínio) |
| `domain` | Não | Domínio para enriquecimento (DNS, RDAP, scraping) |

**Comportamento por cenário:**

| Cenário | Fluxo |
|---|---|
| Apenas `name` | Busca via **OpenSERP** (Google) → extrai links, e-mails, menções |
| `name` + `domain` | **DNS** (5 tipos) + **RDAP** + **Tecnologias** + **Redes sociais** + **Verificação de nome no HTML** |
| `name` + `email` (sem domain) | Domínio extraído do e-mail automaticamente → fluxo completo |
| Todos preenchidos | Dedup por e-mail + reenriquecimento completo |

**Resposta (200 OK):**

```json
{
  "id": 1,
  "emailMasked": "joa***@exemplo.com",
  "name": "João Silva",
  "domain": "exemplo.com",
  "mxStatus": true,
  "dnsMxRecords": ["10 mail.exemplo.com."],
  "dnsARecords": ["192.168.1.1"],
  "dnsAaaaRecords": [],
  "dnsCnameRecords": [],
  "dnsTxtRecords": ["v=spf1 include:_spf.google.com ~all"],
  "status": "ACTIVE",
  "technologies": ["WordPress", "jQuery", "Google Analytics", "Cloudflare"],
  "socialLinks": [
    "https://github.com/joaosilva",
    "https://linkedin.com/in/joaosilva"
  ],
  "socialProfileSummaries": [
    "GitHub: joaosilva (João Silva) — Desenvolvedor full-stack",
    "LinkedIn: João Silva — Software Engineer na Empresa X"
  ],
  "exposedEmails": ["contato@exemplo.com"],
  "nameMentions": [
    "Nome completo encontrado em: https://exemplo.com/sobre"
  ],
  "nameMentionUrls": ["https://exemplo.com/sobre"],
  "dorkFindings": 2,
  "serperRawData": null,
  "rdap": {
    "rawJson": { ... },
    "registrar": "HOSTINGER operations, UAB",
    "registrantName": "João Silva",
    "registrantEmail": null,
    "registrationDate": "2023-05-10T14:22:00Z",
    "expirationDate": "2027-05-10T14:22:00Z",
    "nameservers": ["ns1.hostinger.com", "ns2.hostinger.com"],
    "status": ["client transfer prohibited"],
    "taxpayerId": null,
    "source": "identitydigital"
  }
}
```

---

### `PUT /api/v1/leads/{id}` — Atualizar e reenriquecer

Atualiza os dados do lead e executa reenriquecimento completo.

```bash
curl -X PUT http://localhost:8081/api/v1/leads/1 \
  -H "X-API-KEY: b6vxAgj5KG5HPGCKlQQ7" \
  -H "Content-Type: application/json" \
  -d '{"name":"João Silva","email":"joao@novoemail.com","domain":"novodominio.com"}'
```

---

### `GET /api/v1/leads` — Listar todos os leads

Retorna todos os leads ativos (exclui soft-deleted).

```bash
curl http://localhost:8081/api/v1/leads -H "X-API-KEY: b6vxAgj5KG5HPGCKlQQ7"
```

---

### `GET /api/v1/leads/{id}` — Buscar lead por ID

Retorna um lead específico. Retorna 404 se não existir ou estiver soft-deleted.

```bash
curl http://localhost:8081/api/v1/leads/1 -H "X-API-KEY: b6vxAgj5KG5HPGCKlQQ7"
```

---

### `GET /api/v1/leads/domain/{domain}` — Buscar leads por domínio

Retorna todos os leads ativos de um domínio específico.

```bash
curl http://localhost:8081/api/v1/leads/domain/exemplo.com \
  -H "X-API-KEY: b6vxAgj5KG5HPGCKlQQ7"
```

**Resposta (204 No Content)** — se nenhum lead for encontrado para o domínio.

---

### `DELETE /api/v1/leads/{id}` — Soft delete (LGPD)

Marca o lead como `DELETED` (direito ao esquecimento). O registro permanece no banco para auditoria, mas não aparece nas consultas padrão.

```bash
curl -X DELETE http://localhost:8081/api/v1/leads/1 \
  -H "X-API-KEY: b6vxAgj5KG5HPGCKlQQ7"
```

**Resposta (200 OK):**

```json
{
  "message": "Lead excluído com sucesso (LGPD — direito ao esquecimento)",
  "id": "1"
}
```

---

## Funcionalidades de Enriquecimento

### Com domínio informado

| Etapa | Serviço | Dados obtidos |
|---|---|---|
| **1. DNS** | `DnsValidationService` | MX (servidores de e-mail), A (IPv4), AAAA (IPv6), CNAME (alias), TXT (SPF/DKIM) |
| **2. RDAP** | `RdapService` | Registrar, titular, e-mail do titular, datas de registro/expiração, nameservers, status, CPF/CNPJ (.com.br) |
| **3. Tecnologias** | `TechScraperService` | CMS, frameworks JS, CSS, analytics, CDN, e-commerce, pagamento, fontes (~90 assinaturas) |
| **4. Redes sociais** | `SocialDiscoveryService` | Links para 31 plataformas (LinkedIn, GitHub, Instagram, YouTube, TikTok, etc.) |
| **5. Perfis sociais** | `SocialDiscoveryService` | Título e descrição de cada perfil social encontrado |
| **6. Verificação de nome** | `TechScraperService.findNameInPage()` | Confirma se o nome da pessoa aparece no HTML do site |

### Sem domínio informado

| Etapa | Serviço | Dados obtidos |
|---|---|---|
| **1. Busca no Google** | `OpenSerpSearch` (self-hosted) | Até 30 resultados com título, URL e snippet |
| **2. Extração de links** | `LeadService` | Todos os URLs dos resultados |
| **3. Classificação social** | Reuso de `SocialDiscoveryService.getSocialDomains()` | Identifica links de redes sociais |
| **4. Extração de e-mails** | Regex `EMAIL_PATTERN` | E-mails expostos nos snippets |
| **5. Menções ao nome** | Correspondência textual | "Nome completo encontrado em: ..." ou "Parte do nome ... encontrada em: ..." |

---

## Estrutura Completa da Resposta

```
LeadResponse {
  id                  Long           — ID único
  emailMasked         String         — E-mail mascarado (LGPD)
  name                String         — Nome da pessoa
  domain              String         — Domínio validado
  mxStatus            boolean        — Se possui registro MX
  dnsMxRecords        List<String>   — Servidores de e-mail
  dnsARecords         List<String>   — Endereços IPv4
  dnsAaaaRecords      List<String>   — Endereços IPv6
  dnsCnameRecords     List<String>   — Alias de domínio
  dnsTxtRecords       List<String>   — SPF, DKIM, DMARC
  status              String         — ACTIVE | DELETED
  technologies        List<String>   — Tecnologias detectadas
  socialLinks         List<String>   — URLs de redes sociais
  socialProfileSummaries List<String> — Resumo dos perfis sociais
  exposedEmails       List<String>   — E-mails expostos
  nameMentions        List<String>   — Menções ao nome
  nameMentionUrls     List<String>   — URLs das menções
  dorkFindings        int            — Total de achados
  serperRawData       Object         — JSON bruto do OpenSERP (ou null)
  rdap                RdapData       — Dados de registro do domínio (ou null)
}
```

---

## LGPD & Segurança

- **E-mails criptografados em repouso** — AES-128-GCM com IV aleatório via `EncryptionService` + `EncryptedEmailConverter`
- **E-mails mascarados** em logs e respostas da API (`joa***@exemplo.com`)
- **Soft delete** — registros marcados como `DELETED`, não removidos fisicamente
- **Consentimento** — campos `consentGiven` e `consentDate` em cada lead
- **Retenção de dados** — campo `dataRetentionUntil` (365 dias a partir da criação)
- **Proteção de PII** — nenhum dado sensível aparece em logs ou respostas sem máscara

---

## Swagger / OpenAPI

Documentação interativa disponível em:

```
http://localhost:8081/swagger-ui.html
```

---

## Arquitetura do Código

```
src/main/java/solutions/pdroti/lead/enrichment/api/
├── LeadEnrichmentApplication.java   ← Entry point
├── config/
│   ├── ApiKeyFilter.java            ← Autenticação via header X-API-KEY
│   ├── EncryptedEmailConverter.java ← Criptografia JPA de e-mails
│   ├── EncryptionService.java       ← AES-128-GCM
│   ├── GlobalExceptionHandler.java  ← Tratamento global de erros
│   └── OpenApiConfig.java           ← Configuração Swagger/OpenAPI
├── controller/
│   └── LeadController.java          ← REST endpoints
├── dto/
│   ├── DnsResult.java               ← Resultado da consulta DNS
│   ├── LeadRequest.java             ← Payload de requisição
│   ├── LeadResponse.java            ← Payload de resposta
│   ├── RdapData.java                ← Dados de registro RDAP
│   ├── ScrapedPageData.java         ← Dados de scraping de página
│   └── SocialProfileData.java       ← Dados de perfil social
├── model/
│   └── Lead.java                    ← Entidade JPA
├── repository/
│   └── LeadRepository.java          ← Acesso a dados
├── service/
│   ├── DnsValidationService.java    ← Consultas DNS (MX, A, AAAA, CNAME, TXT)
│   ├── LeadService.java             ← Orquestração do enrichment
│   ├── OpenSerpSearch.java          ← Cliente HTTP OpenSERP (Google Search)
│   ├── RdapService.java             ← Consulta RDAP (Identity Digital + Registro.br)
│   ├── SocialDiscoveryService.java  ← Descoberta e scraping de redes sociais
│   └── TechScraperService.java      ← Scraping de tecnologias e metadados
└── util/
    └── EmailUtils.java              ← Máscara de e-mail (LGPD)
```

## Health Check

```
http://localhost:8081/actuator/health
```
echo "API_KEY=minha-chave" >> .env
docker compose up --build

# Inline
PG_PASSWORD=segura API_KEY=secreta docker compose up --build

# Portas customizadas
PG_PORT=5434 REDIS_PORT=6380 PORT=9090 docker compose up --build
```

### Build da imagem manualmente

```bash
docker build -t lead-enrichment-api .
docker run -p 8081:8081 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5433/postgres \
  -e REDIS_HOST=host.docker.internal \
  lead-enrichment-api
```

---

## Endpoints

### `POST /api/v1/leads/enrich`

Enriquece um lead: valida DNS, descobre tecnologias, redes sociais, metadados da página e varre informações expostas (Google Dorks).

**Request:**
```json
{
  "email": "contato@exemplo.com",
  "domain": "exemplo.com"
}
```

**Response (200):**
```json
{
  "id": 1,
  "emailMasked": "con***@exemplo.com",
  "domain": "exemplo.com",
  "mxStatus": true,
  "status": "ACTIVE",
  "technologies": ["WordPress", "Google Analytics", "React", "Cloudflare"],
  "socialLinks": ["https://facebook.com/exemplo", "https://linkedin.com/company/exemplo"],
  "exposedEmails": ["admin@exemplo.com", "contato@exemplo.com"],
  "exposedPhones": ["+55 11 99999-8888"],
  "exposedAdminPaths": ["/wp-admin", "/admin"],
  "exposedDocuments": ["/docs/relatorio.pdf"],
  "exposedConfigFiles": [".env"],
  "dorkFindings": 5
}
```

### `GET /api/v1/leads`

Lista todos os leads enriquecidos cadastrados.

### `GET /api/v1/leads/{id}`

Recupera um lead específico pelo ID numérico (passado como string).

### `DELETE /api/v1/leads/{id}`

Soft delete do lead (marca `deletedAt` + status `DELETED`) — LGPD direito ao esquecimento.

### `DELETE /api/v1/leads/{id}/hard`

Hard delete físico do banco de dados e cache.

### `GET /actuator/health`

Health check da aplicação.

---

## Scraping & Detecção

### Tecnologias detectadas (~65+)

| Categoria | Exemplos |
|---|---|
| **CMS** | WordPress, Drupal, Joomla, Magento, Shopify, Wix, Squarespace |
| **JS Frameworks** | React, Vue.js, Angular, Next.js, Nuxt.js, Gatsby, Svelte, Alpine.js |
| **Analytics** | Google Analytics, GA4, GTM, Facebook Pixel, Hotjar, HubSpot, TikTok Pixel |
| **Infra** | Cloudflare, CloudFront, Fastly, Akamai |
| **Pagamento** | Stripe, PayPal, Mercado Pago |
| **CSS/UI** | Bootstrap, Tailwind, Materialize, Font Awesome |
| **Meta tags** | Open Graph, Twitter Cards, Facebook App, CSRF Protection |

### Google Dorks — Info exposta detectada

| Info | Padrões buscados |
|---|---|
| **E-mails** | Regex de e-mails no HTML |
| **Telefones** | Padrões Nacionais e Internacionais |
| **Admin paths** | `/admin`, `/wp-admin`, `/login`, `/dashboard` |
| **Documentos** | `.pdf`, `.docx`, `.xlsx`, `.csv`, `.txt`, `.json` |
| **Config files** | `.env`, `.sql`, `.bak`, `.old`, `.swp` |
| **Backups** | `.zip`, `.tar.gz`, `-backup`, `.dump.sql` |
| **Logs** | `error_log`, `laravel.log`, `debug.log` |
| **Erros** | Stack traces, warnings, erros SQL expostos |
| **DB info** | `db_host`, `db_password`, `mysql`, `postgresql` |

### Dados de página extraídos

| Campo | Fonte |
|---|---|
| `title` | `<title>` |
| `description` | `<meta name="description">` |
| `language` | `<html lang="...">` |
| `favicon` | `<link rel="icon">` |
| `canonicalUrl` | `<link rel="canonical">` |
| `themeColor` | `<meta name="theme-color">` |
| `charset` | `<meta charset>` |
| `openGraph` | Todas as `<meta property="og:*">` |
| `twitterCards` | Todas as `<meta name="twitter:*">` |

---

## Arquitetura do Projeto

```
src/main/java/solutions/pdroti/lead/enrichment/api/
├── LeadEnrichmentApplication.java    # Entry point
├── config/                            # Segurança, Redis, OpenAPI, JPA converters
├── controller/                        # REST endpoints
├── dto/                               # Request/Response records
├── model/                             # Entidade JPA (Lead)
├── repository/                        # Spring Data JPA repositories
├── service/                           # Lógica de negócio
│   ├── LeadService.java              # Orquestrador de enrichment
│   ├── TechScraperService.java       # Scraping + detecção + Google Dorks
│   ├── DnsValidationService.java      # Consulta MX
│   ├── SocialDiscoveryService.java    # Links sociais
│   └── RedisCacheService.java        # Cache operations
└── util/                              # Utilitários (EmailUtils)
```

## Segurança

- **Autenticação:** Header `X-API-KEY` obrigatório (exceto Swagger e Actuator)
- **Criptografia:** E-mail criptografado em repouso (AES-128-GCM com IV aleatório)
- **Logs:** PII mascarada via `EmailUtils` (`con***@exemplo.com`)
- **LGPD:** Consentimento, retenção de 365 dias, direito ao esquecimento

## LGPD — Conformidade

- ✅ **Privacy by Design** — Criptografia de PII na camada JPA (`EncryptedEmailConverter`)
- ✅ **Consentimento** — `consentGiven`, `consentDate`, `dataRetentionUntil`
- ✅ **Direito ao esquecimento** — `DELETE` com soft delete (`deletedAt`, status `DELETED`)
- ✅ **Hard delete** — `DELETE /{id}/hard` para remoção física
- ✅ **Minimização** — Apenas dados públicos do domínio são coletados
- ✅ **Logs seguros** — E-mail mascarado em todos os logs e responses

## Decisões Arquiteturais (ADRs)

### ADR-001: Cache-Aside com Redis
Redis como cache distribuído com TTL de 24h. Estratégia Cache-Aside reduz carga no PostgreSQL 
e acelera consultas repetidas de leads já enriquecidos.

### ADR-002: Soft Delete para LGPD
Exclusão lógica (`deletedAt` + status `DELETED`) em vez de remoção física, 
permitindo auditoria e período de retenção conforme LGPD.

### ADR-003: Criptografia de PII com AES-GCM
E-mail é criptografado via `AttributeConverter` JPA antes de persistir. 
Usa AES-128-GCM com IV aleatório de 12 bytes para garantir confidencialidade 
e integridade dos dados sensíveis.

### ADR-004: Enum ScrapeError com ErrorMatcher funcional
Os erros de scraping são classificados por um `Enum` que carrega lambdas 
(`ErrorMatcher`) — cada constante sabe se identificar sem if-else externos, 
seguindo o princípio Open/Closed.

---

> **Nota:** Spring Batch foi removido — o processamento é feito sob demanda via REST.
