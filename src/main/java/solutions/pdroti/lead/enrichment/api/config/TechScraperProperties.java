package solutions.pdroti.lead.enrichment.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Propriedades externalizadas para o {@code TechScraperService}.
 * <p>
 * Carregadas a partir de {@code application.yml} sob o prefixo {@code techscraper}.
 * Permite adicionar/remover assinaturas de detecção de tecnologias sem
 * modificar o código fonte.
 * <p>
 * Estrutura esperada no YAML:
 * <pre>{@code
 * techscraper:
 *   signatures:
 *     WordPress: ["wp-content", "wp-includes"]
 *   script-detectors:
 *     "Facebook Pixel": ["facebook", "fbq"]
 *   meta-generators:
 *     wordpress: "WordPress"
 * }</pre>
 */
@Component
@ConfigurationProperties(prefix = "techscraper")
public class TechScraperProperties {

    /**
     * Assinaturas HTML para detecção de tecnologias.
     * Chave = nome da tecnologia, Valor = substrings a buscar no HTML.
     */
    private Map<String, List<String>> signatures = new LinkedHashMap<>();

    /**
     * Detectores por atributo src em scripts.
     * Chave = nome do serviço, Valor = keywords a buscar no src.
     */
    private Map<String, List<String>> scriptDetectors = new LinkedHashMap<>();

    /**
     * Mapeamento de meta generator → tecnologia.
     * Chave = valor do meta generator, Valor = nome da tecnologia.
     */
    private Map<String, String> metaGenerators = new LinkedHashMap<>();

    public Map<String, List<String>> getSignatures() { return signatures; }
    public void setSignatures(Map<String, List<String>> signatures) { this.signatures = signatures; }

    public Map<String, List<String>> getScriptDetectors() { return scriptDetectors; }
    public void setScriptDetectors(Map<String, List<String>> scriptDetectors) { this.scriptDetectors = scriptDetectors; }

    public Map<String, String> getMetaGenerators() { return metaGenerators; }
    public void setMetaGenerators(Map<String, String> metaGenerators) { this.metaGenerators = metaGenerators; }
}
