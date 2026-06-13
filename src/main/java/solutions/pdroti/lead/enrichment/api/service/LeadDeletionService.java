package solutions.pdroti.lead.enrichment.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solutions.pdroti.lead.enrichment.api.model.Lead;
import solutions.pdroti.lead.enrichment.api.repository.LeadRepository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Serviço responsável pela exclusão de leads.
 * <p>
 * O método principal é {@link #hardDelete(String)}, que remove fisicamente
 * o registro do banco (1 query via {@code deleteById}). O método
 * {@link #softDelete(String)} é mantido para retrocompatibilidade.
 * <p>
 * Extraído do {@code LeadService} para manter a responsabilidade única (SRP).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeadDeletionService {

    private final LeadRepository leadRepository;

    static final String DELETED_STATUS = "DELETED";

    /**
     * Soft delete: marca o lead como DELETED.
     * O registro permanece no banco (status = DELETED, deletedAt preenchido),
     * mas é ocultado das consultas padrão.
     *
     * @param id ID do lead a ser marcado como deletado
     * @return true se o lead foi encontrado e deletado, false caso contrário
     */
    @Transactional
    public boolean softDelete(String id) {
        return parseNumericId(id)
                .flatMap(leadRepository::findById)
                .map(this::performSoftDelete)
                .orElse(false);
    }

    /**
     * Hard delete: remove fisicamente o registro do banco de dados.
     * Usa {@code deleteById} com try-catch para {@link EmptyResultDataAccessException}.
     *
     * @param id ID do lead a ser removido permanentemente
     * @return true se o lead foi removido, false caso contrário
     */
    @Transactional
    public boolean hardDelete(String id) {
        return parseNumericId(id)
                .map(idLong -> {
                    try {
                        leadRepository.deleteById(idLong);
                        log.info("Lead hard deleted: ID={}", idLong);
                        return true;
                    } catch (org.springframework.dao.EmptyResultDataAccessException e) {
                        log.warn("Lead não encontrado para hard delete: ID={}", idLong);
                        return false;
                    }
                }).orElse(false);
    }

    /**
     * Executa o soft delete: marca data/hora de exclusão e altera status para DELETED.
     * O registro permanece no banco para fins de auditoria e recuperação.
     *
     * @param lead entidade a ser marcada como deletada
     * @return sempre true
     */
    private boolean performSoftDelete(Lead lead) {
        lead.setUpdatedAt(LocalDateTime.now());
        lead.setDeletedAt(LocalDateTime.now());
        lead.setStatus(DELETED_STATUS);
        leadRepository.save(lead);
        log.info("Lead soft deleted: ID={}", lead.getId());
        return true;
    }

    /**
     * Converte um ID em formato string para Long.
     * Retorna Optional.empty() se o formato for inválido, com log de aviso.
     *
     * @param id identificador em formato string
     * @return Optional com o Long parseado, ou vazio se inválido
     */
    static Optional<Long> parseNumericId(String id) {
        try {
            return Optional.of(Long.parseLong(id));
        } catch (NumberFormatException e) {
            log.warn("ID inválido (deve ser numérico): {}", id);
            return Optional.empty();
        }
    }
}
