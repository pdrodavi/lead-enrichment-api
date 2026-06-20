package solutions.pdroti.lead.enrichment.api.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SocialDiscoveryPropertiesTest {

    private static final String LINKEDIN = "linkedin.com";
    private static final String GITHUB = "github.com";

    private SocialDiscoveryProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SocialDiscoveryProperties();
    }

    @Test
    void socialDomainsDefaultIsEmpty() {
        assertTrue(properties.getSocialDomains().isEmpty());
    }

    @Test
    void socialDomainsWithValuesReturns() {
        properties.setSocialDomains(List.of(LINKEDIN, "facebook.com"));
        assertEquals(2, properties.getSocialDomains().size());
        assertTrue(properties.getSocialDomains().contains(LINKEDIN));
    }

    @Test
    void platformNamesDefaultIsEmpty() {
        assertTrue(properties.getPlatformNames().isEmpty());
    }

    @Test
    void platformNamesWithValuesReturns() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put(LINKEDIN, "LinkedIn");
        names.put(GITHUB, "GitHub");
        properties.setPlatformNames(names);

        assertEquals("LinkedIn", properties.getPlatformNames().get(LINKEDIN));
        assertEquals("GitHub", properties.getPlatformNames().get(GITHUB));
    }
}
