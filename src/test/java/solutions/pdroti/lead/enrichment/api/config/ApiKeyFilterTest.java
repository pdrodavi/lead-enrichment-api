package solutions.pdroti.lead.enrichment.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyFilterTest {

    private static final String VALID_KEY = "test-api-key-12345";

    private ApiKeyFilter filter;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyFilter(VALID_KEY);
    }

    @Test
    void shouldNotFilter_endpointsPublicos_deveIgnorar() {
        assertTrue(filter.shouldNotFilter(createRequest("/actuator/health")));
        assertTrue(filter.shouldNotFilter(createRequest("/swagger-ui/index.html")));
        assertTrue(filter.shouldNotFilter(createRequest("/v3/api-docs")));
        assertTrue(filter.shouldNotFilter(createRequest("/swagger-resources")));
    }

    @Test
    void shouldNotFilter_endpointsPrivados_naoDeveIgnorar() {
        assertFalse(filter.shouldNotFilter(createRequest("/api/v1/leads")));
        assertFalse(filter.shouldNotFilter(createRequest("/api/v1/leads/enrich")));
        assertFalse(filter.shouldNotFilter(createRequest("/api/v1/leads/1")));
    }

    @Test
    void doFilterInternal_comChaveValida_deveProsseguir() throws Exception {
        MockHttpServletRequest request = createRequest("/api/v1/leads");
        request.addHeader("X-API-KEY", VALID_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_semChave_deveRetornar401() throws Exception {
        MockHttpServletRequest request = createRequest("/api/v1/leads");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertTrue(response.getContentAsString().contains("Unauthorized"));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_comChaveInvalida_deveRetornar401() throws Exception {
        MockHttpServletRequest request = createRequest("/api/v1/leads");
        request.addHeader("X-API-KEY", "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_respostaDeveSerJson() throws Exception {
        MockHttpServletRequest request = createRequest("/api/v1/leads");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals("application/json", response.getContentType());
    }

    private MockHttpServletRequest createRequest(String path) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI(path);
        return req;
    }
}
