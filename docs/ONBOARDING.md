# Guia de Onboarding — Lead Enrichment API

> Este guia orienta a configuração do ambiente, entendimento do projeto e primeira contribuição. Tempo estimado: 10 a 15 minutos.

---

## Índice

1. [Primeiros Passos](#1-primeiros-passos)
2. [Checklist de Ambiente](#2-checklist-de-ambiente)
3. [Setup Passo a Passo](#3-setup-passo-a-passo)
4. [Entendendo o Projeto](#4-entendendo-o-projeto)
5. [Fluxo de Desenvolvimento](#5-fluxo-de-desenvolvimento)
6. [Testando a API](#6-testando-a-api)
7. [Depuração e Troubleshooting](#7-depuração-e-troubleshooting)
8. [Padrões e Convenções](#8-padrões-e-convenções)
9. [Comandos Úteis](#9-comandos-úteis)
10. [Glossário](#10-glossário)
11. [Onde Encontrar Ajuda](#11-onde-encontrar-ajuda)

---

## 1. Primeiros Passos

```bash
# 1. Clone o repositório
git clone <url-do-repositorio>
cd lead-enrichment-api

# 2. Configure as variáveis de ambiente
cp .env.example .env
# Edite .env com suas credenciais (veja seção 3.2)

# 3. Inicie os serviços com Docker
docker compose up --build -d

# 4. Teste se a aplicação subiu
curl -s http://localhost:8081/actuator/health | jq .
# Deve retornar: {"status":"UP"}

# 5. Faça sua primeira chamada à API
curl -H "X-API-KEY: $(grep API_KEY .env | cut -d= -f2)" \
  -H "Content-Type: application/json" \
  -X POST http://localhost:8081/api/v1/leads/enrich \
  -d '{"email":"contato@exemplo.com","name":"Maria Santos"}'
```



---

## 2. Checklist de Ambiente

### Obrigatório

| Requisito | Versão Mínima | Como Verificar |
|---|---|---|
| **JDK** | 21 (Temurin recomendado) | `java -version` |
| **Maven** | 3.8+ | `mvn -version` |
| **Docker** | 24+ | `docker --version` |
| **Docker Compose** | 2.20+ | `docker compose version` |
| **Git** | 2.30+ | `git --version` |

### Recomendado

| Ferramenta | Uso | Instalação |
|---|---|---|
| **VS Code** | IDE principal | [code.visualstudio.com](https://code.visualstudio.com) |
| **Extension Pack for Java** | Suporte Java no VS Code | Marketplace |
| **Spring Boot Extension Pack** | Suporte Spring Boot | Marketplace |
| **Mermaid Preview** | Visualizar diagramas | Marketplace |
| **Docker Extension** | Gerenciar containers | Marketplace |
| **Postman** ou **Insomnia** | Testar API | Sites oficiais |
| **jq** | Processar JSON no terminal | `winget install jq` |

---

## 3. Setup Passo a Passo

### 3.1 Verifique o JDK

A aplicação usa **Java 21** com Virtual Threads. Certifique-se de que o JDK 21 está instalado e configurado:

```bash
java -version
# Deve mostrar: openjdk version "21" ...
```

> **Windows:** O projeto inclui `build-jdk21.bat` que configura o PATH para um JDK 21 portable em `C:\openjdk-21_windows-x64_bin\jdk-21`. Ajuste o caminho se necessário.

### 3.2 Configure o `.env`

Copie o template e preencha com suas credenciais:

```bash
cp .env.example .env
```

Edite o arquivo `.env`:

```ini
# Credenciais do banco (valores default funcionam para Docker)
PG_USER=postgres
PG_PASSWORD=pgsqldev
PG_PORT=5433

# Chaves de segurança (troque para valores reais em produção)
API_KEY=minha-chave-temporaria
ENCRYPTION_SECRET=minha-chave-aes-16bytes!
```

> ⚠️ **Nunca** commite o `.env` — ele está no `.gitignore`.

### 3.3 Suba os Serviços com Docker

```bash
# Build + start em background
docker compose up --build -d

# Acompanhe os logs da aplicação
docker compose logs -f app

# Verifique se todos os serviços estão rodando
docker compose ps
```

**Serviços iniciados:**

| Container | Porta | Aguardar até |
|---|---|---|
| `postgres-dev` | 5433 | `pg_isready` |
| `openserp-dev` | 7000 | Log: "Listening on :7000" |
| `openserp-dev-2` | 7002 | Log: "Listening on :7000" |
| `openserp-dev-3` | 7003 | Log: "Listening on :7000" |
| `jaeger-dev` | 16686 | UI disponível |
| `lead-enrichment-api-dev` | 8081 | Health check UP |

### 3.4 Alternativa: Execução Local sem Docker

Se preferir executar localmente sem Docker:

```bash
# 1. Certifique-se de que o PostgreSQL 16 está rodando na porta 5433

# 2. Execute com o script (carrega .env automaticamente)
run.bat

# Ou via Maven direto (precisa exportar variáveis)
export DB_URL=jdbc:postgresql://localhost:5433/postgres
export DB_USERNAME=postgres
export DB_PASSWORD=pgsqldev
export API_KEY=minha-chave-temporaria
export ENCRYPTION_SECRET=minha-chave-aes-16bytes!
mvn spring-boot:run -Dmaven.test.skip=true
```

### 3.5 Verifique se está tudo funcionando

```bash
# Health check
curl http://localhost:8081/actuator/health
# → {"status":"UP"}

# Swagger UI (abra no navegador)
# http://localhost:8081/swagger-ui/index.html

# Jaeger UI
# http://localhost:16686
```

---

## 4. Entendendo o Projeto

### 4.1 Mapa Mental (60 segundos)

```
Lead Enrichment API
│
├── Cliente HTTP ──→ ApiKeyFilter ──→ LeadController
│                                             │
│                                    LeadService (orquestrador)
│                                      │              │
│                              OpenSerpEnricher  DomainEnricher
│                              (sempre executa)  (se houver domínio)
│                                      │              │
│                              ┌──── OpenSERP ──┐  ┌── DNS ──┐
│                              │   + Google      │  │ RDAP    │
│                              │   + Documentos  │  │ TechScra│
│                              └────────────────┘  │ Social  │
│                                                   └─────────┘
│                                            │
│                                     PostgreSQL (AES-GCM)
│                                            │
└── LeadResponse (com email mascarado) ←─────┘
```

### 4.2 Arquivos que Você Precisa Conhecer

| Arquivo | Por que é importante |
|---|---|
| `src/main/resources/application.yml` | Configuração central da aplicação |
| `pom.xml` | Dependências Maven |
| `docker-compose.yml` | Orquestração de serviços |
| `docs/TECHNICAL_REFERENCE.md` | Documentação técnica completa |
| `docs/api-guide.md` | Guia detalhado da API |
| `docs/security-lgpd.md` | Segurança e compliance |

### 4.3 Fluxo de uma Requisição Típica

```
1. Cliente envia POST /api/v1/leads/enrich com X-API-KEY
2. ApiKeyFilter valida a chave → 401 se inválida
3. LeadController valida o body (@Valid) → 400 se inválido
4. LeadService.enrich() é chamado:
   a. Extrai domínio do e-mail (se não informado)
   b. Gera SHA-256 hash do e-mail
   c. Busca lead existente por hash
   d. Limpa dados de enriquecimento anterior
   e. DISPARA EM PARALELO:
      - OpenSerpEnricher (sempre)
      - DomainEnricher (se houver domínio)
   f. Persiste no PostgreSQL (e-mail criptografado AES-GCM)
5. LeadController monta a resposta com email mascarado
6. Cliente recebe 200 OK + List<LeadResponse>
```

---

## 5. Fluxo de Desenvolvimento

### 5.1 Branch Strategy

```bash
main          # Produção — apenas merge via PR
├── develop   # Desenvolvimento — branch base para features
│   ├── feature/nova-funcionalidade
│   ├── fix/correcao-bug
│   └── refactor/melhoria
```

### 5.2 Ciclo de Desenvolvimento

```bash
# 1. Atualize a develop
git checkout develop
git pull

# 2. Crie sua branch
git checkout -b feature/minha-feature

# 3. Desenvolva e commit (commits pequenos e frequentes)
git add .
git commit -m "feat: adiciona nova funcionalidade"

# 4. Mantenha sua branch atualizada
git fetch origin
git rebase origin/develop

# 5. Abra um Pull Request
# → Descreva o que foi feito, por que e como testar

# 6. Após aprovação, faça squash merge
```

### 5.3 Commits Semânticos

| Tipo | Quando usar |
|---|---|
| `feat:` | Nova funcionalidade |
| `fix:` | Correção de bug |
| `refactor:` | Refatoração sem mudança de comportamento |
| `perf:` | Melhoria de performance |
| `docs:` | Documentação |
| `test:` | Testes |
| `chore:` | Build, CI, tarefas administrativas |

### 5.4 Antes de Abrir um PR

- [ ] Código compila sem erros (`mvn compile`)
- [ ] Testes passam (`mvn test`)
- [ ] Novos testes foram adicionados (se aplicável)
- [ ] Código segue os padrões do projeto (SRP, DRY, KISS)
- [ ] Documentação atualizada (se aplicável)
- [ `.env` não foi commitado
- [ ] Diagramas Mermaid atualizados (se aplicável)

---

## 6. Testando a API

### 6.1 Exemplos com curl

```bash
# Variável com a chave (para não repetir)
API_KEY=$(grep API_KEY .env | cut -d= -f2)

# Enriquecer lead
curl -H "X-API-KEY: $API_KEY" \
  -H "Content-Type: application/json" \
  -X POST http://localhost:8081/api/v1/leads/enrich \
  -d '{"email":"joao@exemplo.com","name":"João Silva"}'

# Listar leads (paginado)
curl -H "X-API-KEY: $API_KEY" \
  "http://localhost:8081/api/v1/leads?page=0&size=10&sort=createdAt,desc"

# Buscar por ID
curl -H "X-API-KEY: $API_KEY" \
  http://localhost:8081/api/v1/leads/1

# Buscar por domínio
curl -H "X-API-KEY: $API_KEY" \
  "http://localhost:8081/api/v1/leads/domain/exemplo.com"

# Atualizar lead
curl -H "X-API-KEY: $API_KEY" \
  -H "Content-Type: application/json" \
  -X PUT http://localhost:8081/api/v1/leads/1 \
  -d '{"email":"joao@novoemail.com","name":"João Pereira"}'

# Excluir lead (hard delete)
curl -H "X-API-KEY: $API_KEY" \
  -X DELETE http://localhost:8081/api/v1/leads/1
```

### 6.2 Swagger UI

Acesse `http://localhost:8081/swagger-ui/index.html` para:

- Visualizar todos os endpoints
- Testar chamadas diretamente pelo navegador
- Ver schemas de request/response
- Copiar exemplos

### 6.3 Testes com o OpenAPI Spec

Baixe a spec em `http://localhost:8081/v3/api-docs` e importe no Postman/Insomnia.

---

## 7. Depuração e Troubleshooting

### 7.1 Problemas Comuns

| Problema | Causa Provável | Solução |
|---|---|---|
| **App não sobe — porta ocupada** | Porta 8081 em uso | Mude `PORT` no `.env` ou mate o processo: `netstat -ano \| findstr :8081` |
| **Connection refused no PostgreSQL** | Docker não iniciou | `docker compose up -d postgres` |
| **API Key inválida** | `.env` não configurado | `cp .env.example .env` e preencha |
| **OpenSERP retorna vazio** | OpenSERP não iniciou | `docker compose logs -f openserp` |
| **Erro de criptografia** | `ENCRYPTION_SECRET` inválida | Deve ter pelo menos 16 caracteres |
| **Maven sem memória** | Limite baixo | `export MAVEN_OPTS="-Xmx512m"` |

### 7.2 Logs

```bash
# App
docker compose logs -f app

# PostgreSQL
docker compose logs -f postgres

# OpenSERP
docker compose logs -f openserp

# Jaeger
docker compose logs -f jaeger
```

### 7.3 Tracing com Jaeger

1. Acesse `http://localhost:16686`
2. Selecione o serviço `lead-enrichment-api`
3. Clique em "Find Traces"
4. Clique em qualquer trace para ver o span detalhado

### 7.4 Health Checks

```bash
curl -s http://localhost:8081/actuator/health | jq .
# → status UP / DOWN

curl -s http://localhost:8081/actuator/health/liveness
curl -s http://localhost:8081/actuator/health/readiness
```

### 7.5 Recriar Tudo do Zero

```bash
# Para tudo e remove volumes (dados do banco)
docker compose down -v

# Limpa build do Maven
mvn clean

# Sobe tudo novamente
docker compose up --build -d
```

---

## 8. Padrões e Convenções

### 8.1 Código

| Padrão | Diretriz |
|---|---|
| **Idioma** | Código e comentários em **inglês** |
| **Pacotes** | `solutions.pdroti.lead.enrichment.api.{camada}` |
| **Nomes** | `camelCase` para variáveis/métodos, `PascalCase` para classes |
| **Lombok** | Use `@Slf4j`, `@RequiredArgsConstructor`, `@Getter/@Setter` |
| **SRP** | Cada classe tem uma única responsabilidade |
| **Injeção** | Prefira construtor com `@RequiredArgsConstructor` |
| **DTOs** | Use `record` do Java (imutáveis) |
| **Validação** | `@Valid` + Jakarta Validation nos DTOs |
| **Exceções** | Tratamento global via `GlobalExceptionHandler` |

### 8.2 Banco de Dados

| Padrão | Diretriz |
|---|---|
| **DDL** | `ddl-auto=update` (Hibernate gerencia) |
| **Fetch** | `FetchType.LAZY` sempre que possível |
| **Consultas** | Spring Data JPA derived queries |
| **Hash** | `emailHash` = SHA-256(lowercase email), unique |

### 8.3 API

| Padrão | Diretriz |
|---|---|
| **Base Path** | `/api/v1/leads` |
| **Autenticação** | Header `X-API-KEY` |
| **Formato** | JSON |
| **Paginação** | Spring `Pageable` + `Page<LeadResponse>` |
| **Erros** | JSON padronizado: `{error, message, timestamp}` |
| **Códigos** | 200, 400, 401, 404, 500 |

### 8.4 Configuração

| Padrão | Diretriz |
|---|---|
| **Sensível** | Variáveis de ambiente (`.env`, não versionado) |
| **Regras de negócio** | `application.yml` com `@ConfigurationProperties` |
| **Default** | Valores padrão seguros para desenvolvimento |

---

## 9. Comandos Úteis

### Maven

```bash
# Compilar
build-jdk21.bat compile

# Compilar + testes
build-jdk21.bat test

# Empacotar JAR
build-jdk21.bat package -DskipTests

# Executar aplicação
build-jdk21.bat spring-boot:run -Dmaven.test.skip=true

# Limpar build
build-jdk21.bat clean

# Instalar dependências sem compilar
build-jdk21.bat dependency:go-offline -B -DskipTests
```

### Docker

```bash
# Subir tudo
docker compose up --build -d

# Parar tudo
docker compose down

# Parar tudo e remover volumes
docker compose down -v

# Logs de um serviço específico
docker compose logs -f app

# Rebuild de um serviço
docker compose up -d --build app

# Executar comando no container
docker compose exec app sh
```

### Git

```bash
# Verificar diff antes de commit
git diff --cached

# Commits parciais (apenas alguns arquivos)
git add -p

# Desfazer alterações locais (cuidado!)
git checkout -- <arquivo>

# Stash temporário
git stash
git stash pop
```

---

## 10. Glossário

| Termo | Definição |
|---|---|
| **Lead** | Potencial cliente com dados de contato (e-mail, nome, domínio) |
| **Enriquecimento** | Processo de coletar dados públicos sobre um lead |
| **OpenSERP** | Google Search API self-hosted (karust/openserp) |
| **RDAP** | Registration Data Access Protocol — dados de registro de domínio |
| **DNS** | Domain Name System — registros MX, A, AAAA, CNAME, TXT |
| **TechScraper** | Serviço que detecta tecnologias de um site (WordPress, React, etc.) |
| **SocialDiscovery** | Serviço que descobre perfis em redes sociais |
| **AES-128-GCM** | Algoritmo de criptografia simétrica com autenticação |
| **SHA-256** | Hash criptográfico para consulta segura de e-mails |
| **Hard Delete** | Exclusão física do registro no banco de dados |
| **Virtual Threads** | Loom — threads leves do Java 21 para I/O intensivo |
| **Caffeine** | Biblioteca de cache em memória (alta performance) |
| **CompletableFuture** | API Java para programação assíncrona |
| **PII** | Personally Identifiable Information (dados pessoais) |
| **LGPD** | Lei Geral de Proteção de Dados (Brasil) |

---

## 11. Onde Encontrar Ajuda

| Recurso | Localização |
|---|---|
| **Documentação Técnica** | `docs/TECHNICAL_REFERENCE.md` |
| **Guia da API** | `docs/api-guide.md` |
| **Arquitetura** | `docs/architecture.md` |
| **Deploy** | `docs/deployment.md` |
| **Segurança/LGPD** | `docs/security-lgpd.md` |
| **ADRs** | `docs/adr/` (10 documentos) |
| **Diagramas** | `docs/diagrams/` (3 diagramas Mermaid) |
| **OpenAPI Spec** | `docs/openapi.yaml` |
| **Swagger UI** | `http://localhost:8081/swagger-ui/index.html` |
| **Jaeger (Tracing)** | `http://localhost:16686` |
| **Código Fonte** | `src/main/java/solutions/pdroti/lead/enrichment/api/` |

---

> **Dúvidas?** Fale com o time no canal do projeto ou abra uma issue no repositório.
>
> *"Um lead enriquecido por dia, não sabe o bem que lhe faria."* 😊
>
> **Última atualização:** 2026-06-13
