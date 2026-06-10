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

/**
 * Controlador REST da API de Leads.
 * <p>
 * Endpoints para enriquecimento, listagem, consulta, atualização e
 * remoção de leads (com suporte a soft delete para LGPD).
 * <p>
 * Todos os endpoints exigem a header {@code X-API-KEY} para autenticação.
 *
 * @see LeadService
 * @see LeadRequest
 * @see LeadResponse
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;

    /**
     * Enriquece um lead com dados do domínio (DNS, RDAP, tecnologias,
     * redes sociais) ou via OpenSERP se não houver domínio.
     * <p>
     * Retorna todos os leads do mesmo domínio do lead enriquecido.
     *
     * @param request dados do lead (nome obrigatório, email e domínio opcionais)
     * @return 200 com lista de leads do domínio, ou 400 se validação falhar
     */
    @PostMapping("/enrich")
    public ResponseEntity<List<LeadResponse>> enrichLead(@Valid @RequestBody LeadRequest request) {
        log.info("POST /enrich email={} name={} domain={}",
                request.getEmail(), request.getName(), request.getDomain());
        var enriched = leadService.enrich(request.getEmail(), request.getDomain(), request.getName());

        String domain = enriched.getDomain();
        if (domain != null && !domain.isBlank()) {
            var allFromDomain = leadService.findByDomain(domain).stream()
                    .map(LeadResponse::fromEntity)
                    .toList();
            log.info("Domínio '{}' possui {} lead(s) no total", domain, allFromDomain.size());
            return ResponseEntity.ok(allFromDomain);
        }

        return ResponseEntity.ok(List.of(LeadResponse.fromEntity(enriched)));
    }

    /**
     * Lista todos os leads enriquecidos (exclui soft-deleted).
     *
     * @return 200 com lista de leads ativos
     */
    @GetMapping
    public ResponseEntity<List<LeadResponse>> listAll() {
        var leads = leadService.listAll().stream()
                .map(LeadResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(leads);
    }

    /**
     * Retorna todos os leads ativos de um domínio específico.
     *
     * @param domain domínio para filtrar
     * @return 200 com leads do domínio, ou 204 se nenhum encontrado
     */
    @GetMapping("/domain/{domain}")
    public ResponseEntity<List<LeadResponse>> getLeadsByDomain(@PathVariable String domain) {
        log.info("GET /domain/{}", domain);
        var leads = leadService.findByDomain(domain).stream()
                .map(LeadResponse::fromEntity)
                .toList();
        if (leads.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(leads);
    }

    /**
     * Atualiza os dados de um lead existente e reenriquece.
     *
     * @param id      ID do lead
     * @param request novos dados (nome obrigatório, email e domínio opcionais)
     * @return 200 com lead atualizado, ou 404 se não encontrado
     */
    @PutMapping("/{id}")
    public ResponseEntity<LeadResponse> updateLead(
            @PathVariable String id,
            @Valid @RequestBody LeadRequest request) {
        log.info("PUT /{} name={} email={}", id, request.getName(), request.getEmail());
        var updated = leadService.update(id, request.getEmail(), request.getDomain(), request.getName());
        return ResponseEntity.ok(LeadResponse.fromEntity(updated));
    }

    /**
     * Retorna os dados completos de um lead pelo ID.
     *
     * @param id ID do lead
     * @return 200 com dados do lead, ou 404 se não encontrado
     */
    @GetMapping("/{id}")
    public ResponseEntity<LeadResponse> getLeadById(@PathVariable String id) {
        return leadService.findById(id)
                .map(lead -> ResponseEntity.ok(LeadResponse.fromEntity(lead)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Soft delete do lead (LGPD — direito ao esquecimento).
     * O registro é mantido no banco com status DELETED.
     *
     * @param id ID do lead a ser excluído
     * @return 200 com mensagem de confirmação, ou 404 se não encontrado
     */
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

