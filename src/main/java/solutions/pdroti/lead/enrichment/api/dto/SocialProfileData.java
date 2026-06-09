package solutions.pdroti.lead.enrichment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Dados extraídos do scraping de um perfil de rede social.
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

    private static final int MAX_SUMMARY_LENGTH = 252;

    /** Formata como string resumida para persistência (máx. 255 chars). */
    public String toSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(platform).append(": ");

        if (title != null && !title.isBlank()) {
            sb.append(title);
        }
        if (description != null && !description.isBlank()) {
            sb.append(" — ").append(description);
        }

        // Evita repetir a URL se ela já estiver contida no título
        if (sb.length() < 100) {
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
