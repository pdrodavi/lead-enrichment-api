package solutions.pdroti.lead.enrichment.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuração de proxies/endpoints para o OpenSERP.
 * <p>
 * Suporta múltiplos endpoints com proxies opcionais, permitindo
 * rotacionamento round-robin para evitar bloqueio por CAPTCHA.
 * <p>
 * Exemplo de configuração no application.yml:
 * <pre>
 * open-serp:
 *   endpoints:
 *     - url: http://opensrp1:7000
 *     - url: http://opensrp2:7000
 *       proxy: http://user:pass@proxy1:8080
 *     - url: http://opensrp3:7000
 *       proxy: http://user:pass@proxy2:8080
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "open-serp")
public class OpenSerpProxyProperties {

    /** Lista de endpoints OpenSERP com proxies opcionais. */
    private List<EndpointConfig> endpoints = new ArrayList<>();

    /** URL única (mantida para compatibilidade com config existente). */
    private String apiUrl;

    @Data
    public static class EndpointConfig {
        /** URL base do OpenSERP (ex: http://localhost:7000). */
        private String url;

        /**
         * Proxy opcional para este endpoint.
         * Formato: http://user:password@host:port ou http://host:port
         */
        private String proxy;
    }
}
