package solutions.pdroti.lead.enrichment.api.service;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.Type;
import solutions.pdroti.lead.enrichment.api.dto.DnsResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Serviço de validação DNS para domínios.
 * <p>
 * Utiliza a biblioteca dnsjava para consultar registros DNS reais:
 * MX, A (IPv4), AAAA (IPv6), CNAME e TXT.
 * <p>
 * Otimizações:
 * <ul>
 *   <li>Consultas paralelas — todos os 5 tipos rodam simultaneamente via
 *       {@link CompletableFuture} com pool de threads dedicado</li>
 *   <li>Cache Caffeine — resultados são cacheados por 1 hora</li>
 * </ul>
 */
@Slf4j
@Service
public class DnsValidationService {

    private final Cache<String, DnsResult> dnsCache;
    private final Executor enrichmentExecutor;

    public DnsValidationService(Cache<String, DnsResult> dnsCache,
                                 @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
                                 java.util.concurrent.Executor enrichmentExecutor) {
        this.dnsCache = dnsCache;
        this.enrichmentExecutor = enrichmentExecutor;
    }

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
     * Consulta todos os tipos de registro DNS do domínio em paralelo:
     * MX (mail exchange), A (IPv4), AAAA (IPv6), CNAME (alias) e TXT (SPF, DKIM).
     * Resultados são cacheados via Caffeine por 1 hora.
     *
     * @param domain domínio a ser consultado
     * @return {@link DnsResult} com todos os registros encontrados
     */
    public DnsResult lookupDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return DnsResult.empty();
        }

        // Tenta cache primeiro
        String cacheKey = domain.toLowerCase().strip();
        DnsResult cached = dnsCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("DNS cache hit para {}", domain);
            return cached;
        }

        // Consultas DNS paralelas — todas rodam simultaneamente
        var mxFuture = CompletableFuture.supplyAsync(
                () -> lookupRecordsList(domain, Type.MX), enrichmentExecutor);
        var aFuture = CompletableFuture.supplyAsync(
                () -> lookupRecordsList(domain, Type.A), enrichmentExecutor);
        var aaaaFuture = CompletableFuture.supplyAsync(
                () -> lookupRecordsList(domain, Type.AAAA), enrichmentExecutor);
        var cnameFuture = CompletableFuture.supplyAsync(
                () -> lookupRecordsList(domain, Type.CNAME), enrichmentExecutor);
        var txtFuture = CompletableFuture.supplyAsync(
                () -> lookupTxtRecordsList(domain), enrichmentExecutor);

        CompletableFuture.allOf(mxFuture, aFuture, aaaaFuture, cnameFuture, txtFuture).join();

        List<String> mxRecords = mxFuture.join();
        List<String> aRecords = aFuture.join();
        List<String> aaaaRecords = aaaaFuture.join();
        List<String> cnameRecords = cnameFuture.join();
        List<String> txtRecords = txtFuture.join();

        boolean hasMx = !mxRecords.isEmpty();

        if (hasMx || !aRecords.isEmpty() || !aaaaRecords.isEmpty()
                || !cnameRecords.isEmpty() || !txtRecords.isEmpty()) {
            log.debug("DNS para {}: {} MX, {} A, {} AAAA, {} CNAME, {} TXT",
                    domain, mxRecords.size(), aRecords.size(), aaaaRecords.size(),
                    cnameRecords.size(), txtRecords.size());
        }

        DnsResult result = new DnsResult(hasMx, mxRecords, aRecords, aaaaRecords, cnameRecords, txtRecords);
        dnsCache.put(cacheKey, result);
        return result;
    }

    /**
     * Executa uma consulta DNS genérica e retorna lista de resultados.
     */
    private List<String> lookupRecordsList(String domain, int type) {
        List<String> results = new ArrayList<>();
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
        return results;
    }

    /**
     * Executa consulta TXT com tratamento especial para extrair strings
     * de objetos {@link TXTRecord}, retornando lista.
     */
    private List<String> lookupTxtRecordsList(String domain) {
        List<String> results = new ArrayList<>();
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
        return results;
    }
}
