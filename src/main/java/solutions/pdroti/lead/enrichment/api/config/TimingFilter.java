package solutions.pdroti.lead.enrichment.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro que loga o tempo de cada requisição HTTP.
 * <p>
 * Exemplo de saída:
 * <pre>
 * 20:15:33 INFO  - ▶ POST /api/v1/leads/enrich → 200 em 34.2s
 * 20:15:35 INFO  - ▶ GET  /api/v1/leads      → 200 em 0.012s
 * </pre>
 */
@Slf4j
@Component
@Order(1)
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.timing-filter.enabled", havingValue = "true", matchIfMissing = false)
public class TimingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (isAsyncDispatch(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String method = request.getMethod();
        String path = getPath(request);
        long start = System.nanoTime();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            int status = response.getStatus();
            log.info("▶ {} {} → {} em {}ms", method, path, status, elapsedMs);
        }
    }

    /** Extrai o path limpo (sem query string). */
    private static String getPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        return ctx != null && !ctx.isBlank() ? uri.substring(ctx.length()) : uri;
    }
}
