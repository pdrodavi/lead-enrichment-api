package solutions.pdroti.lead.enrichment.api.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.extern.slf4j.Slf4j;
import solutions.pdroti.lead.enrichment.api.model.Lead;
import solutions.pdroti.lead.enrichment.api.util.EmailUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static solutions.pdroti.lead.enrichment.api.dto.SerpSearchResult.empty;

/**
 * Resposta com dados enriquecidos do lead (email mascarado por LGPD).
 * <p>
 * Os campos são organizados em sub-records para melhor legibilidade:
 * <ul>
 *   <li>{@link DnsRecords} — registros DNS (MX, A, AAAA, CNAME, TXT)</li>
 *   <li>{@link DiscoveryData} — tecnologias, redes sociais, e-mails, menções, OpenSERP</li>
 *   <li>{@link RdapData} — dados de registro de domínio</li>
 * </ul>
 */
@Schema(description = "Resposta com dados enriquecidos do lead (email mascarado por LGPD)")
@Slf4j
public record LeadResponse(

        @Schema(description = "ID único do lead", example = "1")
        Long id,

        @Schema(description = "Email mascarado (LGPD)", example = "con***@exemplo.com")
        String emailMasked,

        @Schema(description = "Nome da pessoa", example = "João Silva")
        String name,

        @Schema(description = "Domínio validado", example = "exemplo.com")
        String domain,

        @Schema(description = "Status do processamento", example = "ENRICHED")
        String status,

        @Schema(description = "Registros DNS do domínio")
        DnsRecords dns,

        @Schema(description = "Dados de descoberta (tecnologias, redes sociais, menções)")
        DiscoveryData discovery,

        @Schema(description = "Dados RDAP do domínio")
        RdapData rdap
) {

    private static final Pattern URL_IN_MENTION = Pattern.compile("https?://[^\\s,;)]+");

    /**
     * Cria um LeadResponse a partir do lead salvo, usando o ObjectMapper
     * configurado pelo Spring para deserializar dados JSON.
     *
     * @param lead   entidade Lead persistida
     * @param mapper ObjectMapper gerenciado pelo Spring (respeita configurações do application.yml)
     * @return LeadResponse com dados mascarados e agrupados
     */
    public static LeadResponse fromEntity(Lead lead, ObjectMapper mapper) {
        List<String> mentions = lead.getNameMentions() != null ? lead.getNameMentions() : List.of();

        DnsRecords dns = buildDnsRecords(lead);
        DiscoveryData discovery = buildDiscoveryData(lead, mentions, mapper);
        RdapData rdap = buildRdap(lead, mapper);

        return new LeadResponse(
                lead.getId(),
                EmailUtils.mask(lead.getEmail()),
                lead.getName(),
                lead.getDomain(),
                lead.getStatus(),
                dns,
                discovery,
                rdap
        );
    }

    /**
     * Cria um LeadResponse a partir do lead salvo (usa ObjectMapper padrão).
     * Prefira {@link #fromEntity(Lead, ObjectMapper)} para respeitar as
     * configurações do Spring.
     *
     * @param lead entidade Lead persistida
     * @return LeadResponse com dados mascarados e agrupados
     */
    public static LeadResponse fromEntity(Lead lead) {
        return fromEntity(lead, new ObjectMapper());
    }

    /** Constrói o sub-record DnsRecords a partir dos campos do lead. */
    private static DnsRecords buildDnsRecords(Lead lead) {
        return new DnsRecords(
                lead.getMxStatus(),
                lead.getDnsMxRecords() != null ? lead.getDnsMxRecords() : List.of(),
                lead.getDnsARecords() != null ? lead.getDnsARecords() : List.of(),
                lead.getDnsAaaaRecords() != null ? lead.getDnsAaaaRecords() : List.of(),
                lead.getDnsCnameRecords() != null ? lead.getDnsCnameRecords() : List.of(),
                lead.getDnsTxtRecords() != null ? lead.getDnsTxtRecords() : List.of()
        );
    }

    /** Constrói o sub-record DiscoveryData a partir dos campos do lead. */
    private static DiscoveryData buildDiscoveryData(Lead lead, List<String> mentions, ObjectMapper mapper) {
        return new DiscoveryData(
                lead.getTechnologies() != null ? lead.getTechnologies() : List.of(),
                lead.getSocialLinks() != null ? lead.getSocialLinks() : List.of(),
                lead.getSocialProfileSummaries() != null ? lead.getSocialProfileSummaries() : List.of(),
                lead.getExposedEmails() != null ? lead.getExposedEmails() : List.of(),
                mentions,
                extractUrlsFromMentions(mentions),
                lead.getDorkFindings(),
                lead.getFoundDocuments() != null ? lead.getFoundDocuments() : List.of(),
                lead.getDiscoveredUrls() != null ? lead.getDiscoveredUrls() : List.of(),
                buildSerperResult(lead, mapper)
        );
    }

    /** Extrai as URLs do campo nameMentions. */
    private static List<String> extractUrlsFromMentions(List<String> mentions) {
        if (mentions == null || mentions.isEmpty()) return List.of();
        List<String> urls = new ArrayList<>();
        for (String mention : mentions) {
            var matcher = URL_IN_MENTION.matcher(mention);
            while (matcher.find()) {
                urls.add(matcher.group());
            }
        }
        return urls;
    }

    /** Converte o JSON estruturado do OpenSERP em objeto tipado para o response. */
    private static SerpSearchResult buildSerperResult(Lead lead, ObjectMapper mapper) {
        if (lead.getSerperRawData() == null) return null;
        try {
            return mapper.readValue(lead.getSerperRawData(), SerpSearchResult.class);
        } catch (Exception e) {
            log.error("Erro ao deserializar serperRawData para lead ID {}: {}", lead.getId(), e.getMessage());
            return empty(null);
        }
    }

    /** Constrói RdapData a partir dos campos RDAP do lead. */
    private static RdapData buildRdap(Lead lead, ObjectMapper mapper) {
        if (lead.getRdapRawData() == null) return RdapData.empty();
        JsonNode parsedJson;
        try {
            parsedJson = mapper.readTree(lead.getRdapRawData());
        } catch (Exception e) {
            return RdapData.empty();
        }
        return new RdapData(
                parsedJson,
                lead.getRdapRegistrar(),
                lead.getRdapRegistrantName(),
                lead.getRdapRegistrantEmail(),
                lead.getRdapRegistrationDate() != null ? lead.getRdapRegistrationDate().toString() : null,
                lead.getRdapExpirationDate() != null ? lead.getRdapExpirationDate().toString() : null,
                lead.getRdapNameservers() != null ? lead.getRdapNameservers() : List.of(),
                lead.getRdapStatus() != null ? lead.getRdapStatus() : List.of(),
                lead.getRdapTaxpayerId(),
                lead.getRdapSource()
        );
    }
}
