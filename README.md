# Lead Enrichment API

API para enriquecimento de leads com dados públicos.  
A partir de um **nome** (obrigatório), e opcionalmente um e-mail e domínio, a API descobre:

- Tecnologias usadas no site do domínio (CMS, frameworks, analytics, CDN)
- Perfis em redes sociais (GitHub, LinkedIn, Instagram, etc.)
- Dados extraídos dos perfis sociais (título, descrição)
- Informações de segurança expostas via Google Dorks (e-mails, telefones, documentos, configs)
- Validação de registro MX (DNS)
- Busca pelo nome no DuckDuckGo quando nenhum domínio é informado

Tudo com conformidade LGPD (e-mails criptografados e mascarados, soft-delete).

---

## Stack

| Tecnologia | Função |
|---|---|
| **Java 17** | Runtime |
| **Spring Boot 3.3.x** | Framework web / JPA / Actuator |
| **PostgreSQL 16** | Banco de dados relacional |
| **Hibernate 6.x** | ORM |
| **Jsoup** | Scraping HTML (sites e DuckDuckGo) |
| **dnsjava** | Consultas DNS (registro MX) |
| **SpringDoc OpenAPI** | Swagger UI em `/swagger-ui.html` |
| **Spring Actuator** | Health check em `/actuator/health` |
| **Lombok** | Redução de boilerplate |

---

## Requisitos

- JDK 17+
- Docker + Docker Compose
- Maven 3.8+

---

## Variáveis de Ambiente

| Variável | Descrição | Default |
|---|---|---|
| `DB_URL` | URL do PostgreSQL | `jdbc:postgresql://localhost:5433/postgres` |
| `DB_USERNAME` | Usuário do DB | `postgres` |
| `DB_PASSWORD` | Senha do DB | `pgsqldev` |
| `API_KEY` | Chave de API (header `X-API-KEY`) | `b6vxAgj5KG5HPGCKlQQ7` |
| `ENCRYPTION_SECRET` | Chave AES para criptografia de e-mails | `f44sGktPn25aHIuTfi9KbIwNnh8qO0xdbn+KmwwePz8=` |
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

```
X-API-KEY: b6vxAgj5KG5HPGCKlQQ7
```

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
| `email` | Não | E-mail do lead (usado para dedup e identificação) |
| `domain` | Não | Domínio para scraping (DNS, tecnologias, redes sociais, Dorks) |

**Comportamento por cenário:**

| Cenário | O que acontece |
|---|---|
| Só `name` | Busca o nome no DuckDuckGo — encontra e-mails, redes sociais e perfis |
| `name` + `domain` | Scraping no domínio: tecnologias, redes sociais, Dorks. Só persiste se o **nome completo** for encontrado no HTML do site |
| `name` + `email` | Usa o e-mail para buscar lead existente e reenriquecer |
| Tudo preenchido | Fluxo completo: dedup por e-mail + scraping no domínio |

**Resposta (200 OK):**

```json
{
  "id": 1,
  "emailMasked": "joa***@exemplo.com",
  "name": "João Silva",
  "domain": "exemplo.com",
  "mxStatus": true,
  "status": "ACTIVE",
  "technologies": ["WordPress", "jQuery", "Google Analytics"],
  "socialLinks": ["https://github.com/joaosilva", "https://linkedin.com/in/joaosilva"],
  "socialProfileSummaries": [
    "GitHub: joaosilva (João Silva) — Desenvolvedor full-stack",
    "LinkedIn: João Silva — Software Engineer na Empresa X"
  ],
  "exposedEmails": ["contato@exemplo.com"],
  "exposedPhones": [],
  "exposedAdminPaths": ["/wp-admin"],
  "exposedDocuments": [],
  "exposedConfigFiles": [],
  "nameMentions": ["Nome completo encontrado: João Silva"],
  "dorkFindings": 8
}
```

**Erro (400 Bad Request)** — nome não encontrado no domínio:

```json
{
  "error": "Bad Request",
  "message": "Nome \"João Silva\" não encontrado no domínio exemplo.com"
}
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

### `DELETE /api/v1/leads/{id}` — Soft delete (LGPD)

Marca o lead como `DELETED` (direito ao esquecimento). O registro permanece no banco para auditoria, mas não aparece nas consultas.

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

1. **Validação DNS** — verifica se o domínio tem registro MX
2. **Scraping de tecnologias** — detecta CMS, frameworks, analytics, CDN, e-commerce, etc. (~60 assinaturas)
3. **Descoberta de redes sociais** — encontra links para GitHub, LinkedIn, Instagram, YouTube, etc.
4. **Scraping de perfis sociais** — acessa cada perfil encontrado e extrai título e descrição
5. **Google Dorks** — varre o HTML do site em busca de:
   - E-mails e telefones expostos
   - Caminhos administrativos (`/admin`, `/wp-admin`)
   - Documentos públicos (`.pdf`, `.docx`, `.xlsx`)
   - Arquivos de configuração (`.env`, `.sql`, `.bak`)
   - Menções ao nome completo da pessoa

### Sem domínio informado

Busca o nome no **DuckDuckGo** e extrai:
- E-mails encontrados nos snippets de resultado
- Links de redes sociais
- Dados dos perfis sociais (via scraping)
- Menções ao nome completo

---

## LGPD & Segurança

- **E-mails criptografados** no banco (AES) via `EncryptedEmailConverter`
- **E-mails mascarados** na resposta da API (`joa***@exemplo.com`)
- **Soft delete** — registros marcados como `DELETED` não são removidos fisicamente
- **Consentimento** — campo `consentGiven` e `consentDate` em cada lead
- **Retenção de dados** — campo `dataRetentionUntil` (365 dias)

---

## Swagger / OpenAPI

Documentação interativa disponível em:

```
http://localhost:8081/swagger-ui.html
```

---

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
