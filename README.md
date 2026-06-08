# Lead Enrichment API

API inteligente para enriquecimento de leads B2B.  
Dado um e-mail e domínio, a API descobre dados públicos como tecnologias do site, redes sociais, 
metadados da página e informações de segurança expostas (Google Dorks) — tudo com conformidade LGPD.

---

## Stack

| Tecnologia | Versão | Função |
|---|---|---|
| **Java** | 17 | Runtime |
| **Spring Boot** | 3.3.x | Framework web |
| **PostgreSQL** | — | Armazenamento principal |
| **Redis** | — | Cache distribuído (Cache-Aside, TTL 24h) |
| **Jsoup** | — | HTML parsing e web scraping |
| **dnsjava** | — | Consultas DNS (registros MX) |
| **Hibernate** | 6.x | JPA / ORM |
| **SpringDoc OpenAPI** | — | Swagger UI em `/swagger-ui.html` |
| **Spring Actuator** | — | Health check `/actuator/health` |

---

## Requisitos

- JDK 17+
- PostgreSQL (porta 5433)
- Redis (porta 6379)
- Maven 3.8+

## Configuração

| Variável | Descrição | Default (dev) |
|---|---|---|
| `DB_URL` | URL do PostgreSQL | `jdbc:postgresql://localhost:5433/postgres` |
| `DB_USERNAME` | Usuário do DB | `postgres` |
| `DB_PASSWORD` | Senha do DB | `pgsqldev` |
| `REDIS_HOST` | Host do Redis | `localhost` |
| `REDIS_PORT` | Porta do Redis | `6379` |
| `API_KEY` | Chave de API (header `X-API-KEY`) | `dev-key-change-in-production` |
| `ENCRYPTION_SECRET` | Chave AES-GCM para criptografia de PII | `CHANGE_ME_32_BYTE_SECRET_KEY!` |
| `PORT` | Porta do servidor | `8089` |

## Execução

### Local (sem Docker)

```bash
# Certifique-se de ter PostgreSQL e Redis rodando localmente
mvn spring-boot:run

# Ou com variáveis customizadas
DB_PASSWORD=... API_KEY=... ENCRYPTION_SECRET=... mvn spring-boot:run
```

### Docker Compose (recomendado)

Sobe todos os serviços (PostgreSQL 16, Redis 7, aplicação) com um comando:

```bash
# Build + Start
docker compose up --build

# Modo detached (background)
docker compose up --build -d

# Acompanhar logs
docker compose logs -f app

# Parar tudo
docker compose down

# Parar tudo e remover volumes (dados)
docker compose down -v
```

### Variáveis de ambiente no Docker

Todas as variáveis podem ser sobrescritas via `.env` ou inline:

```bash
# Usando .env file
echo "PG_PASSWORD=senha_segura" >> .env
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
