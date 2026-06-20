package solutions.pdroti.lead.enrichment.api.service;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.Type;
import solutions.pdroti.lead.enrichment.api.dto.DnsResult;

import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DnsValidationServiceTest {

    @Mock
    private Cache<String, DnsResult> dnsCache;

    @Mock
    private Executor enrichmentExecutor;

    private DnsValidationService dnsValidationService;

    @BeforeEach
    void setUp() {
        dnsValidationService = new DnsValidationService(dnsCache, enrichmentExecutor);
    }

    @Test
    void lookupDomain_comDomainNull_deveRetornarEmpty() {
        DnsResult result = dnsValidationService.lookupDomain(null);
        assertFalse(result.hasMx());
        assertTrue(result.mxRecords().isEmpty());
    }

    @Test
    void lookupDomain_comDomainBlank_deveRetornarEmpty() {
        DnsResult result = dnsValidationService.lookupDomain("   ");
        assertFalse(result.hasMx());
    }

    @Test
    void lookupDomain_comCacheHit_deveRetornarCache() {
        DnsResult cached = new DnsResult(true, List.of("mail.exemplo.com"), List.of(), List.of(), List.of(), List.of());
        when(dnsCache.getIfPresent("exemplo.com")).thenReturn(cached);

        DnsResult result = dnsValidationService.lookupDomain("exemplo.com");

        assertTrue(result.hasMx());
        assertEquals(1, result.mxRecords().size());
        verify(dnsCache).getIfPresent("exemplo.com");
    }

    @Test
    void lookupDomain_comCacheMiss_deveArmazenarNoCache() {
        DnsValidationService service = new DnsValidationService(dnsCache, Runnable::run);
        when(dnsCache.getIfPresent("exemplo.com")).thenReturn(null);

        DnsResult result = service.lookupDomain("exemplo.com");

        assertNotNull(result);
        verify(dnsCache).getIfPresent("exemplo.com");
        verify(dnsCache).put(eq("exemplo.com"), any(DnsResult.class));
    }

    @Test
    void hasMxRecord_deveConsultarEAvaliar() {
        DnsResult cached = new DnsResult(true, List.of("mail.exemplo.com"), List.of(), List.of(), List.of(), List.of());
        when(dnsCache.getIfPresent("exemplo.com")).thenReturn(cached);

        boolean mxResult = dnsValidationService.hasMxRecord("exemplo.com");

        assertTrue(mxResult);
    }

    @Test
    void hasMxRecord_comCacheHitComMx_deveRetornarTrue() {
        DnsResult cached = new DnsResult(true, List.of("mail.exemplo.com"), List.of(), List.of(), List.of(), List.of());
        when(dnsCache.getIfPresent("exemplo.com")).thenReturn(cached);

        boolean result = dnsValidationService.hasMxRecord("exemplo.com");

        assertTrue(result);
    }

    @Test
    void lookupDomain_deveIgnorarCase() {
        DnsResult cached = new DnsResult(true, List.of("mail.exemplo.com"), List.of(), List.of(), List.of(), List.of());
        when(dnsCache.getIfPresent("exemplo.com")).thenReturn(cached);

        DnsResult result = dnsValidationService.lookupDomain("ExEmPlo.CoM");

        assertTrue(result.hasMx());
    }

}
