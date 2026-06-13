package solutions.pdroti.lead.enrichment.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import solutions.pdroti.lead.enrichment.api.dto.LeadRequest;
import solutions.pdroti.lead.enrichment.api.dto.LeadResponse;
import solutions.pdroti.lead.enrichment.api.service.LeadDeletionService;
import solutions.pdroti.lead.enrichment.api.service.LeadService;
import solutions.pdroti.lead.enrichment.api.util.EmailUtils;

import java.util.List;
import java.util.Map;

/**
 * Controlador REST da API de Leads.
 * <p>
 * Endpoints para enriquecimento, listagem, consulta, atualização e
 * remoção permanente de leads.
 * <p>
 * Todos os endpoints exigem a header {@code X-API-KEY} para autenticação.
 *
 * @see LeadService
 * @see LeadDeletionService
 * @see LeadRequest
 * @see LeadResponse
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;
    private final LeadDeletionService leadDeletionService;
    private final ObjectMapper objectMapper;

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
                EmailUtils.mask(request.getEmail()), request.getName(), request.getDomain());
        var enriched = leadService.enrich(request.getEmail(), request.getDomain(), request.getName());
        var enrichedResponse = LeadResponse.fromEntity(enriched, objectMapper);

        String domain = enriched.getDomain();
        if (domain != null && !domain.isBlank()) {
            var allFromDomain = leadService.findByDomain(domain).stream()
                    .map(lead -> LeadResponse.fromEntity(lead, objectMapper))
                    .toList();
            log.info("Domínio '{}' possui {} lead(s) no total", domain, allFromDomain.size());
            // Garante que pelo menos o lead enriquecido esteja na resposta
            if (allFromDomain.isEmpty()) {
                log.warn("Lead recém-enriquecido não encontrado em findByDomain — retornando apenas ele");
                return ResponseEntity.ok(List.of(enrichedResponse));
            }
            return ResponseEntity.ok(allFromDomain);
        }

        return ResponseEntity.ok(List.of(enrichedResponse));
    }

    /**
     * Lista todos os leads com status ACTIVE.
     *
     * @return 200 com lista de leads ativos
     */
    @GetMapping
    public ResponseEntity<List<LeadResponse>> listAll() {
        var leads = leadService.listAll().stream()
                .map(lead -> LeadResponse.fromEntity(lead, objectMapper))
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
                .map(lead -> LeadResponse.fromEntity(lead, objectMapper))
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
        log.info("PUT /{} name={} email={}", id, request.getName(), EmailUtils.mask(request.getEmail()));
        var updated = leadService.update(id, request.getEmail(), request.getDomain(), request.getName());
        return ResponseEntity.ok(LeadResponse.fromEntity(updated, objectMapper));
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
                .map(lead -> ResponseEntity.ok(LeadResponse.fromEntity(lead, objectMapper)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Exclui permanentemente um lead do banco de dados (hard delete).
     * O registro é removido fisicamente — não é um soft delete.
     *
     * @param id ID do lead a ser excluído
     * @return 200 com mensagem de confirmação, ou 404 se não encontrado
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteLead(@PathVariable String id) {
        boolean deleted = leadDeletionService.hardDelete(id);
        if (deleted) {
            log.info("DELETE /{} permanently deleted", id);
            return ResponseEntity.ok(Map.of(
                    "message", "Lead excluído permanentemente do banco de dados",
                    "lgpdMessage", "Lead excluído com sucesso (LGPD — direito ao esquecimento)",
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

