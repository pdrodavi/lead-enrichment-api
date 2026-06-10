# Guia de Deploy

## Pré-requisitos

- **JDK 17+** — Compilação e execução local
- **Maven 3.8+** — Gerenciamento de dependências
- **Docker + Docker Compose** — Containerização e orquestração
- **PostgreSQL 16** — Banco de dados (apenas para execução local sem Docker)
- **OpenSERP** — Self-hosted Google Search API (opcional para modo offline)

---

## Execução com Docker Compose (Recomendado)

### Subir o ambiente completo

```bash
# Build + Start (modo attached)
docker compose up --build

# Modo detached (background)
docker compose up --build -d

# Acompanhar logs da aplicação
docker compose logs -f app
```

### Parar o ambiente

```bash
# Parar tudo (mantém volumes)
docker compose down

# Parar tudo e remover dados do banco
docker compose down -v
```

### Serviços do Docker Compose

| Serviço | Imagem | Porta | Descrição |
|---|---|---|---|
| `postgres` | postgres:16 | 5433 | Banco de dados relacional |
| `openserp` | karust/openserp:latest | 7000 | Google Search API self-hosted |
| `app` | build local | 8781 | Lead Enrichment API |

### Rede

Todos os serviços compartilham a rede `lead-enrichment-api-network` (bridge).

---

## Execução Local (sem Docker)

### 1. Iniciar PostgreSQL

Certifique-se de que o PostgreSQL 16 está rodando na porta `5433`.

### 2. (Opcional) Iniciar OpenSERP

```bash
docker run -d --name openserp -p 7000:7000 karust/openserp:latest serve -a 0.0.0.0 -p 7000
```

### 3. Executar a aplicação

```bash
# Com Maven
mvn spring-boot:run -Dmaven.test.skip=true

# Com variáveis customizadas
API_KEY=minha-chave ENCRYPTION_SECRET=minha-chave-aes-16bytes mvn spring-boot:run
```

---

## Build da Imagem Docker

### Build manual

```bash
docker build -t lead-enrichment-api:latest .
```

O Dockerfile usa **multi-stage build**:

| Estágio | Imagem Base | Função |
|---|---|---|
| `builder` | maven:3.9-eclipse-temurin-17 | Compilação do JAR |
| `runtime` | eclipse-temurin:17-jre-alpine | Execução (imagem leve ~200MB) |

### Executar container manualmente

```bash
docker run -d \
  --name lead-enrichment-api \
  -p 8081:8081 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5433/postgres \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=pgsqldev \
  -e API_KEY=b6vxAgj5KG5HPGCKlQQ7 \
  -e ENCRYPTION_SECRET=f44sGktPn25aHIuTfi9KbIwNnh8qO0xdbn+KmwwePz8= \
  lead-enrichment-api:latest
```

---

## Configuração Externalizada

Após as refatorações, várias configurações foram externalizadas para o `application.yml` e podem ser customizadas sem recompilar o código.

### Tecnologias e Redes Sociais

```yaml
techscraper:
  signatures:          # ~65 assinaturas de tecnologia
    WordPress: ["wp-content", "wp-includes"]
    React: ["react.js", "_next/static"]
    # ...
  script-detectors:    # ~20 detectores de scripts
    "Facebook Pixel": ["facebook", "fbq"]
    # ...
  meta-generators:     # ~30 geradores de meta tags
    wordpress: "WordPress"
    # ...

social-discovery:
  social-domains:      # 31 domínios de redes sociais
    - facebook.com
    - linkedin.com
    # ...
  platform-names:      # 30 nomes de plataforma
    github.com: "GitHub"
    linkedin.com: "LinkedIn"
    # ...
```

## Variáveis de Ambiente

| Variável | Descrição | Padrão | Obrigatória |
|---|---|---|---|
| `DB_URL` | URL de conexão JDBC do PostgreSQL | `jdbc:postgresql://localhost:5433/postgres` | Sim |
| `DB_USERNAME` | Usuário do banco | `postgres` | Sim |
| `DB_PASSWORD` | Senha do banco | `pgsqldev` | Sim |
| `API_KEY` | Chave para autenticação via header `X-API-KEY` | `b6vxAgj5KG5HPGCKlQQ7` | Sim |
| `ENCRYPTION_SECRET` | Chave AES-128 para criptografia de e-mails (mín. 16 bytes) | `f44sGktPn25aHIuTfi9KbIwNnh8qO0xdbn+KmwwePz8=` | Sim |
| `SERPER_API_URL` | URL base da API OpenSERP | `http://localhost:7000` | Sim (se usar OpenSERP) |
| `PORT` | Porta do servidor HTTP | `8081` | Sim |
| `ENV` | Sufixo de ambiente para nomes de container | `dev` | Opcional |

### Variáveis específicas do Docker Compose

| Variável | Descrição | Padrão |
|---|---|---|
| `PG_USER` | Usuário PostgreSQL (Docker) | `postgres` |
| `PG_PASSWORD` | Senha PostgreSQL (Docker) | `pgsqldev` |
| `PG_DB` | Nome do banco (Docker) | `postgres` |
| `PG_PORT` | Porta exposta do PostgreSQL | `5433` |

---

## Deploy em Produção

### 1. Configurar variáveis de ambiente

```bash
export DB_URL=jdbc:postgresql://<host-producao>:5432/leads
export DB_USERNAME=<usuario-prod>
export DB_PASSWORD=<senha-forte>
export API_KEY=<chave-api-forte>
export ENCRYPTION_SECRET=<chave-aes-32bytes-base64>
export SERPER_API_URL=http://<openserp-host>:7000
export PORT=8081
```

> **⚠️ Importante:** Em produção, jamais use os valores padrão. Utilize um gerenciador de segredos (Azure Key Vault, AWS Secrets Manager, HashiCorp Vault).

### 2. Health Check

A API expõe endpoints do Spring Actuator:

```bash
# Health check básico
curl http://localhost:8081/actuator/health

# Probes de Kubernetes (liveness + readiness)
curl http://localhost:8081/actuator/health/liveness
curl http://localhost:8081/actuator/health/readiness

# Métricas
curl http://localhost:8081/actuator/metrics
```

### 3. Logs

Formato de log configurado no `application.yml`:

```
2026-06-10 12:00:00 [http-nio-8081-exec-1] INFO  s.p.l.e.api.service.LeadService - Enriquecendo lead: nome=João email=jo***@exemplo.com domain=exemplo.com
```

---

## Troubleshooting

### "API Key ausente ou inválida"

- Verifique se o header `X-API-KEY` está sendo enviado
- Verifique se a variável `API_KEY` está configurada corretamente

### "Connection to localhost:5433 refused"

- PostgreSQL não está rodando
- Verifique se a porta `5433` está correta
- No Docker Compose, o serviço `postgres` pode estar iniciando ainda

### OpenSERP retornando vazio

- Verifique se o OpenSERP está rodando em `http://localhost:7000`
- Verifique os logs do OpenSERP para erros de consulta

---

## Manutenção

### Backup do Banco

```bash
docker exec postgres-dev pg_dump -U postgres postgres > backup-leads.sql
```

### Expurgo de Dados (LGPD)

O sistema mantém registros soft-deleted por 365 dias. Um job futuro deverá expurgar fisicamente registros com `deletedAt` superior a 365 dias.
