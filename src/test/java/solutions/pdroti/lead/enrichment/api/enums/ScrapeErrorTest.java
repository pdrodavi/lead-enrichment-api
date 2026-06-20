package solutions.pdroti.lead.enrichment.api.enums;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLException;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class ScrapeErrorTest {

    @Test
    void classify_comTimeout_deveRetornarTimeout() {
        String result = ScrapeError.classify(new SocketTimeoutException(), "timeout");
        assertEquals("ScrapeError: Timeout", result);
    }

    @Test
    void classify_comCloudflare_deveRetornarCloudflare() {
        String result = ScrapeError.classify(new RuntimeException(), "cloudflare challenge");
        assertEquals("ScrapeError: Cloudflare Protection", result);
    }

    @Test
    void classify_com403_deveRetornarAccessDenied() {
        String result = ScrapeError.classify(new RuntimeException(), "403 forbidden");
        assertEquals("ScrapeError: Access Denied (403)", result);
    }

    @Test
    void classify_comSslException_deveRetornarSslHandshake() {
        String result = ScrapeError.classify(new SSLException("handshake failed"), "ssl error");
        assertEquals("ScrapeError: SSL Handshake Failed", result);
    }

    @Test
    void classify_comUnknownHost_deveRetornarDomainNotFound() {
        String result = ScrapeError.classify(new RuntimeException(), "unknownhost exception");
        assertEquals("ScrapeError: Domain Not Found", result);
    }

    @Test
    void classify_com404_deveRetornarPageNotFound() {
        String result = ScrapeError.classify(new RuntimeException(), "404 not found");
        assertEquals("ScrapeError: Page Not Found (404)", result);
    }

    @Test
    void classify_comErroGenerico_deveRetornarNomeDaExcecao() {
        String result = ScrapeError.classify(new IllegalArgumentException("invalid"), "invalid");
        assertEquals("ScrapeError: IllegalArgumentException", result);
    }

    @Test
    void classify_comExcecaoComMsgNull_deveUsarMsgVazia() {
        String result = ScrapeError.classify(new RuntimeException(), null);
        assertEquals("ScrapeError: RuntimeException", result);
    }

    @Test
    void format_deveRetornarDescricaoFormatada() {
        assertEquals("ScrapeError: Timeout", ScrapeError.TIMEOUT.format());
        assertEquals("ScrapeError: Cloudflare Protection", ScrapeError.CLOUDFLARE.format());
    }

    @Test
    void classify_comMsgComTimeout_deveRetornarTimeout() {
        String result = ScrapeError.classify(new RuntimeException(), "the connection timed out");
        assertEquals("ScrapeError: Timeout", result);
    }

    @Test
    void classify_comMsgCom1020_deveRetornarCloudflare() {
        String result = ScrapeError.classify(new RuntimeException(), "error code 1020");
        assertEquals("ScrapeError: Cloudflare Protection", result);
    }
}
