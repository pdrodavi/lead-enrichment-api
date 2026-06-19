# #Auditoria de Ativos & Lead Enrichment
## Objetivo
Identificar vulnerabilidades de infraestrutura e exposições de segurança.

## Fluxo
1. **Enriquecimento:** POST `/api/v1/leads/enrich`.
2. **Auditoria:** MX, DNS, arquivos expostos (Dorks).
3. **Severidade:** Ativos com arquivos expostos = CRÍTICO.
4. **Governança:** Log de auditoria persistente e exportação JSON.

## Estética
Industrial/Utilitário, tipografia monospaced.