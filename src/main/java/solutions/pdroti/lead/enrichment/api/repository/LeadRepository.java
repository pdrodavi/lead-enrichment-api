package solutions.pdroti.lead.enrichment.api.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import solutions.pdroti.lead.enrichment.api.model.Lead;

import java.util.List;
import java.util.Optional;

/**
 * Repositório JPA para a entidade {@link Lead}.
 * <p>
 * NOTA: O campo {@code email} é criptografado no banco (AES-GCM),
 * portanto consultas como {@code findByEmail} NÃO funcionam —
 * use {@link #findByEmailHash(String)} para buscar por e-mail.
 * <p>
 * As {@code @ElementCollection} usam {@code @Fetch(FetchMode.SUBSELECT)}
 * para carregar todas as coleções com uma única subquery, evitando
 * N+1 sem causar {@code MultipleBagFetchException}.
 */
public interface LeadRepository extends JpaRepository<Lead, Long> {

    Optional<Lead> findByEmailHash(String emailHash);

    Optional<Lead> findByName(String name);

    @Override
    Optional<Lead> findById(Long id);

    Page<Lead> findByStatus(String status, Pageable pageable);

    Page<Lead> findByDomainAndStatus(String domain, String status, Pageable pageable);

    List<Lead> findByDomainAndStatus(String domain, String status);

    /** Busca todos os leads com determinado status (sem paginação — uso interno). */
    List<Lead> findByStatus(String status);
}
