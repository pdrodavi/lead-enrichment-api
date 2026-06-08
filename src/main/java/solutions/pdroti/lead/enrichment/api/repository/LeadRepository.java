package solutions.pdroti.lead.enrichment.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import solutions.pdroti.lead.enrichment.api.model.Lead;

import java.util.List;
import java.util.Optional;

/** Repositório JPA para a entidade {@link Lead}. */
public interface LeadRepository extends JpaRepository<Lead, Long> {

    /** Busca lead pelo e-mail (usado no fluxo de enrichment). */
    Optional<Lead> findByEmail(String email);

    /** Busca todos os leads com determinado status (ex: ACTIVE, DELETED). */
    List<Lead> findByStatus(String status);
}
