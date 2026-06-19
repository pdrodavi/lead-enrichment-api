package solutions.pdroti.lead.enrichment.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleValidation_deveRetornar400() {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "test");
        var ex = new MethodArgumentNotValidException(null, bindingResult);
        var response = handler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("error"));
        assertEquals("Validation Error", response.getBody().get("error"));
        assertTrue(response.getBody().containsKey("details"));
        assertTrue(response.getBody().containsKey("timestamp"));
    }

    @Test
    void handleIllegalArgument_deveRetornar400() {
        var response = handler.handleIllegalArgument(
                new IllegalArgumentException("Lead não encontrado"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Bad Request", response.getBody().get("error"));
        assertEquals("Lead não encontrado", response.getBody().get("message"));
    }

    @Test
    void handleClientDisconnect_brokenPipe_naoDeveLancarExcecao() {
        assertDoesNotThrow(() ->
            handler.handleClientDisconnect(new IOException("broken pipe")));
    }

    @Test
    void handleClientDisconnect_outroErro_naoDeveLancarExcecao() {
        assertDoesNotThrow(() ->
            handler.handleClientDisconnect(new IOException("erro qualquer")));
    }

    @Test
    void handleGeneral_deveRetornar500() {
        var response = handler.handleGeneral(new RuntimeException("Erro interno"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Internal Server Error", response.getBody().get("error"));
    }

    @Test
    void handleGeneral_naoDeveExporDetalhesInternos() {
        var response = handler.handleGeneral(new NullPointerException("dados sensíveis"));

        assertEquals("Ocorreu um erro interno. Tente novamente mais tarde.",
                response.getBody().get("message"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void todasAsRespostasDevemTerTimestamp() {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "test");
        var validation = handler.handleValidation(
                new MethodArgumentNotValidException(null, bindingResult));
        var illegalArg = handler.handleIllegalArgument(
                new IllegalArgumentException("teste"));
        var general = handler.handleGeneral(new RuntimeException("teste"));

        assertTrue(((Map<String, Object>) validation.getBody()).containsKey("timestamp"));
        assertTrue(((Map<String, Object>) illegalArg.getBody()).containsKey("timestamp"));
        assertTrue(((Map<String, Object>) general.getBody()).containsKey("timestamp"));
    }
}
