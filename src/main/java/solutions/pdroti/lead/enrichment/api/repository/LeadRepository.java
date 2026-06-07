package solutions.pdroti.lead.enrichment.api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import solutions.pdroti.lead.enrichment.api.model.Lead;

@Repository
public interface LeadRepository extends JpaRepository<Lead, Long> {

    Optional<Lead> findByEmail(String email);

    java.util.List<Lead> findByStatus(String status);
}
