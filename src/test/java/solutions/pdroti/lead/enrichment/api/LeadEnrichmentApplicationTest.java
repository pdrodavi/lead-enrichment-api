package solutions.pdroti.lead.enrichment.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LeadEnrichmentApplicationTest {

    @Test
    void applicationClassIsAccessible() {
        assertNotNull(LeadEnrichmentApplication.class);
        assertTrue(LeadEnrichmentApplication.class.getDeclaredAnnotations().length > 0);
    }

    @Test
    void applicationClassHasMainMethod() throws Exception {
        var mainMethod = LeadEnrichmentApplication.class.getMethod("main", String[].class);
        assertNotNull(mainMethod);
    }
}
