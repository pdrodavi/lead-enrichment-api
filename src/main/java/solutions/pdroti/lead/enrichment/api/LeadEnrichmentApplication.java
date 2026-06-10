package solutions.pdroti.lead.enrichment.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Classe principal da aplicação Lead Enrichment API.
 * <p>
 * Aplicação Spring Boot para validação de leads e enriquecimento de dados
 * a partir de domínios (DNS, RDAP, scraping web, redes sociais).
 * <p>
 * O cache está habilitado via {@link EnableCaching} para operações
 * repetitivas de consulta DNS e RDAP.
 */
@SpringBootApplication
@EnableCaching
public class LeadEnrichmentApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeadEnrichmentApplication.class, args);
    }
}
