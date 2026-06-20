package solutions.pdroti.lead.enrichment.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Circuit breaker para o OpenSERP Search.
 * <p>
 * Abre o circuito após N CAPTCHAs consecutivos por um período de cooldown.
 * Quando aberto, todas as buscas são recusadas com resposta vazia.
 */
@Slf4j
@Component
public class OpenSerpCircuitBreaker {

    private final int threshold;
    private final Duration cooldown;

    private final AtomicInteger consecutiveCaptchas = new AtomicInteger(0);
    private final AtomicReference<Instant> openedAt = new AtomicReference<>(null);

    public OpenSerpCircuitBreaker() {
        this(3, Duration.ofMinutes(5));
    }

    public OpenSerpCircuitBreaker(int threshold, Duration cooldown) {
        this.threshold = threshold;
        this.cooldown = cooldown;
    }

    /** Retorna true se o circuito estiver aberto (em cooldown). */
    public boolean isOpen() {
        Instant opened = openedAt.get();
        if (opened == null) return false;
        if (Instant.now().isAfter(opened.plus(cooldown))) {
            openedAt.set(null);
            consecutiveCaptchas.set(0);
            log.debug("OpenSERP circuit breaker fechado — retomando operação");
            return false;
        }
        return true;
    }

    /** Registra um CAPTCHA. Retorna true se o circuito foi aberto. */
    public boolean recordCaptcha() {
        int count = consecutiveCaptchas.incrementAndGet();
        log.warn("OpenSERP CAPTCHA #{}", count);

        if (count >= threshold) {
            openedAt.set(Instant.now());
            log.error("OpenSERP circuit breaker ABERTO — pausando por {} minutos", cooldown.toMinutes());
            return true;
        }
        return false;
    }

    /** Reseta o contador de CAPTCHAs (chamado em sucesso). */
    public void reset() {
        consecutiveCaptchas.set(0);
    }
}
