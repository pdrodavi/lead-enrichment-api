package solutions.pdroti.lead.enrichment.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.cache.annotation.Cacheable;
import solutions.pdroti.lead.enrichment.api.dto.LeadRequest;
import solutions.pdroti.lead.enrichment.api.dto.LeadResponse;
import solutions.pdroti.lead.enrichment.api.dto.LeadResponseSummary;
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
 * <b>Cache:</b>
 * <ul>
 *   <li>{@code POST /enrich} — {@code @Cacheable("enrich-result")} por email (TTL 24h)</li>
 *   <li>{@code PUT /{id}} — evict manual do cache do email antigo + novo</li>
 *   <li>{@code GET /} — retorna {@link LeadResponseSummary} (sem parse de JSONs brutos)</li>
 * </ul>
 * <p>
 * Todos os endpoints exigem a header {@code X-API-KEY} para autenticação.
 *
 * @see LeadService
 * @see LeadDeletionService
 * @see LeadRequest
 * @see LeadResponse
 * @see LeadResponseSummary
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;
    private final LeadDeletionService leadDeletionService;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

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
    @Cacheable(value = "enrich-result", key = "#request.email")
    public ResponseEntity<List<LeadResponse>> enrichLead(@Valid @RequestBody LeadRequest request) {
        log.info("POST /enrich email={} name={} domain={}",
                EmailUtils.mask(request.getEmail()), request.getName(), request.getDomain());
        var result = leadService.enrichWithDomainLeads(
                request.getEmail(), request.getDomain(), request.getName());
        var enrichedResponse = LeadResponse.fromEntity(result.enriched(), objectMapper);

        // Reaproveita os leads do domínio já retornados pelo service
        // (evita uma segunda consulta ao banco)
        if (!result.domainLeads().isEmpty()) {
            var allFromDomain = result.domainLeads().stream()
                    .map(lead -> LeadResponse.fromEntity(lead, objectMapper))
                    .toList();
            log.debug("Domínio '{}' possui {} lead(s) no total",
                    result.enriched().getDomain(), allFromDomain.size());
            return ResponseEntity.ok(allFromDomain);
        }

        return ResponseEntity.ok(List.of(enrichedResponse));
    }

    /**
     * Lista todos os leads com status ACTIVE (paginado).
     * Retorna resumo leve ({@link LeadResponseSummary}) sem JSONs brutos
     * para evitar parseamentos caros de {@code ObjectMapper} na listagem.
     *
     * @param pageable parâmetros de paginação (page, size, sort)
     * @return 200 com página de resumos de leads ativos
     */
    @GetMapping
    public ResponseEntity<Page<LeadResponseSummary>> listAll(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        var page = leadService.listAll(pageable)
                .map(LeadResponseSummary::fromEntity);
        return ResponseEntity.ok(page);
    }

    /**
     * Retorna todos os leads ativos de um domínio específico (paginado).
     *
     * @param domain   domínio para filtrar
     * @param pageable parâmetros de paginação (page, size, sort)
     * @return 200 com página de leads do domínio, ou 204 se nenhum encontrado
     */
    @GetMapping("/domain/{domain}")
    public ResponseEntity<Page<LeadResponse>> getLeadsByDomain(
            @PathVariable String domain,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        log.info("GET /domain/{} page={} size={}", domain,
                pageable.getPageNumber(), pageable.getPageSize());
        var page = leadService.findByDomain(domain, pageable)
                .map(lead -> LeadResponse.fromEntity(lead, objectMapper));
        if (page.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(page);
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

        // Evita cache stale: remove entrada do email ANTIGO antes de atualizar
        var oldLead = leadService.findById(id);
        oldLead.ifPresent(lead -> {
            var cache = cacheManager.getCache("enrich-result");
            if (cache != null) {
                cache.evict(lead.getEmail());
                log.debug("Cache evict para email antigo: {}", EmailUtils.mask(lead.getEmail()));
            }
        });

        var updated = leadService.update(id, request.getEmail(), request.getDomain(), request.getName());

        // Remove entrada do novo email (o @CacheEvict foi removido intencionalmente)
        var cache = cacheManager.getCache("enrich-result");
        if (cache != null) {
            cache.evict(request.getEmail());
        }

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
        // Evita cache stale: remove entrada do cache ANTES de deletar
        var oldLead = leadService.findById(id);
        oldLead.ifPresent(lead -> {
            var cache = cacheManager.getCache("enrich-result");
            if (cache != null) {
                cache.evict(lead.getEmail());
                log.debug("Cache evict para email do lead deletado: {}", EmailUtils.mask(lead.getEmail()));
            }
        });

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

