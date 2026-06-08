# ADR-001: Cache-Aside com Redis

## Status
Aceito

## Contexto
A API de enriquecimento de leads faz chamadas externas (DNS, scraping web) que são lentas e custosas. Precisamos de uma estratégia de cache para evitar reprocessamento.

## Decisão
Adotar o padrão **Cache-Aside** com Redis:
1. Consulta ao Redis primeiro
2. Se não existir, consulta ao PostgreSQL
3. Se não existir no DB, processa enriquecimento completo
4. Salva resultado no Redis e no DB

## Consequências
- Redução de latência em requisições repetidas
- TTL de 24h evita dados obsoletos
- Redis como cache distribuído (escalável horizontalmente)
