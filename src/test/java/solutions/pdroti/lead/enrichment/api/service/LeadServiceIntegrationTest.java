/*
package solutions.pdroti.lead.enrichment.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import solutions.pdroti.lead.enrichment.api.model.Lead;
import solutions.pdroti.lead.enrichment.api.repository.LeadRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
public class LeadServiceIntegrationTest {

    @Autowired
    private LeadService leadService;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @MockBean
    private DnsValidationService dnsValidationService;

    @MockBean
    private TechScraperService techScraperService;

    @Test
    void testLeadEnrichmentFlow() {
        String domain = "example.com";

        redisTemplate.delete("lead:" + domain);

        when(dnsValidationService.hasMxRecord(domain)).thenReturn(true);
        when(techScraperService.scrape(domain)).thenReturn(anyMap());

        Lead lead = leadService.enrich(domain, "email@example.com");

        assertNotNull(lead);
        assertEquals(domain, lead.getDomain());

        verify(techScraperService, times(1)).scrape(domain);

        Lead cachedLead = (Lead) redisTemplate.opsForValue().get("lead:" + domain);
        assertNotNull(cachedLead);

        leadService.enrich(domain, "email@example.com");

        verify(techScraperService, times(1)).scrape(domain);
    }
}
*/