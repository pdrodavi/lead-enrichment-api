package solutions.pdroti.lead.enrichment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Requisição para enriquecimento de um lead")
public class LeadRequest {

    @NotBlank @Email
    @Schema(description = "Email do lead para enriquecimento", example = "contato@exemplo.com")
    private String email;

    @NotBlank
    @Schema(description = "Domínio para validação DNS e scraping", example = "exemplo.com")
    private String domain;
}
