# Guia de Implantação — Lead Enrichment API

## Pré-requisitos

- **JDK 21+** — Compilação e execução local (Virtual Threads)
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
| `redis` | redis:latest | 6379 | Cache L2 distribuído (Redis) com persistência AOF |
| `openserp` | karust/openserp:latest | 7000 | Google Search API (instância 1) |
| `openserp2` | karust/openserp:latest | 7002 | Google Search API (instância 2) |
| `openserp3` | karust/openserp:latest | 7003 | Google Search API (instância 3) |
| `jaeger` | jaegertracing/all-in-one:1.39 | 4317, 4318, 16686 | Tracing distribuído (OTLP + UI) |
| `app` | build local | 8781 | Lead Enrichment API |

### Rede

Todos os serviços compartilham a rede `lead-enrichment-api-network` (bridge).

> **Redis:** Opcional. Se `REDIS_HOST` não for configurado, a aplicação opera apenas com cache local Caffeine (L1). O Redis (L2) é ativado automaticamente quando o host é fornecido, fornecendo cache distribuído entre instâncias.

---

## Execução Local (sem Docker)

### 1. Iniciar PostgreSQL

Certifique-se de que o PostgreSQL 16 está rodando na porta `5433`.

### 2. (Opcional) Iniciar OpenSERP

```bash
docker run -d --name openserp -p 7000:7000 karust/openserp:latest serve -a 0.0.0.0 -p 7000
```

### 3. Configurar variáveis de ambiente

```bash
cp .env.example .env
# Edite .env com suas credenciais reais
```

### 4. Executar a aplicação

```bash
# Opção A — Script build-jdk21.bat (JDK 21 + .env automático)
build-jdk21.bat spring-boot:run -Dmaven.test.skip=true

# Opção B — Script run.bat (fallback JDK 17)
run.bat

# Opção C — Ctrl+Shift+B no VS Code (usa a task configurada)

# Opção D — Maven direto (exige variáveis exportadas)
set API_KEY=minha-chave
set ENCRYPTION_SECRET=minha-chave-aes-16bytes
mvn spring-boot:run -Dmaven.test.skip=true
```

---

## Variáveis de Ambiente

Todas as configurações sensíveis são lidas do arquivo `.env` ou de variáveis de ambiente:

| Variável | Obrigatória | Descrição | Exemplo |
|---|---|---|---|
| `DB_URL` | ✅ | URL do PostgreSQL | `jdbc:postgresql://host:5432/postgres` |
| `DB_USERNAME` | ✅ | Usuário do banco | `postgres` |
| `DB_PASSWORD` | ✅ | Senha do banco | — |
| `API_KEY` | ✅ | Chave de autenticação da API | — |
| `ENCRYPTION_SECRET` | ✅ | Secret AES-128-GCM (mín. 16 bytes) | — |
| `OPENSERP_API_URL` | ✅ | URL do OpenSERP self-hosted | `http://localhost:7000` |
| `OPENSERP_API_URL_2` | ❌ | URL do OpenSERP 2 | `http://localhost:7002` |
| `OPENSERP_API_URL_3` | ❌ | URL do OpenSERP 3 | `http://localhost:7003` |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | ❌ | Endpoint OTLP para Jaeger | `http://localhost:4318/v1/traces` |
| `REDIS_HOST` | ❌ | Host do Redis (se vazio, cache L2 desabilitado) | — |
| `REDIS_PASSWORD` | ❌ | Senha do Redis | — |
| `PORT` | ❌ | Porta da aplicação (default: 8081) | `8081` |

> ⚠️ **Nunca** commite o `.env` — ele já está no `.gitignore`.

## Build da Imagem Docker

### Build manual

```bash
docker build -t lead-enrichment-api:latest .
```

O Dockerfile usa **multi-stage build**:

| Estágio | Imagem Base | Função |
|---|---|---|
| `builder` | maven:3.9-eclipse-temurin-21 | Compilação do JAR |
| `runtime` | eclipse-temurin:21-jre-alpine | Execução (imagem leve ~200MB) |

### Executar container manualmente

```bash
docker run -d \
  --name lead-enrichment-api \
  -p 8081:8081 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5433/postgres \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=pgsqldev \
  -e API_KEY=sua-chave-aqui \
  -e ENCRYPTION_SECRET=sua-chave-aes-aqui \
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

---

## Deploy em Produção

### 1. Configurar variáveis de ambiente

```bash
export DB_URL=jdbc:postgresql://<host-producao>:5432/leads
export DB_USERNAME=<usuario-prod>
export DB_PASSWORD=<senha-forte>
export API_KEY=<chave-api-forte>
export ENCRYPTION_SECRET=<chave-aes-32bytes-base64>
export OPENSERP_API_URL=http://<openserp-host>:7000
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

Atualmente a API utiliza **hard delete** (exclusão física) via `LeadDeletionService.hardDelete()`. Registros removidos não ocupam espaço e não requerem expurgo futuro.

> Consulte o [ADR-004](./adr/ADR-004-soft-delete-lgpd.md) para detalhes sobre a estratégia de exclusão.
