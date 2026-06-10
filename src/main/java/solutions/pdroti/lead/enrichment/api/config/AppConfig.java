package solutions.pdroti.lead.enrichment.api.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
     * RestTemplate configurado com timeouts para chamadas HTTP externas.
     * Substitui a criação manual de OkHttpClient no {@code OpenSerpSearch}.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(20))
                .build();
    }
}
