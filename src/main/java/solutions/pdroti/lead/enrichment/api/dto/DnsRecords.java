package solutions.pdroti.lead.enrichment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Sub-record que agrupa todos os dados de DNS de um lead.
 */
@Schema(description = "Registros DNS do domínio")
public record DnsRecords(

        @Schema(description = "Se o domínio possui registro MX")
        boolean mxStatus,

        @Schema(description = "Registros MX (servidores de e-mail)")
        List<String> mxRecords,

        @Schema(description = "Registros A (IPv4)")
        List<String> aRecords,

        @Schema(description = "Registros AAAA (IPv6)")
        List<String> aaaaRecords,

        @Schema(description = "Registros CNAME (alias)")
        List<String> cnameRecords,

        @Schema(description = "Registros TXT (SPF, DKIM, DMARC)")
        List<String> txtRecords
) {

    /** Retorna um DnsRecords vazio (sem dados). */
    public static DnsRecords empty() {
        return new DnsRecords(false, List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
