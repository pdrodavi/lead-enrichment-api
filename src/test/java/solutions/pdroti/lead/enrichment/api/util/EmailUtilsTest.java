package solutions.pdroti.lead.enrichment.api.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class EmailUtilsTest {

    // ========== mask() ==========

    @ParameterizedTest
    @CsvSource({
        "pedro@pdroti.com, ped***@pdroti.com",
        "joao.silva@gmail.com, joa***@gmail.com",
        "a@b.com, a***@b.com",
        "ab@cd.com, ab***@cd.com",
        "abc@def.com, abc***@def.com",
        "maria_123@empresa.com.br, mar***@empresa.com.br",
        "contato@exemplo.com, con***@exemplo.com"
    })
    void mask_deveOfuscarEmail(String input, String expected) {
        assertEquals(expected, EmailUtils.mask(input));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "invalido", "sem-arroba"})
    void mask_deveRetornarNullParaInvalidos(String input) {
        assertNull(EmailUtils.mask(input));
    }

    @Test
    void mask_deveManterDominioIntacto() {
        String masked = EmailUtils.mask("user@sub.dominio.com.br");
        assertNotNull(masked);
        assertTrue(masked.endsWith("@sub.dominio.com.br"));
    }

    // ========== hash() ==========

    @Test
    void hash_deveRetornarHashSha256Hex() {
        String hash = EmailUtils.hash("pedro@pdroti.com");
        assertNotNull(hash);
        assertEquals(64, hash.length()); // SHA-256 hex = 64 chars
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void hash_deveSerCaseInsensitive() {
        String hash1 = EmailUtils.hash("Pedro@Pdroti.com");
        String hash2 = EmailUtils.hash("pedro@pdroti.com");
        assertEquals(hash1, hash2);
    }

    @Test
    void hash_deveIgnorarEspacos() {
        String hash1 = EmailUtils.hash("  pedro@pdroti.com  ");
        String hash2 = EmailUtils.hash("pedro@pdroti.com");
        assertEquals(hash1, hash2);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "invalido", "sem-arroba"})
    void hash_deveRetornarNullParaInvalidos(String input) {
        assertNull(EmailUtils.hash(input));
    }

    @Test
    void hash_deveSerDeterministico() {
        String hash1 = EmailUtils.hash("teste@exemplo.com");
        String hash2 = EmailUtils.hash("teste@exemplo.com");
        assertEquals(hash1, hash2);
    }

    @Test
    void hash_emailsDiferentesDevemTerHashesDiferentes() {
        String hash1 = EmailUtils.hash("um@exemplo.com");
        String hash2 = EmailUtils.hash("dois@exemplo.com");
        assertNotEquals(hash1, hash2);
    }
}
