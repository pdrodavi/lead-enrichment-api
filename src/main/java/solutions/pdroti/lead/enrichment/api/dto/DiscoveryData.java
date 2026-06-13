package solutions.pdroti.lead.enrichment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Sub-record que agrupa todos os dados de descoberta de um lead:
 * tecnologias, redes sociais, e-mails expostos, menções ao nome,
 * documentos encontrados e resultado da busca no OpenSERP.
 */
@Schema(description = "Dados de descoberta do lead (tecnologias, redes sociais, menções, etc.)")
public record DiscoveryData(

        @Schema(description = "Tecnologias detectadas no domínio")
        List<String> technologies,

        @Schema(description = "Links de redes sociais encontrados")
        List<String> socialLinks,

        @Schema(description = "Resumo dos dados scrapy dos perfis de redes sociais")
        List<String> socialProfileSummaries,

        @Schema(description = "E-mails expostos encontrados")
        List<String> exposedEmails,

        @Schema(description = "Menções ao nome da pessoa encontradas na página")
        List<String> nameMentions,

        @Schema(description = "URLs onde o nome da pessoa foi encontrado (extraídas das menções)")
        List<String> nameMentionUrls,

        @Schema(description = "Total de achados (emails + menções)")
        int dorkFindings,

        @Schema(description = "Links para documentos encontrados (PDF, DOC, XLS, PPT, etc.)",
                example = "[\"https://example.com/curriculo.pdf\"]")
        List<String> foundDocuments,

        @Schema(description = "Todos os URLs descobertos (inclui sociais e não-sociais)",
                example = "[\"https://example.com\", \"https://github.com/pdroti\"]")
        List<String> discoveredUrls,

        @Schema(description = "Resultado estruturado da busca no OpenSERP")
        SerpSearchResult openSerpRawData
) {

    /** Retorna um DiscoveryData vazio (sem dados). */
    public static DiscoveryData empty() {
        return new DiscoveryData(
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), 0,
                List.of(), List.of(), null
        );
    }
}
