package solutions.pdroti.lead.enrichment.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solutions.pdroti.lead.enrichment.api.dto.RdapData;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Serviço de consulta RDAP (Registration Data Access Protocol) para
 * obter dados de registro de domínio.
 * <p>
 * Utiliza duas fontes:
 * <ul>
 *   <li><b>Identity Digital</b> — para domínios genéricos (.com, .org, .net, etc.)</li>
 *   <li><b>Registro.br</b> — para domínios .com.br (dados mais completos, incluindo CPF/CNPJ)</li>
 * </ul>
 * <p>
 * O RDAP é o sucessor moderno do WHOIS, com respostas em JSON estruturado.
 *
 * @see <a href="https://www.icann.org/rdap">ICANN RDAP</a>
 * @see <a href="https://rdap.registro.br">Registro.br RDAP</a>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RdapService {

    /** URL base da API RDAP da Identity Digital. */
    private static final String IDENTITY_DIGITAL_URL = "https://rdap.identitydigital.services/rdap/domain/";

    /** URL base da API RDAP do Registro.br. */
    private static final String REGISTRO_BR_URL = "https://rdap.registro.br/domain/";

    /** Timeout para conexão e leitura. */
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    /** User-Agent para as requisições. */
    private static final String USER_AGENT = "LeadEnrichmentAPI/1.0";

    private final ObjectMapper objectMapper;

    /**
     * Consulta os dados de registro do domínio via RDAP.
     * <p>
     * Fluxo da consulta:
     * <ol>
     *   <li>Tenta Identity Digital (cobre a maioria dos TLDs genéricos)</li>
     *   <li>Se o domínio for {@code .com.br}, também consulta o Registro.br</li>
     *   <li>Para {@code .com.br}, mescla os resultados preferindo o Registro.br</li>
     * </ol>
     *
     * @param domain domínio a ser consultado (ex: \"exemplo.com\" ou \"exemplo.com.br\")
     * @return {@link RdapData} com dados do registro, ou vazio se não encontrado
     */
    public RdapData lookup(String domain) {
        if (domain == null || domain.isBlank()) {
            return RdapData.empty();
        }

        String lowerDomain = domain.toLowerCase().strip();

        // Identity Digital — funciona para a maioria dos TLDs genéricos
        RdapData identityResult = queryIdentityDigital(lowerDomain);
        RdapData result = identityResult.rawJson() != null ? identityResult : RdapData.empty();

        // Para .com.br, também consulta Registro.br (dados mais completos)
        if (lowerDomain.endsWith(".com.br")) {
            log.info("Domínio .com.br detectado — consultando Registro.br: {}", lowerDomain);
            RdapData registroBrResult = queryRegistroBr(lowerDomain);
            if (registroBrResult.rawJson() != null) {
                result = mergeResults(registroBrResult, identityResult);
            }
        }

        return result;
    }

    /** Consulta a API Identity Digital. */
    private RdapData queryIdentityDigital(String domain) {
        try {
            String json = fetchJson(IDENTITY_DIGITAL_URL + domain);
            if (json == null) return RdapData.empty();
            return parseRdapResponse(json, "identitydigital");
        } catch (Exception e) {
            log.debug("Identity Digital RDAP falhou para {}: {}", domain, e.getMessage());
            return RdapData.empty();
        }
    }

    /** Consulta a API Registro.br. */
    private RdapData queryRegistroBr(String domain) {
        try {
            String json = fetchJson(REGISTRO_BR_URL + domain);
            if (json == null) return RdapData.empty();
            return parseRdapResponse(json, "registrobr");
        } catch (Exception e) {
            log.debug("Registro.br RDAP falhou para {}: {}", domain, e.getMessage());
            return RdapData.empty();
        }
    }

    /** Faz a requisição HTTP GET e retorna o body como string. */
    private String fetchJson(String url) {
        try {
            var client = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .build();
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/rdap+json")
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return response.body();
            }
            log.debug("RDAP {} retornou status {}", url, response.statusCode());
            return null;
        } catch (Exception e) {
            log.debug("Falha ao conectar em {}: {}", url, e.getMessage());
            return null;
        }
    }

    /** Parseia o JSON RDAP e extrai campos relevantes. */
    private RdapData parseRdapResponse(String json, String source) {
        try {
            JsonNode root = objectMapper.readTree(json);

            // Nomes de servidores
            List<String> nameservers = new ArrayList<>();
            JsonNode nsNode = root.get("nameservers");
            if (nsNode != null && nsNode.isArray()) {
                for (JsonNode ns : nsNode) {
                    JsonNode name = ns.get("ldhName");
                    if (name != null) nameservers.add(name.asText());
                }
            }

            // Status
            List<String> status = new ArrayList<>();
            JsonNode stNode = root.get("status");
            if (stNode != null && stNode.isArray()) {
                stNode.forEach(s -> status.add(s.asText()));
            }

            // Datas
            String registrationDate = findEventDate(root, "registration");
            String expirationDate = findEventDate(root, "expiration");

            // Registrar e registrant
            String registrar = null;
            String registrantName = null;
            String registrantEmail = null;
            String taxpayerId = null;

            JsonNode entities = root.get("entities");
            if (entities != null && entities.isArray()) {
                for (JsonNode entity : entities) {
                    List<String> roles = new ArrayList<>();
                    JsonNode rolesNode = entity.get("roles");
                    if (rolesNode != null && rolesNode.isArray()) {
                        rolesNode.forEach(r -> roles.add(r.asText()));
                    }

                    String fn = extractFnFromVcard(entity);

                    if (roles.contains("registrar")) {
                        registrar = fn;
                        // Busca abuse contact dentro das sub-entidades do registrar
                        JsonNode subEntities = entity.get("entities");
                        if (subEntities != null && subEntities.isArray() && registrantEmail == null) {
                            for (JsonNode sub : subEntities) {
                                // Verifica as roles da sub-entidade (não da entidade pai)
                                List<String> subRoles = new ArrayList<>();
                                JsonNode subRolesNode = sub.get("roles");
                                if (subRolesNode != null && subRolesNode.isArray()) {
                                    subRolesNode.forEach(r -> subRoles.add(r.asText()));
                                }
                                String email = extractEmailFromVcard(sub);
                                if (email != null && subRoles.contains("abuse")) {
                                    registrantEmail = email;
                                }
                            }
                        }
                    }

                    if (roles.contains("registrant") || roles.contains("administrative")) {
                        if (registrantName == null) registrantName = fn;
                        String email = extractEmailFromVcard(entity);
                        if (email != null && registrantEmail == null) {
                            registrantEmail = email;
                        }
                        // CPF/CNPJ do Registro.br
                        JsonNode publicIds = entity.get("publicIds");
                        if (publicIds != null && publicIds.isArray()) {
                            for (JsonNode pid : publicIds) {
                                JsonNode type = pid.get("type");
                                if (type != null && ("cpf".equals(type.asText()) || "cnpj".equals(type.asText()))) {
                                    taxpayerId = pid.get("identifier").asText();
                                }
                            }
                        }
                    }

                    // Fallback: se roles for technical/administrative, pega dados
                    if (roles.contains("technical")) {
                        if (registrantName == null) registrantName = fn;
                        String email = extractEmailFromVcard(entity);
                        if (email != null && registrantEmail == null) {
                            registrantEmail = email;
                        }
                    }
                }
            }

            return new RdapData(
                    root, registrar, registrantName, registrantEmail,
                    registrationDate, expirationDate,
                    nameservers, status, taxpayerId, source
            );

        } catch (Exception e) {
            log.debug("Falha ao parsear RDAP: {}", e.getMessage());
            return RdapData.empty();
        }
    }

    /**
     * Extrai um campo específico do vcardArray de uma entidade RDAP.
     * <p>
     * O RDAP usa o formato jCard (JSON vCard) para representar dados
     * de pessoas e organizações. Cada propriedade é um array onde:
     * <ul>
     *   <li>{@code [0]} = nome do parâmetro (ex: "fn", "email")</li>
     *   <li>{@code [3]} = valor do campo</li>
     * </ul>
     *
     * @param entity    entidade RDAP (pessoa/organização)
     * @param fieldName nome do campo a extrair ("fn" ou "email")
     * @return valor do campo, ou null se não encontrado
     */
    private String extractFromVcard(JsonNode entity, String fieldName) {
        try {
            JsonNode vcard = entity.get("vcardArray");
            if (vcard != null && vcard.isArray() && vcard.size() > 1) {
                JsonNode props = vcard.get(1);
                if (props != null && props.isArray()) {
                    for (JsonNode prop : props) {
                        if (prop.isArray() && prop.size() > 3
                                && fieldName.equals(prop.get(0).asText())) {
                            return prop.get(3).asText();
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Extrai o nome completo (fn) do vcardArray de uma entidade. */
    private String extractFnFromVcard(JsonNode entity) {
        return extractFromVcard(entity, "fn");
    }

    /** Extrai o e-mail do vcardArray de uma entidade. */
    private String extractEmailFromVcard(JsonNode entity) {
        return extractFromVcard(entity, "email");
    }

    /** Busca a data de um evento específico no JSON RDAP. */
    private String findEventDate(JsonNode root, String action) {
        try {
            JsonNode events = root.get("events");
            if (events != null && events.isArray()) {
                for (JsonNode event : events) {
                    JsonNode ea = event.get("eventAction");
                    if (ea != null && action.equals(ea.asText())) {
                        JsonNode date = event.get("eventDate");
                        if (date != null) return date.asText();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Mescla dois resultados RDAP, preferindo os dados do Registro.br para .com.br. */
    private RdapData mergeResults(RdapData preferred, RdapData fallback) {
        return new RdapData(
                preferred.rawJson() != null ? preferred.rawJson() : fallback.rawJson(),
                preferred.registrar() != null ? preferred.registrar() : fallback.registrar(),
                preferred.registrantName() != null ? preferred.registrantName() : fallback.registrantName(),
                preferred.registrantEmail() != null ? preferred.registrantEmail() : fallback.registrantEmail(),
                preferred.registrationDate() != null ? preferred.registrationDate() : fallback.registrationDate(),
                preferred.expirationDate() != null ? preferred.expirationDate() : fallback.expirationDate(),
                !preferred.nameservers().isEmpty() ? preferred.nameservers() : fallback.nameservers(),
                !preferred.status().isEmpty() ? preferred.status() : fallback.status(),
                preferred.taxpayerId() != null ? preferred.taxpayerId() : fallback.taxpayerId(),
                "registrobr"
        );
    }
}
