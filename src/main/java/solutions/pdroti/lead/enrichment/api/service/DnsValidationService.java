package solutions.pdroti.lead.enrichment.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.Type;
import solutions.pdroti.lead.enrichment.api.dto.DnsResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Serviço de validação DNS para domínios.
 * <p>
 * Utiliza a biblioteca dnsjava para consultar registros DNS reais:
 * MX, A (IPv4), AAAA (IPv6), CNAME e TXT.
 * Cada consulta é feita de forma independente com try-catch individual,
 * garantindo que falhas em um tipo não impeçam os demais.
 */
@Slf4j
@Service
public class DnsValidationService {

    /**
     * Verifica se o domínio possui pelo menos um registro MX válido.
     * Usado como indicador de que o domínio pode receber e-mails.
     *
     * @param domain domínio a ser consultado (ex: "exemplo.com")
     * @return true se houver ao menos um registro MX
     */
    public boolean hasMxRecord(String domain) {
        return lookupDomain(domain).hasMx();
    }

    /**
     * Consulta todos os tipos de registro DNS do domínio:
     * MX (mail exchange), A (IPv4), AAAA (IPv6), CNAME (alias) e TXT (SPF, DKIM).
     * <p>
     Cada consulta é isolada em try-catch — falhas em um tipo não afetam os outros.
     *
     * @param domain domínio a ser consultado
     * @return {@link DnsResult} com todos os registros encontrados
     */
    public DnsResult lookupDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return DnsResult.empty();
        }

        List<String> mxRecords = new ArrayList<>();
        List<String> aRecords = new ArrayList<>();
        List<String> aaaaRecords = new ArrayList<>();
        List<String> cnameRecords = new ArrayList<>();
        List<String> txtRecords = new ArrayList<>();

        // Consulta MX (mail exchange) — indica se o domínio pode receber e-mails
        lookupRecords(domain, Type.MX, mxRecords);

        // Consulta A (IPv4) — resolução de endereço IPv4
        lookupRecords(domain, Type.A, aRecords);

        // Consulta AAAA (IPv6) — resolução de endereço IPv6
        lookupRecords(domain, Type.AAAA, aaaaRecords);

        // Consulta CNAME — alias canônico do domínio
        lookupRecords(domain, Type.CNAME, cnameRecords);

        // Consulta TXT — registros de texto (SPF, DKIM, DMARC, verificações)
        lookupTxtRecords(domain, txtRecords);

        boolean hasMx = !mxRecords.isEmpty();

        if (hasMx || !aRecords.isEmpty() || !aaaaRecords.isEmpty()
                || !cnameRecords.isEmpty() || !txtRecords.isEmpty()) {
            log.info("DNS para {}: {} MX, {} A, {} AAAA, {} CNAME, {} TXT",
                    domain, mxRecords.size(), aRecords.size(), aaaaRecords.size(),
                    cnameRecords.size(), txtRecords.size());
        }

        return new DnsResult(hasMx, mxRecords, aRecords, aaaaRecords, cnameRecords, txtRecords);
    }

    /**
     * Executa uma consulta DNS genérica para o tipo especificado.
     *
     * @param domain  domínio a consultar
     * @param type    tipo de registro (Type.MX, Type.A, etc.)
     * @param results lista onde os resultados serão adicionados
     */
    private void lookupRecords(String domain, int type, List<String> results) {
        try {
            Record[] records = new Lookup(domain, type).run();
            if (records != null) {
                for (Record r : records) {
                    results.add(r.rdataToString());
                }
            }
        } catch (Exception e) {
            log.debug("Falha ao consultar tipo {} para {}: {}", Type.string(type), domain, e.getMessage());
        }
    }

    /**
     * Executa consulta TXT com tratamento especial para extrair strings
     * de objetos {@link TXTRecord}.
     */
    private void lookupTxtRecords(String domain, List<String> results) {
        try {
            Record[] records = new Lookup(domain, Type.TXT).run();
            if (records != null) {
                for (Record r : records) {
                    if (r instanceof TXTRecord txt) {
                        txt.getStrings().forEach(results::add);
                    } else {
                        results.add(r.rdataToString());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Falha TXT para {}: {}", domain, e.getMessage());
        }
    }
}
