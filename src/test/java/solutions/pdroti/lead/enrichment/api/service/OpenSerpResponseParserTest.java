package solutions.pdroti.lead.enrichment.api.service;

import com.google.gson.JsonArray;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OpenSerpResponseParserTest {

    private OpenSerpResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new OpenSerpResponseParser();
    }

    @Test
    void parse_comJsonValido_deveRetornarResultados() {
        String json = """
                {"results": [
                    {"title": "João Silva - LinkedIn", "url": "https://linkedin.com/in/joaosilva", "domain": "linkedin.com"}
                ]}
                """;
        JsonArray results = parser.parse(json, "test");
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("João Silva - LinkedIn",
                results.get(0).getAsJsonObject().get("title").getAsString());
    }

    @Test
    void parse_comJsonVazio_deveRetornarNull() {
        assertNull(parser.parse("{}", "test"));
    }

    @Test
    void parse_comJsonSemResults_deveRetornarNull() {
        assertNull(parser.parse("{\"outro\": \"campo\"}", "test"));
    }

    @Test
    void parse_comNull_deveRetornarNull() {
        assertNull(parser.parse(null, "test"));
    }

    @Test
    void parse_comBlank_deveRetornarNull() {
        assertNull(parser.parse("   ", "test"));
    }

    @Test
    void parse_comTextoFormato_deveRetornarResultados() {
        String text = """
                [1] João Silva - LinkedIn (linkedin.com)
                Perfil profissional
                URL: https://linkedin.com/in/joaosilva
                
                [2] João Silva - GitHub (github.com)
                Código e projetos
                URL: https://github.com/joaosilva
                """;
        JsonArray results = parser.parse(text, "test");
        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("João Silva - LinkedIn",
                results.get(0).getAsJsonObject().get("title").getAsString());
        assertEquals("https://linkedin.com/in/joaosilva",
                results.get(0).getAsJsonObject().get("url").getAsString());
        assertEquals("linkedin.com",
                results.get(0).getAsJsonObject().get("domain").getAsString());
    }

    @Test
    void parse_comTextoSemUrl_deveRetornarUrlVazia() {
        String text = """
                [1] Título Teste (teste.com)
                Descrição sem URL
                """;
        JsonArray results = parser.parse(text, "test");
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("", results.get(0).getAsJsonObject().get("url").getAsString());
    }

    @Test
    void parse_comTextoInvalido_deveRetornarNull() {
        assertNull(parser.parse("texto sem formato reconhecido", "test"));
    }
}
