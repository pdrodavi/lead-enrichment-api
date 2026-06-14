package solutions.pdroti.lead.enrichment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import solutions.pdroti.lead.enrichment.api.model.Lead;
import solutions.pdroti.lead.enrichment.api.util.EmailUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Resumo leve de um lead para listagens.
 * <p>
 * Diferente de {@link LeadResponse}, este DTO <b>não</b> serializa
 * os JSONs brutos ({@code openSerpRawData}, {@code rdapRawData}),
 * evitando parseamentos caros de {@code ObjectMapper.readTree/readValue}
 * que tornavam a listagem lenta.
 * <p>
 * Contém apenas campos simples e contagens derivadas.
 */
@Schema(description = "Resumo leve do lead para listagens (sem JSONs brutos)")
public record LeadResponseSummary(

        @Schema(description = "ID único do lead", example = "1")
        Long id,

        @Schema(description = "Email mascarado (LGPD)", example = "con***@exemplo.com")
        String emailMasked,

        @Schema(description = "Nome da pessoa", example = "João Silva")
        String name,

        @Schema(description = "Domínio validado", example = "exemplo.com")
        String domain,

        @Schema(description = "Status do processamento", example = "ACTIVE")
        String status,

        @Schema(description = "Possui registros MX?", example = "true")
        boolean mxStatus,

        @Schema(description = "Quantidade de e-mails expostos encontrados", example = "3")
        int dorkFindings,

        @Schema(description = "Quantidade de tecnologias detectadas", example = "5")
        int technologiesCount,

        @Schema(description = "Quantidade de links de redes sociais", example = "2")
        int socialLinksCount,

        @Schema(description = "Quantidade de documentos encontrados", example = "1")
        int documentsCount,

        @Schema(description = "Quantidade de menções ao nome", example = "4")
        int mentionsCount,

        @Schema(description = "Data de criação", example = "2026-06-13T10:00:00")
        LocalDateTime createdAt
) {

    /**
     * Cria um resumo leve a partir do lead, sem parsear JSONs.
     *
     * @param lead entidade Lead persistida
     * @return LeadResponseSummary com dados básicos e contagens
     */
    public static LeadResponseSummary fromEntity(Lead lead) {
        return new LeadResponseSummary(
                lead.getId(),
                EmailUtils.mask(lead.getEmail()),
                lead.getName(),
                lead.getDomain(),
                lead.getStatus(),
                lead.getMxStatus(),
                lead.getDorkFindings(),
                size(lead.getTechnologies()),
                size(lead.getSocialLinks()),
                size(lead.getFoundDocuments()),
                size(lead.getNameMentions()),
                lead.getCreatedAt()
        );
    }

    private static int size(List<?> list) {
        return list != null ? list.size() : 0;
    }
}
