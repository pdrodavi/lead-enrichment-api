package solutions.pdroti.lead.enrichment.api.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuração geral da aplicação.
 * <p>
 * Define beans compartilhados como o {@link RestTemplate} com timeouts configurados.
 */
@Configuration
public class AppConfig {

    /**
     * RestTemplate padrão com timeouts moderados para chamadas HTTP externas
     * (DNS, RDAP, scraping de páginas, etc.).
     */
    @Bean
    @Primary
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(20))
                .build();
    }

    /**
     * RestTemplate dedicado ao OpenSERP com timeouts estendidos,
     * já que buscas no Google self-hosted podem levar até 30 segundos.
     */
    @Bean
    @Qualifier("openSerpRestTemplate")
    public RestTemplate openSerpRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }
}
