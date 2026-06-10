# ADR-008: Mascaramento de Dados em Logs e Respostas (LGPD)

## Status

Aceito

## Contexto

A LGPD exige que dados pessoais não sejam expostos desnecessariamente (Art. 6 — Princípio da necessidade). A aplicação precisa garantir que:

- E-mails não apareçam em texto plano em logs
- Respostas da API não exponham e-mails completos
- O mascaramento seja consistente em toda a aplicação
- O tratamento seja thread-safe e performático

## Decisão

Implementar mascaramento centralizado via `EmailUtils` e aplicá-lo em:

1. **Respostas da API**: campo `emailMasked` substitui `email` no `LeadResponse`
2. **Logs**: `EmailUtils.mask()` é chamado antes de logar qualquer e-mail

### Implementação

```java
@UtilityClass
public class EmailUtils {

    private static final int MASK_VISIBLE_CHARS = 3;
    private static final String MASK = "***";

    public static String mask(String email) {
        if (email == null || email.isBlank() || !email.contains("@"))
            return null;
        int at = email.indexOf("@");
        String local = email.substring(0, at);
        String domain = email.substring(at);
        int visible = Math.min(local.length(), MASK_VISIBLE_CHARS);
        return local.substring(0, visible) + MASK + domain;
    }
}
```

### Exemplos

| Original | Mascarado | Contexto |
|---|---|---|
| `pedro@pdroti.com` | `ped***@pdroti.com` | Log e resposta |
| `joao@exemplo.com` | `joa***@exemplo.com` | Log e resposta |
| `ab@cd.com` | `ab***@cd.com` | Log e resposta |
| `a@b.com` | `a***@b.com` | Log e resposta |

### Uso no Código

```java
// Log seguro
log.info("Enriquecendo lead: nome={} email={}",
    name, EmailUtils.mask(email));

// Resposta segura — LeadResponse.fromEntity()
return new LeadResponse(
    lead.getId(),
    EmailUtils.mask(lead.getEmail()),  // emailMasked
    lead.getName(),
    // ...
);
```

### Thread-Safety

`EmailUtils` é um `@UtilityClass` (Lombok) sem estado mutável — 100% thread-safe.

## Consequências

- Positivas:
  - Proteção contra exposição acidental em logs
  - Consistência em toda a aplicação (único ponto de mascaramento)
  - Zero custo computacional (apenas substring + concatenação)
  - Thread-safe sem sincronização

- Negativas:
  - Informação parcial (não é possível reverter o mascaramento)
  - Logs de debugging perdem informação completa (intencional)

## Referências

- [LGPD Art. 6 — Princípios](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709.htm)
- [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)
