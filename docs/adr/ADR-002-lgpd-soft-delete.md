# ADR-002: Soft Delete para Compliance LGPD

## Status
Aceito

## Contexto
A LGPD garante ao titular o direito de solicitar a exclusão de seus dados pessoais (art. 18, VI). Precisamos atender a esse requisito sem perder a capacidade de auditoria.

## Decisão
Implementar **soft delete** (exclusão lógica) em vez de exclusão física:
- Campos `deletedAt` e `status` = "DELETED" na entidade Lead
- Endpoint `DELETE /api/v1/leads/{id}`
- Dados não são retornados em consultas normais após exclusão
- Período de retenção de 365 dias com expurgo automático futuro

## Consequências
- Permite auditoria e recuperação em até 30 dias
- Dados sensíveis permanecem criptografados mesmo após exclusão
- Necessário job futuro para expurgo físico após retenção expirar
