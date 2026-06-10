package solutions.pdroti.lead.enrichment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO com dados extraídos do scraping de um perfil de rede social.
 * <p>
 * Após descobrir URLs de redes sociais no domínio do lead,
 * cada perfil é scrapy para obter título e descrição.
 *
 * @param url         URL do perfil (ex: "https://github.com/pdroti")
 * @param platform    Nome da plataforma (ex: "GitHub")
 * @param title       Título da página do perfil
 * @param description Descrição/meta do perfil
 */
@Schema(description = "Dados extraídos do scraping de um perfil de rede social")
public record SocialProfileData(

        @Schema(description = "URL do perfil", example = "https://github.com/pdroti")
        String url,

        @Schema(description = "Nome da plataforma", example = "GitHub")
        String platform,

        @Schema(description = "Título da página do perfil", example = "pdroti (Pedro) · GitHub")
        String title,

        @Schema(description = "Descrição/meta do perfil", example = "Desenvolvedor full-stack Java e Angular")
        String description
) {

    /** Comprimento máximo do summary (VARCHAR(255) - margem). */
    private static final int MAX_SUMMARY_LENGTH = 252;

    /** Comprimento mínimo para adicionar URL ao summary. */
    private static final int MIN_LENGTH_FOR_URL = 100;

    /**
     * Formata os dados do perfil como string resumida para persistência.
     * O resultado é truncado em ~252 caracteres para caber em VARCHAR(255).
     * <p>
     * Formato: {@code Plataforma: Título — Descrição (URL)}
     *
     * @return string formatada (nunca null)
     */
    public String toSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(platform).append(": ");

        if (title != null && !title.isBlank()) {
            sb.append(title);
        }
        if (description != null && !description.isBlank()) {
            sb.append(" — ").append(description);
        }

        // Só adiciona URL se o summary ainda estiver curto
        if (sb.length() < MIN_LENGTH_FOR_URL) {
            sb.append(" (").append(url).append(")");
        }

        // Trunca para caber no VARCHAR(255) do banco
        if (sb.length() > MAX_SUMMARY_LENGTH) {
            sb.setLength(MAX_SUMMARY_LENGTH);
            sb.append("...");
        }

        return sb.toString();
    }
}
