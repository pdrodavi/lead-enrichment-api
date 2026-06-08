# ADR-003: Criptografia de PII em Repouso

## Status
Aceito

## Contexto
A LGPD exige que dados pessoais (PII) sejam protegidos contra acesso não autorizado. O email é um dado pessoal sensível armazenado no banco de dados.

## Decisão
Criptografar o campo `email` usando **AES-128-GCM** com JPA `AttributeConverter`:
- `EncryptionService`: serviço com AES/GCM/NoPadding
- `EncryptedEmailConverter`: converter automático JPA
- Chave configurável via `ENCRYPTION_SECRET`
- Formato no banco: `ENC(<base64>)`

## Consequências
- Dados ilegíveis mesmo em caso de vazamento do banco
- Transparência para a aplicação (conversão automática)
- Custo computacional mínimo (AES é rápido)
- Necessário gerenciamento seguro da chave em produção
