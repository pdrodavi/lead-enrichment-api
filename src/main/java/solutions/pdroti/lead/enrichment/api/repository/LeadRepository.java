package solutions.pdroti.lead.enrichment.api.repository;

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
     * Este é o método correto para lookup por e-mail, já que o
     * e-mail em si é armazenado criptografado.
     *
     * @param emailHash hash SHA-256 do e-mail (hexadecimal)
     * @return lead encontrado, ou vazio
     */
    Optional<Lead> findByEmailHash(String emailHash);

    /** Busca lead pelo nome (usado para atualizar lead existente). */
    Optional<Lead> findByName(String name);

    /** Busca todos os leads com determinado status (ex: ACTIVE, DELETED). */
    List<Lead> findByStatus(String status);

    /** Busca todos os leads ativos com o domínio informado. */
    List<Lead> findByDomainAndStatus(String domain, String status);
}
