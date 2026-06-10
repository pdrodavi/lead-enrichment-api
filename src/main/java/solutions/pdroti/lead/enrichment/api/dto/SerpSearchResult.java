package solutions.pdroti.lead.enrichment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Resultado estruturado da busca no OpenSERP (Google Search).
 * <p>
 * Substitui o JSON bruto anterior por um objeto tipado com
 * metadados da consulta e a lista de resultados individuais.
 */
@Schema(description = "Resultado estruturado da busca no Google via OpenSERP")
public record SerpSearchResult(

        @Schema(description = "Termo buscado", example = "João Silva")
        String query,

        @Schema(description = "Quantidade de resultados que correspondem ao nome")
        int totalResults,

        @Schema(description = "Lista de resultados da busca")
        List<SerpResultItem> items
) {

    /** Retorna um resultado vazio (sem dados). */
    public static SerpSearchResult empty(String query) {
        return new SerpSearchResult(query, 0, List.of());
    }
}
