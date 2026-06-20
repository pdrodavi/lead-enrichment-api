package solutions.pdroti.lead.enrichment.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Rate limiter para o OpenSERP Search.
 * <p>
 * Garante um delay mínimo entre requisições consecutivas para
 * evitar bloqueio por rate limiting do Google/OpenSERP.
 */
@Slf4j
@Component
public class OpenSerpRateLimiter {

    private final long minDelayMs;
    private final AtomicReference<Instant> lastRequestTime = new AtomicReference<>(Instant.EPOCH);

    public OpenSerpRateLimiter() {
        this(2_000);
    }

    public OpenSerpRateLimiter(long minDelayMs) {
        this.minDelayMs = minDelayMs;
    }

    /** Garante o delay mínimo desde a última requisição. Bloqueia se necessário. */
    public void acquire() {
        Instant last = lastRequestTime.get();
        Instant now = Instant.now();
        long elapsed = Duration.between(last, now).toMillis();
        if (elapsed < minDelayMs) {
            try {
                Thread.sleep(minDelayMs - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestTime.set(Instant.now());
    }
}
