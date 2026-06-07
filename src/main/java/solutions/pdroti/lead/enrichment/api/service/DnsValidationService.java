package solutions.pdroti.lead.enrichment.api.service;

import org.springframework.stereotype.Service;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.Type;

@Service
public class DnsValidationService {
    public boolean hasMxRecord(String domain) {
        try {
            Record[] records = new Lookup(domain, Type.MX).run();
            return records != null && records.length > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
