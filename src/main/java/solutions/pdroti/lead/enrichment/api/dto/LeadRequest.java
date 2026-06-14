package solutions.pdroti.lead.enrichment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO de requisição para enriquecimento ou atualização de um lead.
 * <p>
 * {@code email} e {@code name} são obrigatórios. Se {@code domain}
 * não for informado, é extraído automaticamente do e-mail.
 */
@Data
@Schema(description = "Requisição para enriquecimento de um lead")
public class LeadRequest {

    @NotBlank
    @Email
    @Schema(description = "Email do lead (obrigatório — identificador único)", example = "contato@exemplo.com")
    private String email;

    @Schema(description = "Domínio para validação DNS e scraping (opcional, extraído do email se ausente)", example = "exemplo.com")
    private String domain;

    @NotBlank
    @Size(min = 3, message = "Nome deve ter pelo menos 3 caracteres")
    @Schema(description = "Nome da pessoa (obrigatório, mín. 3 caracteres)", example = "João Silva")
    private String name;
}
