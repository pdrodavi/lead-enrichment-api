# ADR-009: Tratamento Global de Erros com @RestControllerAdvice

## Status

Aceito

## Contexto

A API precisa de um tratamento de erros consistente e padronizado. Requisitos:

- Respostas de erro em formato JSON uniforme
- Tratamento de erros de validação de beans (@Valid)
- Tratamento de argumentos inválidos (lead não encontrado)
- Tratamento de desconexão de cliente (broken pipe) sem stack trace
- Cobertura de erros não esperados (fallback 500)

## Decisão

Implementar um handler global de exceções com `@RestControllerAdvice`:

### Formato Padrão de Erro

```json
{
  "error": "Tipo do erro",
  "message": "Descrição legível",
  "timestamp": "2026-06-10T12:00:00"
}
```

> **Nota:** O timestamp é formatado com `DateTimeFormatter.ISO_LOCAL_DATE_TIME` (substituindo o `toString()` anterior, que gerava precisão variável de nanossegundos).

### Erros Tratados

| Exceção | HTTP | Comportamento |
|---|---|---|
| `MethodArgumentNotValidException` | 400 | Retorna lista de campos com erros de validação |
| `IllegalArgumentException` | 400 | Retorna mensagem descritiva (ex: "Lead não encontrado") |
| `IOException` (broken pipe) | - | Apenas loga warning — não estoura exceção |
| `Exception` (genérica) | 500 | Loga erro e retorna mensagem genérica |

### Implementação

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        log.warn("Erro de validação: {}", errors);
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Validation Error",
                "details", errors,
                "timestamp", LocalDateTime.now().format(TIMESTAMP_FORMATTER)
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex) {
        log.warn("Requisição inválida: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Bad Request",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now().format(TIMESTAMP_FORMATTER)
        ));
    }

    @ExceptionHandler(IOException.class)
    public void handleClientDisconnect(IOException ex) {
        String msg = ex.getMessage();
        if (msg != null && (msg.contains("broken pipe")
                || msg.contains("anulada") || msg.contains("abort")
                || msg.contains("reset"))) {
            log.warn("Cliente desconectou durante o processamento: {}", msg);
        } else {
            log.warn("Erro de I/O na resposta: {}", msg);
        }
    }
}
```

## Consequências

- Positivas:
  - Respostas de erro consistentes e previsíveis
  - Separação clara entre lógica de negócio e tratamento de erros
  - Clientes podem parsear erros de forma programática
  - Desconexões de cliente não poluem logs com stack traces

- Negativas:
  - Tratamento genérico pode ocultar detalhes úteis em erros específicos
  - Necessário manter o handler atualizado para novas exceções

## Referências

- [@RestControllerAdvice (Spring)](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-rest-spring-mvc.html)
- [RFC 7231 — HTTP Status Codes](https://datatracker.ietf.org/doc/html/rfc7231#section-6)
