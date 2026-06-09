package solutions.pdroti.lead.enrichment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import solutions.pdroti.lead.enrichment.api.model.Lead;
import solutions.pdroti.lead.enrichment.api.util.EmailUtils;

import java.util.List;

@Schema(description = "Resposta com dados enriquecidos do lead (email mascarado por LGPD)")
public record LeadResponse(
        @Schema(description = "ID único do lead", example = "1")
        Long id,

        @Schema(description = "Email mascarado (LGPD)", example = "con***@exemplo.com")
        String emailMasked,

        @Schema(description = "Nome da pessoa", example = "João Silva")
        String name,

        @Schema(description = "Domínio validado", example = "exemplo.com")
        String domain,

        @Schema(description = "Status do registro MX", example = "true")
        boolean mxStatus,

        @Schema(description = "Status do processamento", example = "ENRICHED")
        String status,

        @Schema(description = "Tecnologias detectadas no domínio")
        List<String> technologies,

        @Schema(description = "Links de redes sociais encontrados")
        List<String> socialLinks,

        @Schema(description = "Resumo dos dados scrapy dos perfis de redes sociais")
        List<String> socialProfileSummaries,

        // === Dados do Google Dorks (persistidos) ===

        @Schema(description = "E-mails expostos encontrados (Google Dorks)")
        List<String> exposedEmails,

        @Schema(description = "Telefones expostos encontrados")
        List<String> exposedPhones,

        @Schema(description = "Caminhos administrativos expostos")
        List<String> exposedAdminPaths,

        @Schema(description = "Documentos expostos (.pdf, .docx, etc.)")
        List<String> exposedDocuments,

        @Schema(description = "Arquivos de configuração expostos (.env, .sql, .bak)")
        List<String> exposedConfigFiles,

        @Schema(description = "Menções ao nome da pessoa encontradas na página")
        List<String> nameMentions,

        @Schema(description = "Total de achados no Dorks scan")
        int dorkFindings
) {

    /** Cria resposta a partir do lead salvo (dorks já inclusos na entidade). */
    public static LeadResponse fromEntity(Lead lead) {
        return new LeadResponse(
                lead.getId(),
                maskEmail(lead.getEmail()),
                lead.getName(),
                lead.getDomain(),
                lead.getMxStatus(),
                lead.getStatus(),
                lead.getTechnologies(),
                lead.getSocialLinks(),
                lead.getSocialProfileSummaries(),
                lead.getExposedEmails(),
                lead.getExposedPhones(),
                lead.getExposedAdminPaths(),
                lead.getExposedDocuments(),
                lead.getExposedConfigFiles(),
                lead.getNameMentions(),
                lead.getDorkFindings()
        );
    }

    private static String maskEmail(String email) {
        return EmailUtils.mask(email);
    }
}
