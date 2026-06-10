package solutions.pdroti.lead.enrichment.api.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import solutions.pdroti.lead.enrichment.api.model.Lead;
import solutions.pdroti.lead.enrichment.api.util.EmailUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static solutions.pdroti.lead.enrichment.api.dto.SerpSearchResult.empty;

@Schema(description = "Resposta com dados enriquecidos do lead (email mascarado por LGPD)")
public record LeadResponse(
        @Schema(description = "ID único do lead", example = "1")
        Long id,

        @Schema(description = "Email mascarado (LGPD)", example = "con***@exemplo.com")
        String emailMasked,

        @Schema(description = "Nome da pessoa", example = "João Silva")
        String name,

        @Schema(description = "Domínio validado", example = "exemplo.com")
        String domain,

        @Schema(description = "Se o domínio possui registro MX")
        boolean mxStatus,

        // === DNS — registros completos ===

        @Schema(description = "Registros MX (servidores de e-mail)")
        List<String> dnsMxRecords,

        @Schema(description = "Registros A (IPv4)")
        List<String> dnsARecords,

        @Schema(description = "Registros AAAA (IPv6)")
        List<String> dnsAaaaRecords,

        @Schema(description = "Registros CNAME (alias)")
        List<String> dnsCnameRecords,

        @Schema(description = "Registros TXT (SPF, DKIM, DMARC)")
        List<String> dnsTxtRecords,

        @Schema(description = "Status do processamento", example = "ENRICHED")
        String status,

        @Schema(description = "Tecnologias detectadas no domínio")
        List<String> technologies,

        @Schema(description = "Links de redes sociais encontrados")
        List<String> socialLinks,

        @Schema(description = "Resumo dos dados scrapy dos perfis de redes sociais")
        List<String> socialProfileSummaries,

        @Schema(description = "E-mails expostos encontrados")
        List<String> exposedEmails,

        @Schema(description = "Menções ao nome da pessoa encontradas na página")
        List<String> nameMentions,

        @Schema(description = "URLs onde o nome da pessoa foi encontrado (extraídas das menções)")
        List<String> nameMentionUrls,

        @Schema(description = "Total de achados (emails + menções)")
        int dorkFindings,

        // === Dados OpenSERP (resultado bruto da busca) ===

        @Schema(description = "Resultado estruturado da busca no OpenSERP")
        SerpSearchResult serperRawData,

        @Schema(description = "Links para documentos encontrados (PDF, DOC, XLS, PPT, etc.)",
                example = "[\"https://example.com/curriculo.pdf\"]")
        List<String> foundDocuments,

        // === Todos os links descobertos (não só sociais) ===

        @Schema(description = "Todos os URLs descobertos durante o enriquecimento (inclui sociais e não-sociais)",
                example = "[\"https://example.com\", \"https://github.com/pdroti\"]")
        List<String> discoveredUrls,

        // === Dados RDAP (registro de domínio) ===

        @Schema(description = "Dados RDAP do domínio")
        RdapData rdap
) {

    private static final Pattern URL_IN_MENTION = Pattern.compile("https?://[^\\s,;)]+");
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /** Cria resposta a partir do lead salvo. */
    public static LeadResponse fromEntity(Lead lead) {
        List<String> mentions = lead.getNameMentions();
        return new LeadResponse(
                lead.getId(),
                maskEmail(lead.getEmail()),
                lead.getName(),
                lead.getDomain(),
                lead.getMxStatus(),
                lead.getDnsMxRecords(),
                lead.getDnsARecords(),
                lead.getDnsAaaaRecords(),
                lead.getDnsCnameRecords(),
                lead.getDnsTxtRecords(),
                lead.getStatus(),
                lead.getTechnologies(),
                lead.getSocialLinks(),
                lead.getSocialProfileSummaries(),
                lead.getExposedEmails(),
                mentions,
                extractUrlsFromMentions(mentions),
                lead.getDorkFindings(),
                buildSerperResult(lead),
                lead.getFoundDocuments(),
                lead.getDiscoveredUrls(),
                buildRdap(lead)
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
    private static SerpSearchResult buildSerperResult(Lead lead) {
        if (lead.getSerperRawData() == null) return null;
        try {
            return JSON_MAPPER.readValue(lead.getSerperRawData(), SerpSearchResult.class);
        } catch (Exception e) {
            // Se falhar ao deserializar, retorna vazio (dados legacy ou corrompidos)
            return empty(null);
        }
    }

    /** Constrói RdapData a partir dos campos RDAP do lead. */
    private static RdapData buildRdap(Lead lead) {
        if (lead.getRdapRawData() == null) return null;
        Object parsedJson;
        try {
            parsedJson = JSON_MAPPER.readTree(lead.getRdapRawData());
        } catch (Exception e) {
            parsedJson = lead.getRdapRawData();
        }
        return new RdapData(
                parsedJson,
                lead.getRdapRegistrar(),
                lead.getRdapRegistrantName(),
                lead.getRdapRegistrantEmail(),
                lead.getRdapRegistrationDate() != null ? lead.getRdapRegistrationDate().toString() : null,
                lead.getRdapExpirationDate() != null ? lead.getRdapExpirationDate().toString() : null,
                lead.getRdapNameservers(),
                lead.getRdapStatus(),
                lead.getRdapTaxpayerId(),
                lead.getRdapSource()
        );
    }

    private static String maskEmail(String email) {
        return EmailUtils.mask(email);
    }
}
