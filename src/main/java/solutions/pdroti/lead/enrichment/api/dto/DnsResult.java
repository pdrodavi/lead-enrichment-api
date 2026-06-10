package solutions.pdroti.lead.enrichment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * DTO que encapsula o resultado completo de uma consulta DNS.
 * <p>
 * Contém os registros MX, A (IPv4), AAAA (IPv6), CNAME e TXT.
 * O campo {@link #hasMx()} é um atalho booleano para verificar
 * se o domínio pode receber e-mails.
 *
 * @param hasMx       Se o domínio possui registro MX válido
 * @param mxRecords   Registros MX (servidores de e-mail)
 * @param aRecords    Registros A (IPv4)
 * @param aaaaRecords Registros AAAA (IPv6)
 * @param cnameRecords Registros CNAME (alias)
 * @param txtRecords  Registros TXT (SPF, DKIM, DMARC)
 */
@Schema(description = "Resultado completo da consulta DNS")
public record DnsResult(
    @Schema(description = "Se o domínio possui registro MX válido")
    boolean hasMx,
    @Schema(description = "Registros MX (servidores de email)")
    List<String> mxRecords,
    @Schema(description = "Registros A (IPv4)")
    List<String> aRecords,
    @Schema(description = "Registros AAAA (IPv6)")
    List<String> aaaaRecords,
    @Schema(description = "Registros CNAME (alias)")
    List<String> cnameRecords,
    @Schema(description = "Registros TXT (texto, incluindo SPF/DKIM)")
    List<String> txtRecords
) {
    /** Retorna um DnsResult vazio (sem registros). */
    public static DnsResult empty() {
        return new DnsResult(false, List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
