package solutions.pdroti.lead.enrichment.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Handler global de exceções que padroniza todas as respostas de erro da API.
 * <p>
 * Cobre validação de beans ({@link MethodArgumentNotValidException}),
 * argumentos inválidos ({@link IllegalArgumentException}), desconexão de
 * cliente ({@link IOException}) e erros internos genéricos.
 * <p>
 * Todas as respostas seguem o formato:
 * <pre>
 * { "error": "...", "message": "...", "timestamp": "..." }
 * </pre>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String TIMESTAMP = "timestamp";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Trata erros de validação de beans {@code @Valid}.
     * Retorna HTTP 400 com lista de campos e mensagens de erro.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        log.warn("Erro de validação: {}", errors);
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Validation Error",
                "details", errors,
                TIMESTAMP, LocalDateTime.now().format(TIMESTAMP_FORMATTER)
        ));
    }

    /**
     * Trata exceções de argumento inválido (ex: lead não encontrado).
     * Retorna HTTP 400 com a mensagem descritiva.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Requisição inválida: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Bad Request",
                "message", ex.getMessage(),
                TIMESTAMP, LocalDateTime.now().format(TIMESTAMP_FORMATTER)
        ));
    }

    /**
     * Trata desconexão do cliente durante o processamento.
     * Apenas loga warning — não estoura pilha de exceção.
     * Reconhece padrões comuns: broken pipe, anulada, abort, reset.
     */
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

    /**
     * Trata qualquer exceção não esperada (fallback genérico).
     * Retorna HTTP 500 sem expor detalhes internos.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Erro interno não esperado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Internal Server Error",
                "message", "Ocorreu um erro interno. Tente novamente mais tarde.",
                TIMESTAMP, LocalDateTime.now().format(TIMESTAMP_FORMATTER)
        ));
    }
}
