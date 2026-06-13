# Guia da API

## Base URL

| Ambiente | URL |
|---|---|
| **Produção** | `https://api-lead-enrichment.pdroti.solutions` |
| **Desenvolvimento** | `http://localhost:8081` |
| **Swagger UI** | `http://localhost:8081/swagger-ui.html` |
| **OpenAPI Spec** | `http://localhost:8081/v3/api-docs` |

## Autenticação

Todas as requisições (exceto actuator e Swagger) exigem o header:

```
X-API-KEY: <sua-chave>
```

**Endpoints públicos** (não exigem chave):
- `/actuator/**` — Health checks, métricas
- `/swagger-ui/**` — Swagger UI
- `/v3/api-docs/**` — OpenAPI spec

### Resposta de erro de autenticação

```json
{
  "error": "Unauthorized",
  "message": "Invalid or missing API key"
}
```

## Endpoints

### 1. Enriquecer Lead

```
POST /api/v1/leads/enrich
```

Enriquece um lead com dados públicos do domínio (DNS, RDAP, tecnologias, redes sociais) ou via OpenSERP.

#### Request Body

```json
{
  "email": "contato@exemplo.com",
  "domain": "exemplo.com",
  "name": "João Silva"
}
```

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `email` | string | ✅ | Email do lead (identificador único) |
| `domain` | string | ❌ | Domínio para enriquecimento (extraído do email se ausente) |
| `name` | string | ✅ | Nome da pessoa |

#### Response (200 OK)

Os campos são organizados em **sub-records** para melhor legibilidade: `dns`, `discovery` e `rdap`.

```json
[
  {
    "id": 1,
    "emailMasked": "con***@exemplo.com",
    "name": "João Silva",
    "domain": "exemplo.com",
    "status": "ENRICHED",
    "dns": {
      "mxStatus": true,
      "mxRecords": ["10 mail.exemplo.com."],
      "aRecords": ["192.168.1.1"],
      "aaaaRecords": ["2001:db8::1"],
      "cnameRecords": [],
      "txtRecords": ["v=spf1 include:_spf.google.com ~all"]
    },
    "discovery": {
      "technologies": ["WordPress", "jQuery", "Cloudflare"],
      "socialLinks": [
        "https://linkedin.com/in/joaosilva",
        "https://github.com/joaosilva"
      ],
      "socialProfileSummaries": [
        "LinkedIn: Software Engineer na Empresa X"
      ],
      "exposedEmails": ["joao@exemplo.com"],
      "nameMentions": ["Nome completo encontrado em: https://exemplo.com"],
      "nameMentionUrls": ["https://exemplo.com"],
      "dorkFindings": 5,
      "foundDocuments": ["https://exemplo.com/curriculo.pdf"],
      "discoveredUrls": ["https://exemplo.com", "https://github.com/joaosilva"],
      "serperRawData": {
        "query": "João Silva",
        "totalResults": 15,
        "items": [{
          "title": "João Silva - LinkedIn",
          "url": "https://linkedin.com/in/joaosilva",
          "snippet": "Software Engineer...",
          "domain": "linkedin.com"
        }]
      }
    },
    "rdap": {
      "registrar": "HOSTINGER operations, UAB",
      "registrantName": "João Silva",
      "registrationDate": "2020-01-15T00:00:00Z",
      "expirationDate": "2027-01-15T00:00:00Z",
      "nameservers": ["ns1.exemplo.com"],
      "status": ["client transfer prohibited"],
      "taxpayerId": null,
      "source": "identitydigital"
    }
  }
]
```

> **Nota:** O endpoint retorna **todos os leads do mesmo domínio**, não apenas o recém-enriquecido.

---

### 2. Listar Todos os Leads

```
GET /api/v1/leads
```

Retorna todos os leads ativos (exclui soft-deleted).

#### Response (200 OK)

```json
[
  {
    "id": 1,
    "emailMasked": "con***@exemplo.com",
    "name": "João Silva",
    "domain": "exemplo.com",
    "status": "ENRICHED"
  }
]
```

---

### 3. Buscar Lead por ID

```
GET /api/v1/leads/{id}
```

#### Response (200 OK)

Retorna o lead completo (mesmo formato do enrich).

#### Response (404 Not Found)

```json
{
  "error": "Lead não encontrado",
  "id": "999"
}
```

---

### 4. Buscar Leads por Domínio

```
GET /api/v1/leads/domain/{domain}
```

#### Response (200 OK)

```json
[
  {
    "id": 1,
    "emailMasked": "con***@exemplo.com",
    "name": "João Silva",
    "domain": "exemplo.com",
    "status": "ENRICHED"
  }
]
```

#### Response (204 No Content)

Retornado quando nenhum lead é encontrado para o domínio.

---

### 5. Atualizar Lead

```
PUT /api/v1/leads/{id}
```

Atualiza os dados de um lead existente e reenriquece.

#### Request Body

```json
{
  "email": "novo@exemplo.com",
  "domain": "exemplo.com",
  "name": "João Silva Atualizado"
}
```

#### Response (200 OK)

Mesmo formato do enrich, retornando apenas o lead atualizado.

---

### 6. Excluir Lead (Soft Delete — LGPD)

```
DELETE /api/v1/leads/{id}
```

#### Response (200 OK)

```json
{
  "message": "Lead excluído com sucesso (LGPD — direito ao esquecimento)",
  "id": "1"
}
```

#### Response (404 Not Found)

```json
{
  "error": "Lead não encontrado",
  "id": "999"
}
```

---

## Tratamento de Erros

Todos os erros seguem o formato padronizado:

```json
{
  "error": "Tipo do erro",
  "message": "Descrição do erro",
  "timestamp": "2026-06-10T12:00:00"
}
```

### Códigos HTTP

| Código | Significado |
|---|---|
| `200` | Sucesso |
| `204` | Sem conteúdo |
| `400` | Erro de validação ou argumento inválido |
| `401` | API Key ausente ou inválida |
| `404` | Lead não encontrado |
| `500` | Erro interno do servidor |

### Exemplo: Erro de Validação (400)

```json
{
  "error": "Validation Error",
  "details": [
    "name: O nome é obrigatório",
    "email: O email deve ser válido"
  ],
  "timestamp": "2026-06-10T12:00:00"
}
```

---

## Health Check

```
GET /actuator/health
```

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": {
      "status": "UP"
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

---

## Modelo de Dados

### Estrutura da Resposta (`LeadResponse`)

```
LeadResponse
├── id: Long
├── emailMasked: String (mascarado LGPD)
├── name: String
├── domain: String
├── status: String (ACTIVE | DELETED)
├── dns: DnsRecords          ← sub-record
│   ├── mxStatus: boolean
│   ├── mxRecords: List<String>
│   ├── aRecords: List<String>
│   ├── aaaaRecords: List<String>
│   ├── cnameRecords: List<String>
│   └── txtRecords: List<String>
├── discovery: DiscoveryData  ← sub-record
│   ├── technologies: List<String>
│   ├── socialLinks: List<String>
│   ├── socialProfileSummaries: List<String>
│   ├── exposedEmails: List<String>
│   ├── nameMentions: List<String>
│   ├── nameMentionUrls: List<String>
│   ├── dorkFindings: int
│   ├── foundDocuments: List<String>
│   ├── discoveredUrls: List<String>
│   └── serperRawData: SerpSearchResult (ou null)
└── rdap: RdapData            ← sub-record
    ├── rawJson: JsonNode
    ├── registrar: String
    ├── registrantName: String
    ├── registrantEmail: String
    ├── registrationDate: String
    ├── expirationDate: String
    ├── nameservers: List<String>
    ├── status: List<String>
    ├── taxpayerId: String
    └── source: String
```

### Entidade `Lead` (banco de dados)

| Campo | Tipo | Descrição |
|---|---|---|
| `id` | Long (PK) | ID gerado automaticamente |
| `email` | String (criptografado AES-GCM) | Email do lead |
| `emailHash` | String (SHA-256, unique) | Hash para consulta |
| `name` | String | Nome da pessoa |
| `domain` | String | Domínio enriquecido |
| `mxStatus` | boolean | Se possui registro MX |
| `status` | String | ACTIVE ou DELETED |
| `dnsMxRecords` | `@ElementCollection` | Registros MX |
| `dnsARecords` | `@ElementCollection` | Registros A (IPv4) |
| `dnsAaaaRecords` | `@ElementCollection` | Registros AAAA (IPv6) |
| `dnsCnameRecords` | `@ElementCollection` | Registros CNAME |
| `dnsTxtRecords` | `@ElementCollection` | Registros TXT |
| `technologies` | `@ElementCollection` | Tecnologias detectadas |
| `socialLinks` | `@ElementCollection` | Links de redes sociais |
| `socialProfileSummaries` | `@ElementCollection` | Resumo dos perfis sociais |
| `exposedEmails` | `@ElementCollection` | E-mails expostos |
| `dorkFindings` | int | Total de achados |
| `nameMentions` | `@ElementCollection` | Menções ao nome |
| `createdAt` | LocalDateTime | Data de criação |
| `updatedAt` | LocalDateTime | Data de atualização |
| `deletedAt` | LocalDateTime | Data de exclusão (soft delete) |

> Todos os campos de lista usam `FetchType.LAZY` para performance.
