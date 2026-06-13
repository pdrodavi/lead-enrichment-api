package solutions.pdroti.lead.enrichment.api.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import solutions.pdroti.lead.enrichment.api.dto.DnsResult;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Configuração geral da aplicação.
 * <p>
 * Define beans compartilhados: RestTemplate com connection pooling,
 * executor dedicado para tasks de enriquecimento e caches.
 */
@Configuration
public class AppConfig {

    /**
     * Executor dedicado para tasks de enriquecimento (CompletableFuture).
     * <p>
     * Usa Virtual Threads (Java 21+): cada task roda em uma virtual thread
     * leve e escalável, ideal para operações I/O-bound (DNS, HTTP, scraping).
     * Diferente de pools físicos, virtual threads não bloqueiam recursos
     * do sistema durante espera de I/O, permitindo escalar para centenas
     * de tarefas simultâneas sem overhead.
     */
    @Bean("enrichmentExecutor")
    public Executor enrichmentExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * RestTemplate padrão com connection pooling (Apache HttpClient 5),
     * reutilizando conexões TCP para reduzir latência em chamadas repetidas.
     */
    @Bean
    @Primary
    public RestTemplate restTemplate() {
        var connManager = new PoolingHttpClientConnectionManager();
        connManager.setMaxTotal(200);
        connManager.setDefaultMaxPerRoute(50);

        var connConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(5_000))
                .setSocketTimeout(Timeout.ofMilliseconds(20_000))
                .build();
        connManager.setDefaultConnectionConfig(connConfig);

        var requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(5_000))
                .build();

        var httpClient = HttpClients.custom()
                .setConnectionManager(connManager)
                .setDefaultRequestConfig(requestConfig)
                .evictExpiredConnections()
                .evictIdleConnections(Timeout.ofSeconds(30))
                .build();

        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
    }

    /**
     * RestTemplate dedicado ao OpenSERP com timeouts estendidos,
     * já que buscas no Google self-hosted podem levar até 30 segundos.
     */
    @Bean
    @Qualifier("openSerpRestTemplate")
    public RestTemplate openSerpRestTemplate() {
        var connManager = new PoolingHttpClientConnectionManager();
        connManager.setMaxTotal(50);
        connManager.setDefaultMaxPerRoute(10);

        var connConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(10_000))
                .setSocketTimeout(Timeout.ofMilliseconds(30_000))
                .build();
        connManager.setDefaultConnectionConfig(connConfig);

        var httpClient = HttpClients.custom()
                .setConnectionManager(connManager)
                .evictExpiredConnections()
                .build();

        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
    }

    /**
     * Cache de resultados DNS. TTL de 1 hora — registros DNS mudam pouco.
     */
    @Bean
    public Cache<String, DnsResult> dnsCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(10_000)
                .recordStats()
                .build();
    }

    /**
     * Cache de tecnologias detectadas por domínio. TTL de 1 hora.
     */
    @Bean
    public Cache<String, List<String>> techCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(10_000)
                .recordStats()
                .build();
    }

    /**
     * Cache de links sociais por domínio. TTL de 1 hora.
     */
    @Bean
    public Cache<String, List<String>> socialLinksCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(10_000)
                .recordStats()
                .build();
    }
}
