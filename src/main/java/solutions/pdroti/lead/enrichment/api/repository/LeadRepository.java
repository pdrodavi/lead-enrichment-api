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
 */
public interface LeadRepository extends JpaRepository<Lead, Long> {

    /**
     * Busca lead pelo hash SHA-256 do e-mail.
     *
     * @param emailHash hash SHA-256 do e-mail (hexadecimal)
     * @return lead encontrado, ou vazio
     */
    Optional<Lead> findByEmailHash(String emailHash);

    /** Busca lead pelo nome (usado para atualizar lead existente). */
    Optional<Lead> findByName(String name);

    /** Busca paginada de leads com determinado status (ex: ACTIVE, DELETED). */
    Page<Lead> findByStatus(String status, Pageable pageable);

    /** Busca paginada de leads ativos com o domínio informado. */
    Page<Lead> findByDomainAndStatus(String domain, String status, Pageable pageable);

    /** Busca todos os leads com determinado status (sem paginação — uso interno). */
    List<Lead> findByStatus(String status);
}
