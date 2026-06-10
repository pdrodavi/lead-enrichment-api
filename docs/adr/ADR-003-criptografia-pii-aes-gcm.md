# ADR-003: Criptografia de PII em Repouso com AES-128-GCM

## Status

Aceito

## Contexto

A LGPD (Lei 13.709/2018) classifica o e-mail como dado pessoal sensível. A aplicação armazena e-mails de leads no banco de dados e precisa garantir:

- Confidencialidade dos dados em repouso (Art. 46 — Medidas de segurança)
- Proteção contra vazamento de dados em caso de acesso não autorizado ao banco
- Capacidade de consulta por e-mail sem expor o dado original
- Transparência para a aplicação (conversão automática na leitura/escrita)

## Decisão

Implementar criptografia **AES-128-GCM** com **IV aleatório de 12 bytes** para proteção do campo `email` na entidade `Lead`, utilizando `AttributeConverter` do JPA.

### Arquitetura

```
┌─────────────┐     ┌──────────────────────┐     ┌─────────────┐
│  Aplicação  │────>│ JPA AttributeConverter│────>│ PostgreSQL  │
│  (texto     │     │ (criptografa/         │     │ ENC(base64) │
│   plano)    │<────│  descriptografa)      │<────│             │
└─────────────┘     └──────────────────────┘     └─────────────┘
```

### Componentes

| Componente | Arquivo | Responsabilidade |
|---|---|---|
| `EncryptionService` | `config/EncryptionService.java` | Algoritmo AES-128-GCM com IV aleatório |
| `EncryptedEmailConverter` | `config/EncryptedEmailConverter.java` | Converter automático JPA |

### Hash para Consulta

Como o e-mail é criptografado, consultas `WHERE email = ?` não funcionam. Para permitir lookup:

- Campo `emailHash` armazena **SHA-256** do e-mail (lowercase)
- Campo `unique = true` garante não-duplicação
- Consultas usam `findByEmailHash(hash)` em vez de `findByEmail(email)`

### Formato no Banco

```
ENC(<Base64(IV(12) + ciphertext(N))>)
```

### Exemplo

```
Entrada: "joao@exemplo.com"
Banco:   "ENC(qK3mR8xP2vL9aB0cF5dG6hJ7kL8mN9oP0qR1sT2uV3wX4yZ5)"
```

## Consequências

- Positivas:
  - Dados ilegíveis mesmo em caso de vazamento do banco
  - Zero mudanças no código de negócio (conversão automática)
  - AES-128-GCM fornece confidencialidade + autenticação (tag GCM)
  - IV aleatório por operação — mesmo e-mail gera ciphertext diferente

- Negativas:
  - Necessário gerenciamento seguro da chave `ENCRYPTION_SECRET` em produção
  - Não é possível fazer `LIKE` ou buscas parciais no e-mail
  - Overhead computacional mínimo (~1ms por operação)
  - Rotação de chave requer reprocessamento de todos os registros

## Referências

- [LGPD Art. 46](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709.htm)
- [NIST SP 800-38D — GCM](https://csrc.nist.gov/publications/detail/sp/800-38d/final)
- [OWASP Cryptographic Storage](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html)
