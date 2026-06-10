package solutions.pdroti.lead.enrichment.api.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solutions.pdroti.lead.enrichment.api.dto.RdapData;
import solutions.pdroti.lead.enrichment.api.model.Lead;
import solutions.pdroti.lead.enrichment.api.repository.LeadRepository;
import solutions.pdroti.lead.enrichment.api.util.EmailUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leadRepository;
    private final DnsValidationService dnsValidationService;
    private final TechScraperService techScraperService;
    private final SocialDiscoveryService socialDiscoveryService;
    private final RdapService rdapService;
    private final OpenSerpSearch openSerpSearch;
    private static final int DATA_RETENTION_DAYS = 365;
    private static final String DEFAULT_STATUS = "ACTIVE";
    private static final String DELETED_STATUS = "DELETED";

    /** Regex para extração de e-mails de textos (snippets, títulos). */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    /** Número máximo de resultados retornados pelo OpenSERP. */
    private static final int OPENSERP_MAX_RESULTS = 30;

    /**
     * Enriquece um lead com dados públicos.
     * <p>
     * Fluxo:
     * <ol>
     *   <li>Extrai o domínio do e-mail se não foi informado</li>
     *   <li>Busca lead existente pelo hash do e-mail (identificador único)</li>
     *   <li>Sempre executa OpenSERP + condicionalmente enriquecimento de domínio</li>
     * </ol>
     *
     * @param email  e-mail do lead (obrigatório — identificador único)
     * @param domain domínio para enriquecimento (opcional, extraído do e-mail se ausente)
     * @param name   nome da pessoa (obrigatório)
     * @return lead persistido com dados enriquecidos
     */
    @Transactional
    public Lead enrich(String email, String domain, String name) {
        log.info("Enriquecendo lead: nome={} email={} domain={}", name, maskEmail(email), domain);

        if (domain == null) {
            domain = extractDomainFromEmail(email);
            log.info("Domínio extraído do e-mail: {}", domain);
        }

        Lead existing = leadRepository.findByEmailHash(EmailUtils.hash(email)).orElse(null);
        if (existing != null) {
            log.info("Lead já existe, reenriquecendo: ID={}", existing.getId());
        }

        return performFullEnrichment(existing, email, domain, name);
    }

    /**
     * Enriquece um lead a partir de uma entidade já existente.
     *
     * @param lead entidade com email e name preenchidos
     * @return lead persistido com dados enriquecidos
     */
    @Transactional
    public Lead enrichLead(Lead lead) {
        String domain = extractDomainFromEmail(lead.getEmail());
        return enrich(lead.getEmail(), domain, lead.getName());
    }

    /**
     * Retorna todos os leads com status ACTIVE (exclui soft-deleted).
     *
     * @return lista de leads ativos
     */
    public List<Lead> listAll() {
        return leadRepository.findByStatus(DEFAULT_STATUS);
    }

    /**
     * Retorna todos os leads ativos que pertencem ao domínio informado.
     *
     * @param domain domínio para filtrar (ex: "exemplo.com")
     * @return lista de leads do domínio, ou lista vazia se domain for inválido
     */
    public List<Lead> findByDomain(String domain) {
        if (!hasText(domain)) return List.of();
        return leadRepository.findByDomainAndStatus(domain, DEFAULT_STATUS);
    }

    /**
     * Busca um lead pelo ID (formato string, convertido internamente para Long).
     * Retorna apenas se o lead não estiver soft-deleted (status != DELETED).
     *
     * @param id identificador do lead em formato string
     * @return Optional com o lead encontrado, ou vazio se não existir ou estiver deletado
     */
    public Optional<Lead> findById(String id) {
        return parseNumericId(id)
                .flatMap(leadRepository::findById)
                .filter(lead -> !DELETED_STATUS.equals(lead.getStatus()));
    }

    /**
     * Atualiza os dados de um lead existente e reenriquece.
     * <p>
     * Se o domínio não for informado, tenta extrair do e-mail.
     * Se ainda assim não houver domínio, busca por nome no OpenSERP.
     *
     * @param id     ID do lead a ser atualizado
     * @param email  novo e-mail (pode ser null)
     * @param domain novo domínio (pode ser null)
     * @param name   novo nome
     * @return lead atualizado e reenriquecido
     * @throws IllegalArgumentException se o ID não existir
     */
    @Transactional
    public Lead update(String id, String email, String domain, String name) {
        Lead lead = findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lead não encontrado: " + id));

        lead.setName(name);
        lead.setEmail(email);
        lead.setDomain(domain != null ? domain : extractDomainFromEmail(email));

        return performFullEnrichment(lead, email, lead.getDomain(), name);
    }

    /**
     * Soft delete: marca o lead como DELETED (LGPD — direito ao esquecimento).
     * O registro permanece no banco, mas é ocultado das consultas padrão.
     *
     * @param id ID do lead a ser marcado como deletado
     * @return true se o lead foi encontrado e deletado, false caso contrário
     */
    @Transactional
    public boolean softDelete(String id) {
        return parseNumericId(id)
                .flatMap(leadRepository::findById)
                .map(this::performSoftDelete)
                .orElse(false);
    }

    /**
     * Hard delete: remove fisicamente o registro do banco de dados.
     * Use com cautela — prefira softDelete para conformidade LGPD.
     *
     * @param id ID do lead a ser removido permanentemente
     * @return true se o lead foi removido, false caso contrário
     */
    @Transactional
    public boolean hardDelete(String id) {
        return parseNumericId(id)
                .filter(leadRepository::existsById)
                .map(this::performHardDelete)
                .orElse(false);
    }

    // ==================== Métodos Privados ====================

    /**
     * Executa o pipeline completo de enrichment e persiste o resultado.
     * <p>
     * Fluxo unificado — TODAS as fontes são consultadas:
     * <ul>
     *   <li><b>OpenSERP (sempre):</b> busca o nome no Google, extrai links,
     *       redes sociais, e-mails expostos e menções ao nome</li>
     *   <li><b>Domínio (se disponível):</b> DNS completo, RDAP, scraping de
     *       tecnologias, descoberta de redes sociais e verificação de nome</li>
     * </ul>
     * Os resultados de ambas as fontes são mesclados.
     *
     * @param existing lead existente (null se for novo)
     * @param email    e-mail do lead
     * @param domain   domínio para enriquecimento (pode ser null)
     * @param name     nome da pessoa
     * @return lead persistido com dados enriquecidos
     */
    private Lead performFullEnrichment(Lead existing, String email, String domain, String name) {
        Lead lead = existing != null ? existing : createNewLead(email, domain, name);
        lead.setEmail(email);
        lead.setDomain(domain);
        lead.setName(name);
        lead.setStatus(DEFAULT_STATUS);
        if (existing == null) {
            lead.setCreatedAt(LocalDateTime.now());
        }

        String logId = maskEmail(email);
        if (logId == null) logId = name;

        resetEnrichmentData(lead);

        // 1. OpenSERP — SEMPRE executado (busca pelo nome no Google)
        enrichWithOpenSerp(lead, name);

        // 2. Domínio — executado apenas se disponível
        if (hasText(domain)) {
            enrichWithDomain(lead, domain, name);
        }

        Lead savedLead = leadRepository.save(lead);
        log.info("Lead enriquecido: {}", logId);
        return savedLead;
    }

    /**
     * Reseta todos os campos de enriquecimento para valores padrão.
     * Tanto o OpenSERP quanto o fluxo de domínio repopulam seus
     * respectivos campos após o reset.
     */
    private void resetEnrichmentData(Lead lead) {
        lead.setMxStatus(false);
        lead.setDnsMxRecords(null);
        lead.setDnsARecords(null);
        lead.setDnsAaaaRecords(null);
        lead.setDnsCnameRecords(null);
        lead.setDnsTxtRecords(null);
        lead.setTechnologies(null);
        lead.setSocialLinks(null);
        lead.setSocialProfileSummaries(null);
        lead.setExposedEmails(null);
        lead.setNameMentions(null);
        lead.setDorkFindings(0);
        lead.setSerperRawData(null);
        lead.setRdapRawData(null);
        lead.setRdapRegistrar(null);
        lead.setRdapRegistrantName(null);
        lead.setRdapRegistrantEmail(null);
        lead.setRdapRegistrationDate(null);
        lead.setRdapExpirationDate(null);
        lead.setRdapNameservers(null);
        lead.setRdapStatus(null);
        lead.setRdapTaxpayerId(null);
        lead.setRdapSource(null);
    }

    /**
     * Enriquece o lead utilizando o domínio: consultas DNS, RDAP, scraping,
     * redes sociais e verificação de nome na página.
     */
    private void enrichWithDomain(Lead lead, String domain, String name) {
        // 1. Consulta DNS completa (MX, A, AAAA, CNAME, TXT)
        executeSafely(() -> dnsValidationService.lookupDomain(domain),
                result -> {
                    if (result != null) {
                        lead.setMxStatus(result.hasMx());
                        lead.setDnsMxRecords(toMutable(result.mxRecords()));
                        lead.setDnsARecords(toMutable(result.aRecords()));
                        lead.setDnsAaaaRecords(toMutable(result.aaaaRecords()));
                        lead.setDnsCnameRecords(toMutable(result.cnameRecords()));
                        lead.setDnsTxtRecords(toMutable(result.txtRecords()));
                    }
                }, name);

        // 2. Consulta RDAP (dados de registro do domínio)
        enrichWithRdap(lead, domain);

        // 3. Scraping de tecnologias
        lead.setTechnologies(scrapeSafely(() -> techScraperService.scrapeTechnologies(domain)));

        // 4. Descoberta de redes sociais — mescla com links vindos do OpenSERP
        List<String> domainSocialLinks = scrapeSafely(
                () -> socialDiscoveryService.discoverSocialLinks(domain));
        Set<String> mergedSocialLinks = new LinkedHashSet<>(lead.getSocialLinks() != null
                ? lead.getSocialLinks() : List.of());
        mergedSocialLinks.addAll(domainSocialLinks);
        lead.setSocialLinks(new ArrayList<>(mergedSocialLinks));

        // 5. Scraping de perfis sociais (apenas se encontrou links no domínio)
        if (!domainSocialLinks.isEmpty()) {
            List<String> profiles = socialDiscoveryService.scrapeSocialProfiles(domainSocialLinks)
                    .stream().map(p -> p.toSummary()).toList();
            // Mescla com summaries vindos do OpenSERP
            List<String> existingSummaries = lead.getSocialProfileSummaries() != null
                    ? lead.getSocialProfileSummaries() : List.of();
            Set<String> mergedSummaries = new LinkedHashSet<>(existingSummaries);
            mergedSummaries.addAll(profiles);
            lead.setSocialProfileSummaries(new ArrayList<>(mergedSummaries));
        }

        // 6. Verifica se o nome da pessoa aparece no HTML do domínio — mescla com menções do OpenSERP
        List<String> domainNameMentions = scrapeSafely(
                () -> techScraperService.findNameInPage(domain, name));
        List<String> existingMentions = lead.getNameMentions() != null
                ? lead.getNameMentions() : new ArrayList<>();
        Set<String> mergedMentions = new LinkedHashSet<>(existingMentions);
        mergedMentions.addAll(domainNameMentions);
        lead.setNameMentions(new ArrayList<>(mergedMentions));

        boolean nameFound = domainNameMentions.stream()
                .anyMatch(m -> m.startsWith("Nome completo encontrado"));
        if (!nameFound) {
            log.warn("Nome '{}' não encontrado no HTML do domínio {}", name, domain);
        }
    }

    /**
     * Enriquece o lead com dados RDAP do domínio (registrar, titular, datas, nameservers).
     */
    private void enrichWithRdap(Lead lead, String domain) {
        RdapData rdap = rdapService.lookup(domain);
        if (rdap.rawJson() == null) return;

        lead.setRdapRawData(rdap.rawJson().toString());
        lead.setRdapRegistrar(rdap.registrar());
        lead.setRdapRegistrantName(rdap.registrantName());
        lead.setRdapRegistrantEmail(rdap.registrantEmail());
        lead.setRdapRegistrationDate(parseIsoDate(rdap.registrationDate()));
        lead.setRdapExpirationDate(parseIsoDate(rdap.expirationDate()));
        lead.setRdapNameservers(toMutable(rdap.nameservers()));
        lead.setRdapStatus(toMutable(rdap.status()));
        lead.setRdapTaxpayerId(rdap.taxpayerId());
        lead.setRdapSource(rdap.source());

        log.info("RDAP para {}: registrar={}, registrant={}",
                domain, rdap.registrar(), rdap.registrantName());
    }

    /**
     * Enriquece o lead sem domínio conhecido, buscando o nome da pessoa
     * no OpenSERP (Google Search) e extraindo links, redes sociais,
     * e-mails expostos e menções ao nome.
     */
    private void enrichWithOpenSerp(Lead lead, String name) {
        log.info("Buscando '{}' no OpenSERP", name);

        // Reseta dados específicos do OpenSERP antes de repopular
        lead.setExposedEmails(null);
        lead.setDorkFindings(0);
        lead.setSerperRawData(null);

        JsonArray results = fetchOpenSerpResults(lead, name);
        if (results == null || results.isEmpty()) {
            log.warn("OpenSERP não retornou resultados para '{}'", name);
            // NOTA: new ArrayList<>() obrigatório — Hibernate precisa de listas mutáveis
            lead.setSocialLinks(new ArrayList<>());
            lead.setExposedEmails(new ArrayList<>());
            lead.setNameMentions(new ArrayList<>());
            return;
        }

        // Processa cada resultado do OpenSERP
        Set<String> allLinks = new LinkedHashSet<>();
        Set<String> socialLinksFound = new LinkedHashSet<>();
        List<String> emails = new ArrayList<>();
        List<String> nameMentions = new ArrayList<>();

        // Reuso do pattern de domínios sociais do SocialDiscoveryService
        var socialDomains = SocialDiscoveryService.getSocialDomains();

        for (int i = 0; i < results.size(); i++) {
            JsonObject r = results.get(i).getAsJsonObject();
            String link = r.has("url") ? r.get("url").getAsString() : null;
            String snippet = r.has("snippet") ? r.get("snippet").getAsString() : "";
            String title = r.has("title") ? r.get("title").getAsString() : "";

            if (link == null) continue;

            // Só processa resultados que contêm o nome completo (100% match)
            // Evita trazer dados de outra pessoa com nome parcial igual
            if (!nameMatchesExactly(snippet, name) && !nameMatchesExactly(title, name)) {
                continue;
            }

            allLinks.add(link);
            String lowerLink = link.toLowerCase();

            // Classifica link como social se pertencer a domínio conhecido
            if (socialDomains.stream().anyMatch(lowerLink::contains)) {
                socialLinksFound.add(link);
            }

            // Menção ao nome completo encontrado
            nameMentions.add("Nome completo encontrado em: " + link);

            // Extração de e-mails do snippet/título
            extractEmails(emails, snippet, title);
        }

        // Montagem dos resultados no lead
        lead.setSocialLinks(new ArrayList<>(socialLinksFound));
        lead.getSocialLinks().addAll(allLinks);
        lead.setExposedEmails(emails);
        lead.setDorkFindings(emails.size());
        lead.setNameMentions(nameMentions);

        log.info("OpenSERP: {} links totais, {} sociais, {} e-mails, {} menções",
                allLinks.size(), socialLinksFound.size(), emails.size(), nameMentions.size());
    }



    /**
     * Faz a chamada ao OpenSERP e retorna os resultados como JsonArray.
     * Em caso de falha, retorna um array vazio e registra o erro.
     */
    private JsonArray fetchOpenSerpResults(Lead lead, String name) {
        try {
            JsonArray results = openSerpSearch.searchPerson(name, OPENSERP_MAX_RESULTS);
            lead.setSerperRawData(results.toString());
            log.info("OpenSERP: {} resultados para '{}'", results.size(), name);
            return results;
        } catch (Exception e) {
            log.warn("OpenSERP falhou para '{}': {}", name, e.getMessage());
            lead.setSerperRawData(null);
            return new JsonArray();
        }
    }

    /**
     * Verifica se o nome completo aparece no texto como uma palavra/frase
     * distinta, evitando matches parciais dentro de outras palavras.
     * <p>
     * Exemplos:
     * <ul>
     *   <li>"João Silva" → match em "sobre João Silva" ✓</li>
     *   <li>"João Silva" → NÃO match em "João Silveira" ✗</li>
     *   <li>"João Silva" → NÃO match em "João Silvares" ✗</li>
     * </ul>
     *
     * @param text texto onde buscar (snippet, título, HTML)
     * @param name nome completo a ser encontrado
     * @return true se o nome completo foi encontrado como termo distinto
     */
    private boolean nameMatchesExactly(String text, String name) {
        if (text == null || name == null) return false;
        String lowerText = text.toLowerCase();
        String lowerName = name.toLowerCase();

        int idx = lowerText.indexOf(lowerName);
        if (idx < 0) return false;

        // Verifica se não há caractere alfanumérico antes do nome
        if (idx > 0) {
            char before = lowerText.charAt(idx - 1);
            if (Character.isLetterOrDigit(before)) return false;
        }

        // Verifica se não há caractere alfanumérico depois do nome
        int endIdx = idx + lowerName.length();
        if (endIdx < lowerText.length()) {
            char after = lowerText.charAt(endIdx);
            if (Character.isLetterOrDigit(after)) return false;
        }

        return true;
    }

    /**
     * Extrai endereços de e-mail de um texto (snippet + título) usando regex.
     * Filtra e-mails falsos como "example.com".
     */
    private void extractEmails(List<String> emails, String snippet, String title) {
        var matcher = EMAIL_PATTERN.matcher(snippet + " " + title);
        while (matcher.find()) {
            String foundEmail = matcher.group().toLowerCase();
            if (!foundEmail.contains("example.com")) {
                emails.add(foundEmail);
            }
        }
    }

    /**
     * Cria um novo lead com valores padrão:
     * <ul>
     *   <li>Consentimento LGPD marcado como true com data atual</li>
     *   <li>Retenção de dados configurada para {@link #DATA_RETENTION_DAYS} dias</li>
     *   <li>Status inicial como ACTIVE</li>
     * </ul>
     *
     * @param email  e-mail do lead (pode ser null)
     * @param domain domínio do lead (pode ser null)
     * @param name   nome do lead
     * @return nova entidade Lead pronta para persistência
     */
    private Lead createNewLead(String email, String domain, String name) {
        LocalDateTime now = LocalDateTime.now();
        return Lead.builder()
                .email(email).domain(domain).name(name)
                .consentGiven(true).consentDate(now)
                .dataRetentionUntil(now.plusDays(DATA_RETENTION_DAYS))
                .createdAt(now).status(DEFAULT_STATUS)
                .build();
    }

    /**
     * Executa o soft delete: marca data/hora de exclusão e altera status para DELETED.
     * O registro permanece no banco para fins de auditoria e recuperação.
     *
     * @param lead entidade a ser marcada como deletada
     * @return sempre true
     */
    private boolean performSoftDelete(Lead lead) {
        lead.setDeletedAt(LocalDateTime.now());
        lead.setStatus(DELETED_STATUS);
        leadRepository.save(lead);
        log.info("Lead soft deleted: ID={}", lead.getId());
        return true;
    }

    /**
     * Remove fisicamente o lead do banco de dados.
     * Usado apenas para hard delete (não recomendado para conformidade LGPD).
     *
     * @param id ID do lead a ser removido
     * @return true se foi removido, false se não encontrado
     */
    private boolean performHardDelete(Long id) {
        return leadRepository.findById(id)
                .map(lead -> {
                    leadRepository.delete(lead);
                    log.info("Lead hard deleted: ID={}", id);
                    return true;
                })
                .orElseGet(() -> {
                    log.warn("Lead não encontrado para hard delete: ID={}", id);
                    return false;
                });
    }

    /**
     * {@inheritDoc}
     * @see #executeSafely(Supplier, Consumer, String, Object)
     */
    private <T> void executeSafely(Supplier<T> supplier, Consumer<T> setter, String logId) {
        executeSafely(supplier, setter, logId, null);
    }

    /**
     * Executa um Supplier com try-catch e aplica o resultado via Consumer,
     * utilizando um valor fallback em caso de erro ou resultado nulo.
     * <p>
     * Útil para operações externas (DNS, scraping) onde falhas parciais
     * não devem interromper todo o fluxo de enrichment.
     *
     * @param <T>      tipo do resultado
     * @param supplier operação que pode lançar exceção
     * @param setter   consumer para aplicar o resultado no lead
     * @param logId    identificador para logging em caso de erro
     * @param fallback valor padrão usado se supplier falhar ou retornar null
     */
    private <T> void executeSafely(Supplier<T> supplier, Consumer<T> setter,
                                    String logId, T fallback) {
        try {
            T result = supplier.get();
            setter.accept(result != null ? result : fallback);
        } catch (Exception e) {
            log.warn("Erro para {}: {}", logId, e.getMessage());
            if (fallback != null) {
                setter.accept(fallback);
            }
        }
    }

    /**
     * Extrai o domínio de um e-mail (tudo após o caractere @).
     *
     * @param email endereço de e-mail completo
     * @return domínio extraído (ex: "exemplo.com")
     * @throws IllegalArgumentException se o e-mail for nulo ou não contiver @
     */
    private String extractDomainFromEmail(String email) {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Email inválido: " + email);
        }
        return email.substring(email.indexOf("@") + 1);
    }

    /**
     * Verifica se uma string contém texto visível (não nula e não blank).
     *
     * @param s string a ser verificada
     * @return true se a string não for nula nem composta apenas de espaços
     */
    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * Executa um Supplier de lista com try-catch e converte o resultado
     * em ArrayList mutável. Evita UnsupportedOperationException que o Hibernate
     * lança ao tentar persistir listas imutáveis (List.of(), List.copyOf()).
     *
     * @param supplier operação que retorna uma lista (pode lançar exceção)
     * @return ArrayList mutável com os resultados, ou lista vazia em caso de erro
     */
    private static List<String> scrapeSafely(Supplier<List<String>> supplier) {
        try {
            List<String> result = supplier.get();
            return result != null ? new ArrayList<>(result) : new ArrayList<>();
        } catch (Exception e) {
            log.warn("Erro ao buscar dados: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Converte uma lista imutável (ex: List.of()) em ArrayList mutável
     * para que o Hibernate consiga persistir corretamente.
     *
     * @param lista imutável ou null
     * @return ArrayList mutável (nunca null)
     */
    private static <T> List<T> toMutable(List<T> list) {
        return list != null ? new ArrayList<>(list) : new ArrayList<>();
    }

    /**
     * Converte um ID em formato string para Long.
     * Retorna Optional.empty() se o formato for inválido, com log de aviso.
     *
     * @param id identificador em formato string
     * @return Optional com o Long parseado, ou vazio se inválido
     */
    private Optional<Long> parseNumericId(String id) {
        try {
            return Optional.of(Long.parseLong(id));
        } catch (NumberFormatException e) {
            log.warn("ID inválido (deve ser numérico): {}", id);
            return Optional.empty();
        }
    }

    /**
     * Ofusca o e-mail para logging usando {@link EmailUtils#mask(String)}.
     * Segue a LGPD — nenhum e-mail completo deve aparecer em logs.
     *
     * @param email e-mail a ser mascarado
     * @return e-mail mascarado (ex: "ped***@pdroti.com") ou null
     */
    private String maskEmail(String email) {
        return email != null ? EmailUtils.mask(email) : null;
    }

    /**
     * Converte uma string de data ISO-8601 (ex: "2026-06-05T15:19:47.754Z")
     * em {@link LocalDateTime}.
     * <p>
     * Trata variações comuns:
     * <ul>
     *   <li>Suprime o sufixo "Z" (indicador de UTC)</li>
     *   <li>Trunca frações de segundos com mais de 9 dígitos</li>
     * </ul>
     *
     * @param dateStr string de data no formato ISO-8601
     * @return LocalDateTime convertido, ou null se a string for inválida
     */
    private static LocalDateTime parseIsoDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            String normalized = dateStr.strip();
            // Remove sufixo "Z" (indicador UTC)
            if (normalized.endsWith("Z")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            // Trunca fração de segundos com mais de 9 dígitos (nanossegundos)
            int dotIndex = normalized.indexOf('.');
            if (dotIndex > 0) {
                String fraction = normalized.substring(dotIndex + 1);
                if (fraction.length() > 9) {
                    normalized = normalized.substring(0, dotIndex + 10);
                }
            }
            return LocalDateTime.parse(normalized);
        } catch (Exception e) {
            log.debug("Não foi possível converter data: {}", dateStr);
            return null;
        }
    }
}