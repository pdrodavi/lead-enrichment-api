package solutions.pdroti.lead.enrichment.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.Type;

@Slf4j
@Service
public class DnsValidationService {

    public boolean hasMxRecord(String domain) {
        if (domain == null || domain.isBlank()) {
            return false;
        }

        try {
            Record[] records = new Lookup(domain, Type.MX).run();
            return records != null && records.length > 0;
        } catch (Exception e) {
            log.warn("Falha ao verificar MX para {}: {}", domain, e.getMessage());
            return false;
        }
    }
}
