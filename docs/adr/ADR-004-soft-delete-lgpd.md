# ADR-004: Estratégia de Exclusão para Conformidade LGPD (Direito ao Esquecimento)

## Status

**Atualizado (Jun/2026)** — A implementação atual utiliza **hard delete** (exclusão física).

## Contexto

A LGPD garante ao titular o direito de solicitar a exclusão de seus dados pessoais
(Art. 18, VI — Direito ao Esquecimento). A aplicação precisa de uma estratégia de
exclusão que:

- Atenda integralmente ao direito ao esquecimento (remoção física dos dados)
- Seja simples e eficiente (1 query, sem jobs de expurgo futuros)
- Mantenha rastreabilidade via logs da aplicação
- Garanta que leads excluídos não apareçam em consultas

## Decisão

Implementar **hard delete (exclusão física)** — o registro é removido permanentemente
do banco de dados via `deleteById` em 1 única query.

### Implementação

```java
// LeadDeletionService.hardDelete()
public boolean hardDelete(String id) {
    return parseNumericId(id)
            .map(numericId -> {
                try {
                    leadRepository.deleteById(numericId);
                    log.info("Lead hard deleted: ID={}", numericId);
                    return true;
                } catch (EmptyResultDataAccessException e) {
                    log.warn("Lead não encontrado para hard delete: ID={}", numericId);
                    return false;
                }
            })
            .orElse(false);
}
```

### Fluxo

```
DELETE /api/v1/leads/{id}
       │
       ▼
LeadDeletionService.hardDelete(id)
       │
       ├── parseNumericId(id) → Optional<Long>
       ├── leadRepository.deleteById(numericId)
       │       │
       │       ├── Sucesso → 200 OK + mensagem LGPD
       │       └── EmptyResultDataAccessException → 404 Not Found
       │
       └── ID inválido → 404 Not Found
```

### Comportamento das Consultas

| Endpoint | Comportamento pós-hard-delete |
|---|---|
| `GET /api/v1/leads` | Não retorna leads excluídos |
| `GET /api/v1/leads/{id}` | Retorna 404 |
| `GET /api/v1/leads/domain/{domain}` | Não retorna leads excluídos |
| `PUT /api/v1/leads/{id}` | Retorna 404 |
| `DELETE /api/v1/leads/{id}` | Retorna 404 (já excluído) |

### Resposta de Sucesso

```json
{
  "message": "Lead excluído permanentemente do banco de dados",
  "lgpdMessage": "Lead excluído com sucesso (LGPD — direito ao esquecimento)",
  "id": "1"
}
```

### Histórico: Soft Delete (abordagem anterior)

Originalmente a aplicação implementava **soft delete** (exclusão lógica com status
`DELETED` e campo `deletedAt`). O método `LeadDeletionService.softDelete()` ainda
existe no código para referência, mas o endpoint padrão (`DELETE /api/v1/leads/{id}`)
utiliza hard delete desde a refatoração.

## Consequências

- Positivas:
  - Atende integralmente ao Art. 18, VI da LGPD (direito ao esquecimento)
  - Zero ocupação de espaço com dados "excluídos"
  - 1 query vs 2 queries (soft delete exigia UPDATE + eventual expurgo)
  - Rastreabilidade mantida via logs: `"Lead hard deleted: ID=X"`
  - Simplicidade operacional: sem jobs de expurgo
  - Criptografia AES-GCM garante que dados jamais foram expostos

- Negativas:
  - Sem possibilidade de recuperação após exclusão
  - Sem trilha de auditoria no banco (apenas logs da aplicação)
  - Leads deletados não são contabilizados em métricas históricas

## Alternativas Consideradas

| Alternativa | Motivo da Rejeição |
|---|---|
| Soft delete com expurgo | Complexidade adicional (job schedulado), ocupação de espaço |
| Tabela de log separada | Complexidade desnecessária; logs da aplicação são suficientes |
| GDPR-style anonymization | Dados enriquecidos perdem valor se anonimizados |

## Referências

- [LGPD Art. 18, VI — Direito ao esquecimento](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709.htm)
- [LGPD Art. 15 — Término do tratamento de dados](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709.htm)

## Referências

- [LGPD Art. 18, VI — Direito ao esquecimento](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709.htm)
- [LGPD Art. 15 — Término do tratamento de dados](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709.htm)
