package solutions.pdroti.lead.enrichment.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solutions.pdroti.lead.enrichment.api.repository.LeadRepository;

import java.util.Optional;

/**
 * Serviço responsável pela exclusão de leads.
 * <p>
 * O método principal é {@link #hardDelete(String)}, que remove fisicamente
 * o registro do banco (1 query via {@code deleteById}).
 * <p>
 * Extraído do {@code LeadService} para manter a responsabilidade única (SRP).
 */
@Slf4j
@Service
public class LeadDeletionService {

    private final LeadRepository leadRepository;

    static final String DELETED_STATUS = "DELETED";

    public LeadDeletionService(LeadRepository leadRepository) {
        this.leadRepository = leadRepository;
    }

    /**
     * Hard delete: remove fisicamente o registro do banco de dados.
     * Usa {@code deleteById} com try-catch para {@link EmptyResultDataAccessException}.
     *
     * @param id ID do lead a ser removido permanentemente
     * @return true se o lead foi removido, false caso contrário
     */
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
