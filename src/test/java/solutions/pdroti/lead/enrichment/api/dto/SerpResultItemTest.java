package solutions.pdroti.lead.enrichment.api.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SerpResultItemTest {

    @Test
    void fromSearchResult_comUrlHttps_deveExtrairDominio() {
        var item = SerpResultItem.fromSearchResult(1, "https://exemplo.com/pagina", "Título", "Snippet");
        assertEquals("exemplo.com", item.domain());
        assertNull(item.fileType());
    }

    @Test
    void fromSearchResult_comUrlHttp_deveExtrairDominio() {
        var item = SerpResultItem.fromSearchResult(2, "http://site.com.br/sobre", "Título", "Snippet");
        assertEquals("site.com.br", item.domain());
    }

    @Test
    void fromSearchResult_comUrlComWww_deveRemoverWww() {
        var item = SerpResultItem.fromSearchResult(3, "https://www.github.com/user", "GitHub", "Snippet");
        assertEquals("github.com", item.domain());
    }

    @Test
    void fromSearchResult_comUrlSemPath_deveExtrairDominio() {
        var item = SerpResultItem.fromSearchResult(4, "https://linkedin.com", "LinkedIn", "Snippet");
        assertEquals("linkedin.com", item.domain());
    }

    @Test
    void fromSearchResult_comUrlNull_deveRetornarDominioNull() {
        var item = SerpResultItem.fromSearchResult(5, null, "Título", "Snippet");
        assertNull(item.domain());
        assertNull(item.fileType());
    }

    @Test
    void fromSearchResult_comUrlBlank_deveRetornarDominioNull() {
        var item = SerpResultItem.fromSearchResult(6, "   ", "Título", "Snippet");
        assertNull(item.domain());
    }

    @Test
    void fromSearchResult_comPdf_deveExtrairFileType() {
        var item = SerpResultItem.fromSearchResult(7, "https://exemplo.com/documento.pdf", "PDF", "Snippet");
        assertEquals("pdf", item.fileType());
    }

    @Test
    void fromSearchResult_comDocx_deveExtrairFileType() {
        var item = SerpResultItem.fromSearchResult(8, "https://exemplo.com/doc.docx", "DOCX", "Snippet");
        assertEquals("docx", item.fileType());
    }

    @Test
    void fromSearchResult_comHtml_deveIgnorarFileType() {
        var item = SerpResultItem.fromSearchResult(9, "https://exemplo.com/page.html", "HTML", "Snippet");
        assertNull(item.fileType());
    }

    @Test
    void fromSearchResult_comPhp_deveIgnorarFileType() {
        var item = SerpResultItem.fromSearchResult(10, "https://exemplo.com/page.php", "PHP", "Snippet");
        assertNull(item.fileType());
    }

    @Test
    void fromSearchResult_comAspx_deveIgnorarFileType() {
        var item = SerpResultItem.fromSearchResult(11, "https://exemplo.com/page.aspx", "ASPX", "Snippet");
        assertNull(item.fileType());
    }

    @Test
    void fromSearchResult_comUrlComPath_deveExtrairCorretamente() {
        var item = SerpResultItem.fromSearchResult(12, "https://www.exemplo.com/caminho/pagina.html?q=abc#frag", "Título", "Snippet");
        assertEquals("exemplo.com", item.domain());
        assertNull(item.fileType());
    }

    @Test
    void fromSearchResult_comUrlXls_deveExtrairFileType() {
        var item = SerpResultItem.fromSearchResult(13, "https://exemplo.com/planilha.xls", "XLS", "Snippet");
        assertEquals("xls", item.fileType());
    }

    @Test
    void fromSearchResult_comUrlSemExtensao_deveRetornarFileTypeNull() {
        var item = SerpResultItem.fromSearchResult(14, "https://exemplo.com/pagina", "Título", "Snippet");
        assertNull(item.fileType());
    }

    @Test
    void fromSearchResult_comHtmlExtension_deveRetornarNull() {
        var item = SerpResultItem.fromSearchResult(15, "https://exemplo.com/page.html", "HTML", "Snippet");
        assertNull(item.fileType());
    }

    @Test
    void fromSearchResult_comJsp_deveIgnorarFileType() {
        var item = SerpResultItem.fromSearchResult(16, "https://exemplo.com/index.jsp", "JSP", "Snippet");
        assertNull(item.fileType());
    }
}
