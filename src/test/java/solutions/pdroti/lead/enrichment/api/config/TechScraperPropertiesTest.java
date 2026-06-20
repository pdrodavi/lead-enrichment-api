package solutions.pdroti.lead.enrichment.api.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TechScraperPropertiesTest {

    private TechScraperProperties properties;

    @BeforeEach
    void setUp() {
        properties = new TechScraperProperties();
    }

    @Test
    void signatures_defaultDeveSerVazio() {
        assertTrue(properties.getSignatures().isEmpty());
    }

    @Test
    void signatures_comValores_deveRetornar() {
        Map<String, List<String>> sigs = new LinkedHashMap<>();
        sigs.put("WordPress", List.of("wp-content", "wp-includes"));
        sigs.put("Shopify", List.of("/cdn/shop"));
        properties.setSignatures(sigs);

        assertEquals(2, properties.getSignatures().size());
        assertTrue(properties.getSignatures().get("WordPress").contains("wp-content"));
    }

    @Test
    void scriptDetectors_defaultDeveSerVazio() {
        assertTrue(properties.getScriptDetectors().isEmpty());
    }

    @Test
    void scriptDetectors_comValores_deveRetornar() {
        Map<String, List<String>> detectors = new LinkedHashMap<>();
        detectors.put("Facebook Pixel", List.of("facebook", "fbq"));
        properties.setScriptDetectors(detectors);

        assertTrue(properties.getScriptDetectors().get("Facebook Pixel").contains("fbq"));
    }

    @Test
    void metaGenerators_defaultDeveSerVazio() {
        assertTrue(properties.getMetaGenerators().isEmpty());
    }

    @Test
    void metaGenerators_comValores_deveRetornar() {
        Map<String, String> generators = new LinkedHashMap<>();
        generators.put("wordpress", "WordPress");
        generators.put("joomla", "Joomla");
        properties.setMetaGenerators(generators);

        assertEquals("WordPress", properties.getMetaGenerators().get("wordpress"));
        assertEquals("Joomla", properties.getMetaGenerators().get("joomla"));
    }
}
