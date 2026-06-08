package solutions.pdroti.lead.enrichment.api.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailUtilsTest {

    @Test
    void shouldMaskEmail() {
        assertEquals("ped***@pdroti.com", EmailUtils.mask("pedro@pdroti.com"));
    }

    @Test
    void shouldMaskShortLocalPart() {
        assertEquals("a***@bc.com", EmailUtils.mask("ab@bc.com"));
    }

    @Test
    void shouldReturnNullForNull() {
        assertNull(EmailUtils.mask(null));
    }

    @Test
    void shouldReturnAsIsIfNoAt() {
        assertEquals("invalido", EmailUtils.mask("invalido"));
    }
}
