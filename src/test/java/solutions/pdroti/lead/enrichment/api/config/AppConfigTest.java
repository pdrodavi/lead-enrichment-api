package solutions.pdroti.lead.enrichment.api.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.google.gson.JsonArray;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import solutions.pdroti.lead.enrichment.api.dto.DnsResult;
import solutions.pdroti.lead.enrichment.api.dto.RdapData;
import solutions.pdroti.lead.enrichment.api.dto.SocialProfileData;

import java.net.http.HttpClient;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    private AppConfig appConfig;

    @BeforeEach
    void setUp() {
        appConfig = new AppConfig();
    }

    @Test
    void enrichmentExecutorIsCreated() {
        Executor executor = appConfig.enrichmentExecutor();
        assertNotNull(executor);
    }

    @Test
    void restTemplateIsCreated() {
        RestTemplate restTemplate = appConfig.restTemplate();
        assertNotNull(restTemplate);
    }

    @Test
    void sharedHttpClientIsCreated() {
        HttpClient client = appConfig.sharedHttpClient();
        assertNotNull(client);
    }

    @Test
    void openSerpRestTemplateIsCreated() {
        RestTemplate restTemplate = appConfig.openSerpRestTemplate();
        assertNotNull(restTemplate);
    }

    @Test
    void dnsCacheIsCreated() {
        Cache<String, DnsResult> cache = appConfig.dnsCache();
        assertNotNull(cache);
        assertNull(cache.getIfPresent("qualquer"));
    }

    @Test
    void techCacheIsCreated() {
        Cache<String, List<String>> cache = appConfig.techCache();
        assertNotNull(cache);
    }

    @Test
    void socialLinksCacheIsCreated() {
        Cache<String, List<String>> cache = appConfig.socialLinksCache();
        assertNotNull(cache);
    }

    @Test
    void rdapCacheIsCreated() {
        Cache<String, RdapData> cache = appConfig.rdapCache();
        assertNotNull(cache);
    }

    @Test
    void openSerpCacheIsCreated() {
        Cache<String, JsonArray> cache = appConfig.openSerpCache();
        assertNotNull(cache);
    }

    @Test
    void openSerpHashCacheIsCreated() {
        Cache<String, String> cache = appConfig.openSerpHashCache();
        assertNotNull(cache);
    }

    @Test
    void socialProfileCacheIsCreated() {
        Cache<String, SocialProfileData> cache = appConfig.socialProfileCache();
        assertNotNull(cache);
    }

    @Test
    void cacheManagerIsCreated() {
        var cacheManager = appConfig.cacheManager();
        assertNotNull(cacheManager);
        assertNotNull(cacheManager.getCache("enrich-result"));
    }
}
