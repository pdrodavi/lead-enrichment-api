package solutions.pdroti.lead.enrichment.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimingFilterTest {

    private TimingFilter filter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new TimingFilter();
    }

    @Test
    void doFilterWithNormalRequestLogsTime() throws ServletException, IOException {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getContextPath()).thenReturn("");
        when(response.getStatus()).thenReturn(200);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterWithContextPathExtractsCorrectly() throws ServletException, IOException {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/app/api/test");
        when(request.getContextPath()).thenReturn("/app");
        when(response.getStatus()).thenReturn(201);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
