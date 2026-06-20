package solutions.pdroti.lead.enrichment.api.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SocialProfileDataTest {

    @Test
    void toSummary_comDadosCompletos_deveFormatar() {
        var data = new SocialProfileData(
                "https://github.com/pdroti", "GitHub",
                "pdroti (Pedro)", "Desenvolvedor");

        String result = data.toSummary();

        assertTrue(result.contains("GitHub"));
        assertTrue(result.contains("pdroti (Pedro)"));
        assertTrue(result.contains("Desenvolvedor"));
        assertTrue(result.contains("https://github.com/pdroti"));
    }

    @Test
    void toSummary_comApenasPlataforma_deveRetornarPlataforma() {
        var data = new SocialProfileData("https://x.com/user", "Twitter", null, null);

        String result = data.toSummary();

        assertEquals("Twitter:  (https://x.com/user)", result);
    }

    @Test
    void toSummary_comTituloLongo_deveTruncar() {
        String longTitle = "A".repeat(200);
        String longDesc = "B".repeat(200);
        var data = new SocialProfileData("https://exemplo.com", "Plataforma", longTitle, longDesc);

        String result = data.toSummary();

        assertTrue(result.length() <= 255);
        assertTrue(result.endsWith("..."));
    }

    @Test
    void toSummary_semUrlQuandoSummaryLongo() {
        String longTitle = "X".repeat(150);
        var data = new SocialProfileData("https://exemplo.com", "Plat", longTitle, null);

        String result = data.toSummary();

        assertFalse(result.contains("https://exemplo.com"));
    }

    @Test
    void toSummary_comTituloMasSemDescricao() {
        var data = new SocialProfileData("https://github.com/user", "GitHub", "User Name", null);

        String result = data.toSummary();

        assertTrue(result.contains("User Name"));
        assertFalse(result.contains(" — "));
    }

    @Test
    void toSummary_comDescricaoMasSemTitulo() {
        var data = new SocialProfileData("https://github.com/user", "GitHub", null, "Descrição aqui");

        String result = data.toSummary();

        assertTrue(result.contains("Descrição aqui"));
    }
}
