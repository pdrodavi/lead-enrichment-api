package solutions.pdroti.lead.enrichment.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * DTO com dados extraídos da consulta RDAP (Registro de Domínio).
 * <p>
 * O RDAP é o sucessor do WHOIS e fornece informações sobre
 * registro, titularidade e status de domínios.
 *
 * @param rawJson          JSON bruto retornado pela API RDAP
 * @param registrar        Nome do registrador (ex: HOSTINGER operations, UAB)
 * @param registrantName   Nome do titular do domínio
 * @param registrantEmail  E-mail do titular (quando disponível)
 * @param registrationDate Data de registro do domínio (ISO-8601)
 * @param expirationDate   Data de expiração do domínio (ISO-8601)
 * @param nameservers      Servidores DNS do domínio
 * @param status           Status do domínio (ex: client transfer prohibited)
 * @param taxpayerId       CPF/CNPJ do titular (apenas .com.br via Registro.br)
 * @param source           Fonte da consulta (identitydigital ou registrobr)
 */
@Schema(description = "Dados do registro RDAP do domínio")
public record RdapData(

        @Schema(description = "JSON bruto retornado pela API RDAP")
        JsonNode rawJson,

        @Schema(description = "Nome do registrador (ex: HOSTINGER operations, UAB)")
        String registrar,

        @Schema(description = "Nome do titular do domínio")
        String registrantName,

        @Schema(description = "E-mail do titular (quando disponível)")
        String registrantEmail,

        @Schema(description = "Data de registro do domínio")
        String registrationDate,

        @Schema(description = "Data de expiração do domínio")
        String expirationDate,

        @Schema(description = "Servidores DNS do domínio")
        List<String> nameservers,

        @Schema(description = "Status do domínio (ex: client transfer prohibited)")
        List<String> status,

        @Schema(description = "CPF/CNPJ do titular (apenas .com.br)")
        String taxpayerId,

        @Schema(description = "Fonte da consulta (identitydigital ou registrobr)")
        String source
) {

    /** Retorna um RdapData vazio (sem dados). */
    public static RdapData empty() {
        return new RdapData(null, null, null, null, null, null, List.of(), List.of(), null, null);
    }
}
