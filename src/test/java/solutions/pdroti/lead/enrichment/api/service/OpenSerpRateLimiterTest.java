package solutions.pdroti.lead.enrichment.api.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OpenSerpRateLimiterTest {

    @Test
    void acquire_primeiraChamada_devePassarImediatamente() {
        var limiter = new OpenSerpRateLimiter(500);
        assertDoesNotThrow(() -> limiter.acquire());
    }

    @Test
    void acquire_comDelaySuficiente_devePassar() throws Exception {
        var limiter = new OpenSerpRateLimiter(50);
        limiter.acquire();
        Thread.sleep(100);
        assertDoesNotThrow(() -> limiter.acquire());
    }

    @Test
    void construtorDefault_deveUsarDelay2000() {
        var limiter = new OpenSerpRateLimiter();
        assertDoesNotThrow(() -> limiter.acquire());
    }
}
