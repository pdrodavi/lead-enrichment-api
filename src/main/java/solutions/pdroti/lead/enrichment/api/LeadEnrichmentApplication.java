package solutions.pdroti.lead.enrichment.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class LeadEnrichmentApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeadEnrichmentApplication.class, args);
    }
}
