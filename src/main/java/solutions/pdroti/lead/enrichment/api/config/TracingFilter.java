package solutions.pdroti.lead.enrichment.api.config;

import io.opentelemetry.api.trace.Span;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Filtro que captura o corpo das requisições e respostas e adiciona como
 * atributos no span do OpenTelemetry, permitindo visualizar payloads no Jaeger.
 * <p>
 * Usa {@link ContentCachingRequestWrapper} e {@link ContentCachingResponseWrapper}
 * para ler body/response sem consumir os streams originais.
 */
@Slf4j
@Component
@Order(2)
public class TracingFilter extends OncePerRequestFilter {

    /** Tamanho máximo do body capturado (10KB). */
    private static final int MAX_BODY_SIZE = 10_240;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (isAsyncDispatch(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            captureRequest(wrappedRequest);
            captureResponse(wrappedResponse);
            wrappedResponse.copyBodyToResponse();
        }
    }

    /** Extrai o body da requisição e adiciona ao span. */
    private void captureRequest(ContentCachingRequestWrapper request) {
        byte[] buf = request.getContentAsByteArray();
        if (buf.length == 0) return;

        Span span = Span.current();
        if (span == null || !span.isRecording()) return;

        int len = Math.min(buf.length, MAX_BODY_SIZE);
        span.setAttribute("http.request.body", new String(buf, 0, len, StandardCharsets.UTF_8));
        if (buf.length > MAX_BODY_SIZE) {
            span.setAttribute("http.request.body.truncated", true);
        }
    }

    /** Extrai o body da resposta e adiciona ao span. */
    private void captureResponse(ContentCachingResponseWrapper response) {
        byte[] buf = response.getContentAsByteArray();
        if (buf.length == 0) return;

        Span span = Span.current();
        if (span == null || !span.isRecording()) return;

        int len = Math.min(buf.length, MAX_BODY_SIZE);
        span.setAttribute("http.response.body", new String(buf, 0, len, StandardCharsets.UTF_8));
        span.setAttribute("http.response.size", buf.length);
        if (buf.length > MAX_BODY_SIZE) {
            span.setAttribute("http.response.body.truncated", true);
        }
    }
}
