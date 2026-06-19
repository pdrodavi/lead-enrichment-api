package solutions.pdroti.lead.enrichment.api.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataParserTest {

    // ========== extractDomainFromEmail() ==========

    @Test
    void extractDomainFromEmail_deveExtrairDominio() {
        assertEquals("exemplo.com", DataParser.extractDomainFromEmail("user@exemplo.com"));
        assertEquals("gmail.com", DataParser.extractDomainFromEmail("joao@gmail.com"));
        assertEquals("empresa.com.br", DataParser.extractDomainFromEmail("contato@empresa.com.br"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"invalido", "sem-arroba"})
    void extractDomainFromEmail_deveLancarExcecaoParaInvalidos(String input) {
        assertThrows(IllegalArgumentException.class, () -> DataParser.extractDomainFromEmail(input));
    }

    // ========== isPersonalEmailDomain() ==========

    @ParameterizedTest
    @CsvSource({
        "gmail.com, true",
        "hotmail.com, true",
        "outlook.com, true",
        "yahoo.com.br, true",
        "protonmail.com, true",
        "icloud.com, true",
        "exemplo.com, false",
        "empresa.com.br, false",
        "MINHAEMPRESA.com, false"
    })
    void isPersonalEmailDomain(String domain, boolean expected) {
        assertEquals(expected, DataParser.isPersonalEmailDomain(domain));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void isPersonalEmailDomain_deveRetornarFalseParaNullOuBlank(String domain) {
        assertFalse(DataParser.isPersonalEmailDomain(domain));
    }

    @Test
    void isPersonalEmailDomain_deveIgnorarCase() {
        assertTrue(DataParser.isPersonalEmailDomain("GMAIL.COM"));
        assertTrue(DataParser.isPersonalEmailDomain("Outlook.com"));
    }

    // ========== nameMatchesExactly() ==========

    @ParameterizedTest
    @CsvSource({
        "sobre João Silva, João Silva, true",
        "João Silva é engenheiro, João Silva, true",
        "Conheça João Silva, João Silva, true",
        "sobre João Silveira, João Silva, false",
        "sobre João Silvares, João Silva, false",
        "João Silva, João Silva, true",
        "texto, Nome Inexistente, false"
    })
    void nameMatchesExactly(String text, String name, boolean expected) {
        assertEquals(expected, DataParser.nameMatchesExactly(text, name));
    }

    @Test
    void nameMatchesExactly_deveRetornarFalseSeAlgumParametroForNull() {
        assertFalse(DataParser.nameMatchesExactly(null, "João"));
        assertFalse(DataParser.nameMatchesExactly("texto", null));
        assertFalse(DataParser.nameMatchesExactly(null, null));
    }

    // ========== extractEmails() ==========

    @Test
    void extractEmails_deveExtrairEmailsDoSnippetETitulo() {
        var emails = new ArrayList<String>();
        DataParser.extractEmails(emails, "Contato: joao@exemplo.com", "Página do João");
        assertTrue(emails.contains("joao@exemplo.com"));
    }

    @Test
    void extractEmails_deveFiltrarExampleDotCom() {
        var emails = new ArrayList<String>();
        DataParser.extractEmails(emails, "user@example.com", "Teste");
        assertFalse(emails.contains("user@example.com"));
    }

    @Test
    void extractEmails_deveExtrairDeSnippetETitulo() {
        var emails = new ArrayList<String>();
        DataParser.extractEmails(emails, "contato@site.com", "pagina@site.com");
        assertTrue(emails.contains("contato@site.com"));
        assertTrue(emails.contains("pagina@site.com"));
        assertEquals(2, emails.size());
    }

    // ========== extractPhones() ==========

    @Test
    void extractPhones_deveExtrairTelefonesValidos() {
        var phones = new ArrayList<String>();
        DataParser.extractPhones(phones, "Tel: (11) 99999-8888");
        assertFalse(phones.isEmpty());
    }

    @Test
    void extractPhones_deveFiltrarNumerosInvalidos() {
        var phones = new ArrayList<String>();
        DataParser.extractPhones(phones, "Tel: 123");
        assertTrue(phones.isEmpty());
    }

    @Test
    void extractPhones_deveIgnorarSnippetNullOuVazio() {
        var phones = new ArrayList<String>();
        DataParser.extractPhones(phones, null);
        assertTrue(phones.isEmpty());
        DataParser.extractPhones(phones, "");
        assertTrue(phones.isEmpty());
    }

    // ========== parseIsoDate() ==========

    @Test
    void parseIsoDate_deveConverterDataValida() {
        LocalDateTime date = DataParser.parseIsoDate("2026-06-05T15:19:47");
        assertNotNull(date);
        assertEquals(2026, date.getYear());
        assertEquals(6, date.getMonthValue());
        assertEquals(5, date.getDayOfMonth());
    }

    @Test
    void parseIsoDate_deveRemoverSufixoZ() {
        LocalDateTime date = DataParser.parseIsoDate("2026-06-05T15:19:47.754Z");
        assertNotNull(date);
    }

    @Test
    void parseIsoDate_deveTruncarNanossegundos() {
        LocalDateTime date = DataParser.parseIsoDate("2026-06-05T15:19:47.1234567890");
        assertNotNull(date);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "invalido"})
    void parseIsoDate_deveRetornarNullParaInvalidos(String input) {
        assertNull(DataParser.parseIsoDate(input));
    }

    // ========== toMutable() ==========

    @Test
    void toMutable_deveConverterListaImutavelParaArrayList() {
        List<String> imutavel = List.of("a", "b", "c");
        List<String> mutavel = DataParser.toMutable(imutavel);
        assertTrue(mutavel instanceof ArrayList);
        assertEquals(imutavel, mutavel);
    }

    @Test
    void toMutable_deveRetornarListaVaziaSeNull() {
        List<String> result = DataParser.toMutable(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ========== COMMON_EMAIL_PROVIDERS ==========

    @Test
    void commonEmailProviders_deveConterProvedoresConhecidos() {
        assertTrue(DataParser.COMMON_EMAIL_PROVIDERS.contains("gmail.com"));
        assertTrue(DataParser.COMMON_EMAIL_PROVIDERS.contains("outlook.com"));
        assertTrue(DataParser.COMMON_EMAIL_PROVIDERS.contains("yahoo.com.br"));
        assertTrue(DataParser.COMMON_EMAIL_PROVIDERS.contains("protonmail.com"));
        assertTrue(DataParser.COMMON_EMAIL_PROVIDERS.contains("icloud.com"));
        assertTrue(DataParser.COMMON_EMAIL_PROVIDERS.contains("bol.com.br"));
        assertTrue(DataParser.COMMON_EMAIL_PROVIDERS.contains("uol.com.br"));
    }
}
