# ADR-011: Externalização de Variáveis de Ambiente e Hardening de Segurança

## Status

**Aprovado (Jun/2026)**

## Contexto

O `application.yml` continha valores hardcoded para credenciais sensíveis (API_KEY, ENCRYPTION_SECRET) e IPs de infraestrutura (173.249.3.202), representando riscos de segurança caso o repositório fosse exposto. Além disso, não havia validação em runtime para garantir que variáveis obrigatórias estivessem configuradas.

### Problemas Identificados

1. **Secrets no YAML versionado** — `API_KEY` e `ENCRYPTION_SECRET` tinham valores default reais
2. **IPs de produção hardcoded** — IPs dos servidores OpenSERP, OTEL e banco expostos
3. **Falta de fail-fast** — Se `API_KEY` não fosse configurada, a aplicação iniciava com o valor default
4. **33 parâmetros de configuração** espalhados entre YAML, defaults e sem documentação centralizada

## Decisão

1. **Remover todos os valores default sensíveis** do `application.yml`
2. **Adicionar `@PostConstruct`** em `ApiKeyFilter` e `EncryptionService` para validação fail-fast
3. **Criar `.env.example`** como template completo com todas as variáveis
4. **Externalizar +13 novos parâmetros** (Tomcat, JPA, Tracing, HikariCP) que antes eram fixos
5. **Manter defaults de desenvolvimento** para variáveis não-sensíveis (PORT, timeouts, pools)

### Variáveis Obrigatórias (fail-fast)

```java
@PostConstruct
void validateConfig() {
    if (expectedApiKey == null || expectedApiKey.isBlank()) {
        log.error("API_KEY não configurada.");
        throw new IllegalStateException("API_KEY é obrigatória");
    }
}
```

### Arquivo `.env` (gitignored)

```bash
# Exemplo de .env com valores reais (NUNCA commitado)
API_KEY=chave-real-de-producao
ENCRYPTION_SECRET=segredo-real-32-bytes
DB_URL=jdbc:postgresql://prod-server:5432/db
```

### Arquivo `.env.example` (versionado)

```bash
# Template com placeholders, copiar para .env
API_KEY=your-api-key-here
ENCRYPTION_SECRET=your-32-byte-encryption-secret-here
```

## Consequências

### Positivas
- **Segurança**: Nenhuma credencial ou IP real no repositório
- **Fail-fast**: A aplicação não inicia sem `API_KEY` ou `ENCRYPTION_SECRET`
- **Documentação**: `.env.example` serve como documentação única de todas as variáveis
- **Facilidade de setup**: `cp .env.example .env` + editar → aplicação roda

### Negativas
- **Dependência de `.env`**: Sem o arquivo, defaults de desenvolvimento ainda permitem start, mas sem banco/API Key a aplicação falhará
- **Manutenção**: `.env.example` precisa ser mantido em sincronia com `application.yml`

## Variáveis Gerenciadas

| Grupo | Qtd | Exemplos |
|---|---|---|
| Servidor | 1 | `PORT` |
| Banco/Pool | 7 | `DB_URL`, `HIKARI_MAX_POOL` |
| Redis | 6 | `REDIS_HOST`, `REDIS_POOL_MAX` |
| OpenSERP | 3 | `OPENSERP_API_URL` |
| Segurança | 2 | `API_KEY`, `ENCRYPTION_SECRET` |
| Tomcat | 2 | `TOMCAT_CONN_TIMEOUT` |
| Compression | 2 | `COMPRESSION_ENABLED` |
| Async/Threads | 2 | `VIRTUAL_THREADS` |
| JPA | 1 | `DDL_AUTO` |
| Tracing | 3 | `TRACING_ENABLED`, `TRACING_SAMPLING` |
| Timing | 1 | `TIMING_FILTER_ENABLED` |
| **Total** | **33** | |
