package solutions.pdroti.lead.enrichment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Requisição para enriquecimento de um lead")
public class LeadRequest {

    @Email
    @Schema(description = "Email do lead para enriquecimento (opcional)", example = "contato@exemplo.com")
    private String email;

    @Schema(description = "Domínio para validação DNS e scraping (opcional)", example = "exemplo.com")
    private String domain;

    @NotBlank
    @Schema(description = "Nome da pessoa para enriquecimento e buscas", example = "João Silva")
    private String name;
}
