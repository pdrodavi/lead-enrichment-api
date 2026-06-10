package solutions.pdroti.lead.enrichment.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Propriedades externalizadas para o {@code SocialDiscoveryService}.
 * <p>
 * Carregadas a partir de {@code application.yml} sob o prefixo {@code social-discovery}.
 * Permite adicionar/remover domínios e nomes de plataformas sociais sem modificar o código fonte.
 */
@Component
@ConfigurationProperties(prefix = "social-discovery")
public class SocialDiscoveryProperties {

    /**
     * Lista de domínios de redes sociais reconhecidos para classificação de links.
     */
    private List<String> socialDomains = List.of();

    /**
     * Mapa de domínio → nome da plataforma para identificação de redes sociais.
     */
    private Map<String, String> platformNames = new LinkedHashMap<>();

    public List<String> getSocialDomains() { return socialDomains; }
    public void setSocialDomains(List<String> socialDomains) { this.socialDomains = socialDomains; }

    public Map<String, String> getPlatformNames() { return platformNames; }
    public void setPlatformNames(Map<String, String> platformNames) { this.platformNames = platformNames; }
}
