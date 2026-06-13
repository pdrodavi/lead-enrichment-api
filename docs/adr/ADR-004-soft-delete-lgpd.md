# ADR-004: Estratégia de Exclusão para Conformidade LGPD (Direito ao Esquecimento)

## Status

**Substituído** — A implementação atual utiliza hard delete (exclusão física).
Consulte o código em `LeadDeletionService.hardDelete()`.

## Histórico

Originalmente a aplicação implementava **soft delete** (exclusão lógica), onde o
registro era mantido no banco com status `DELETED` e `deletedAt` preenchido.

Após refatoração, a estratégia foi alterada para **hard delete** (exclusão física)
por razões de:

- **Simplicidade operacional:** elimina necessidade de job de expurgo futuro
- **LGPD Art. 16:** o direito ao esquecimento é atendido integralmente
- **Rastreabilidade:** o log da aplicação registra a exclusão com ID e timestamp
- **1 query vs 2 queries:** `deleteById` com try-catch é mais eficiente

## Mecanismo Anterior (Soft Delete)

```java
// Comportamento anterior (disponível via LeadDeletionService.softDelete())
lead.setStatus("DELETED");
lead.setDeletedAt(LocalDateTime.now());
leadRepository.save(lead);
```

### Fluxo

```
DELETE /api/v1/leads/{id}
       │
       ▼
UPDATE leads SET status = 'DELETED', deleted_at = NOW()
WHERE id = {id} AND status = 'ACTIVE'
       │
       ▼
200 OK + mensagem LGPD
```

### Comportamento das Consultas

| Endpoint | Comportamento pós-soft-delete |
|---|---|
| `GET /api/v1/leads` | Não retorna leads DELETED |
| `GET /api/v1/leads/{id}` | Retorna 404 |
| `GET /api/v1/leads/domain/{domain}` | Não retorna leads DELETED |
| `PUT /api/v1/leads/{id}` | Retorna 404 |
| `DELETE /api/v1/leads/{id}` | Retorna 404 |

### Período de Retenção

- Dados soft-deleted mantidos por **365 dias**
- E-mail permanece criptografado (AES-GCM) mesmo após exclusão
- Expurgo físico futuro a ser implementado (job schedulado)

## Consequências

- Positivas:
  - Atende ao Art. 18, VI da LGPD
  - Permite auditoria e recuperação
  - Dados sensíveis permanecem protegidos mesmo após exclusão

- Negativas:
  - Ocupação de espaço em banco com dados marcados como DELETED
  - Necessário job futuro para expurgo físico após retenção
  - Complexidade adicional em consultas (filtro por status)

## Alternativas Consideradas

| Alternativa | Motivo da Rejeição |
|---|---|
| Hard delete (DELETE físico) | Sem possibilidade de auditoria ou recuperação; não conformidade LGPD |
| Tabela de log separada | Complexidade desnecessária; soft delete atende aos requisitos |
| GDPR-style anonymization | Dados enriquecidos perdem valor se anonimizados |

## Referências

- [LGPD Art. 18, VI — Direito ao esquecimento](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709.htm)
- [LGPD Art. 15 — Término do tratamento de dados](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709.htm)
