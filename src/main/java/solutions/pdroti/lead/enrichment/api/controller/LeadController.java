package solutions.pdroti.lead.enrichment.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import solutions.pdroti.lead.enrichment.api.dto.LeadRequest;
import solutions.pdroti.lead.enrichment.api.dto.LeadResponse;
import solutions.pdroti.lead.enrichment.api.service.LeadService;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;

    /** Enriquece um lead com dados do domínio (MX, tecnologias, redes sociais, Google Dorks). */
    @PostMapping("/enrich")
    public ResponseEntity<LeadResponse> enrichLead(@Valid @RequestBody LeadRequest request) {
        log.info("POST /enrich email={}", request.getEmail());
        var enriched = leadService.enrich(request.getEmail(), request.getDomain());
        return ResponseEntity.ok(LeadResponse.fromEntity(enriched));
    }

    /** Lista todos os leads enriquecidos. */
    @GetMapping
    public ResponseEntity<List<LeadResponse>> listAll() {
        var leads = leadService.listAll().stream()
                .map(LeadResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(leads);
    }

    /** Retorna lead por ID com dados de tecnologias, sociais e Dorks (persistidos). */
    @GetMapping("/{id}")
    public ResponseEntity<LeadResponse> getLeadById(@PathVariable String id) {
        return leadService.findById(id)
                .map(lead -> ResponseEntity.ok(LeadResponse.fromEntity(lead)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Soft delete do lead (LGPD — direito ao esquecimento). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteLead(@PathVariable String id) {
        boolean deleted = leadService.softDelete(id);
        if (deleted) {
            log.info("DELETE /{} soft deleted", id);
            return ResponseEntity.ok(Map.of(
                    "message", "Lead excluído com sucesso (LGPD — direito ao esquecimento)",
                    "id", id
            ));
        }
        log.warn("DELETE /{} not found", id);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "Lead não encontrado",
                "id", id
        ));
    }
}

