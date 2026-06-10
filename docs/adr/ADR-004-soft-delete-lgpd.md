# ADR-004: Soft Delete para Conformidade LGPD (Direito ao Esquecimento)

## Status

Aceito

## Contexto

A LGPD garante ao titular o direito de solicitar a exclusão de seus dados pessoais (Art. 18, VI). A aplicação precisa:

- Atender ao direito ao esquecimento de forma auditável
- Manter rastreabilidade de exclusões para conformidade regulatória
- Permitir recuperação em caso de solicitação indevida
- Definir período de retenção e expurgo futuro

## Decisão

Implementar **soft delete** (exclusão lógica) na entidade `Lead`:

### Mecanismo

```java
@Entity
@Table(name = "leads")
public class Lead {
    // ...
    private String status;         // "ACTIVE" | "DELETED"
    private LocalDateTime deletedAt; // null se ativo
}
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
