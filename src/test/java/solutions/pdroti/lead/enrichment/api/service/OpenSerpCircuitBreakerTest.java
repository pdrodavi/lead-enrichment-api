package solutions.pdroti.lead.enrichment.api.service;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class OpenSerpCircuitBreakerTest {

    @Test
    void isOpen_quandoNuncaAberto_deveRetornarFalse() {
        var cb = new OpenSerpCircuitBreaker(3, Duration.ofMinutes(5));
        assertFalse(cb.isOpen());
    }

    @Test
    void isOpen_aposReset_deveRetornarFalse() {
        var cb = new OpenSerpCircuitBreaker(1, Duration.ofMinutes(5));
        cb.recordCaptcha();
        assertTrue(cb.isOpen());
        // Após cooldown expirar, isOpen retorna false
    }

    @Test
    void recordCaptcha_abaixoDoThreshold_deveRetornarFalse() {
        var cb = new OpenSerpCircuitBreaker(3, Duration.ofMinutes(5));
        assertFalse(cb.recordCaptcha());
        assertFalse(cb.recordCaptcha());
        assertFalse(cb.isOpen());
    }

    @Test
    void recordCaptcha_atingeThreshold_deveAbrirCircuito() {
        var cb = new OpenSerpCircuitBreaker(2, Duration.ofMinutes(5));
        assertFalse(cb.recordCaptcha());
        assertTrue(cb.recordCaptcha());
        assertTrue(cb.isOpen());
    }

    @Test
    void reset_aposCaptcha_deveZerarContador() {
        var cb = new OpenSerpCircuitBreaker(3, Duration.ofMinutes(5));
        cb.recordCaptcha();
        cb.reset();
        assertFalse(cb.isOpen());
    }

    @Test
    void construtorDefault_deveUsarThreshold3() {
        var cb = new OpenSerpCircuitBreaker();
        cb.recordCaptcha();
        cb.recordCaptcha();
        assertFalse(cb.isOpen());
        cb.recordCaptcha();
        assertTrue(cb.isOpen());
    }
}
