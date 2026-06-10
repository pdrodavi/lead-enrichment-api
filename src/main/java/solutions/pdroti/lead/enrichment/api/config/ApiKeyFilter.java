package solutions.pdroti.lead.enrichment.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro HTTP que valida a API Key (X-API-KEY) em todas as requisições.
 * <p>
 * Endpoints públicos (actuator, Swagger/OpenAPI) são ignorados.
 * Retorna HTTP 401 com JSON de erro se a chave estiver ausente ou incorreta.
 */
@Slf4j
@Component
@Order(1)
public class ApiKeyFilter extends OncePerRequestFilter {

    /** Nome do header onde a API Key deve ser enviada. */
    private static final String API_KEY_HEADER = "X-API-KEY";

    private static final String ERROR_RESPONSE = """
        {"error":"Unauthorized","message":"Invalid or missing API key"}
        """.stripTrailing();

    private final String expectedApiKey;

    public ApiKeyFilter(@Value("${api.key}") String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    /**
     * Define quais endpoints NÃO exigem API Key.
     * Inclui actuator (health/checks) e Swagger UI.
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-resources");
    }

    /**
     * Valida a API Key do header X-API-KEY contra a chave configurada.
     * Retorna 401 com resposta JSON em caso de falha.
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey == null || !apiKey.equals(expectedApiKey)) {
            log.warn("API Key inválida para {} {}", request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(ERROR_RESPONSE);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
