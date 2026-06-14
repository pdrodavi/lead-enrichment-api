package solutions.pdroti.lead.enrichment.api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import solutions.pdroti.lead.enrichment.api.model.Lead;
import solutions.pdroti.lead.enrichment.api.repository.LeadRepository;
import solutions.pdroti.lead.enrichment.api.util.DataParser;
import solutions.pdroti.lead.enrichment.api.util.EmailUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

/**
 * Orquestrador do pipeline de enriquecimento de leads.
 * <p>
 * Coordena as fontes de dados delegando para serviços especializados:
 * <ul>
 *   <li>{@link OpenSerpEnricherService} — busca no Google pelo nome da pessoa</li>
 *   <li>{@link DomainEnricherService} — DNS, RDAP, scraping, redes sociais</li>
 *   <li>{@link LeadDeletionService} — exclusão de registros</li>
 * </ul>
 * <p>
 * Otimização: OpenSERP e Domain enrichment executam em paralelo via
 * {@link CompletableFuture} com pool de Virtual Threads dedicado,
 * reduzindo o tempo total pela duração do mais lento.
 * <p>
 * <b>Merge seguro:</b> os campos compartilhados entre
 * {@link OpenSerpEnricherService} e {@link DomainEnricherService}
 * usam {@code LinkedHashSet} para evitar race conditions na escrita
 * paralela.
 * <p>
 * <b>Cache:</b>
 * <ul>
 *   <li>DNS, tecnologias, links sociais, RDAP, perfis sociais — Caffeine (1h)</li>
 *   <li>OpenSERP — Caffeine (30min) + Redis L2 (30min)</li>
 *   <li>Enrich endpoint — {@code @Cacheable} (24h)</li>
 * </ul>
 * <p>
 * <b>Domínios pessoais:</b> provedores como gmail.com, outlook.com
 * têm o enriquecimento de domínio pulado (apenas OpenSERP roda).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leadRepository;
    private final OpenSerpEnricherService openSerpEnricherService;
    private final DomainEnricherService domainEnricher;
    private final SocialDiscoveryService socialDiscoveryService;
    private final TransactionTemplate transactionTemplate;
    private final RestTemplate restTemplate;

    @Qualifier("enrichmentExecutor")
    private final Executor enrichmentExecutor;

    private static final int DATA_RETENTION_DAYS = 365;
    static final String DEFAULT_STATUS = "ACTIVE";

    /**
     * Resultado do enriquecimento com os leads do mesmo domínio,
     * evitando uma segunda consulta ao banco no controller.
     */
    public record EnrichResult(Lead enriched, List<Lead> domainLeads) {}

    /**
     * Enriquece um lead com dados públicos e retorna também os leads do mesmo domínio.
     *
     * @param email  e-mail do lead (obrigatório — identificador único)
     * @param domain domínio para enriquecimento (opcional, extraído do e-mail se ausente)
     * @param name   nome da pessoa (obrigatório)
     * @return {@link EnrichResult} com lead persistido + leads do mesmo domínio
     */
    public EnrichResult enrichWithDomainLeads(String email, String domain, String name) {
        Lead enriched = enrich(email, domain, name);
        String d = enriched.getDomain();
        List<Lead> domainLeads = (d != null && !d.isBlank())
                ? leadRepository.findByDomainAndStatus(d, DEFAULT_STATUS)
                : List.of();
        return new EnrichResult(enriched, domainLeads);
    }

    /**
     * Enriquece um lead com dados públicos.
     *
     * @param email  e-mail do lead (obrigatório — identificador único)
     * @param domain domínio para enriquecimento (opcional, extraído do e-mail se ausente)
     * @param name   nome da pessoa (obrigatório)
     * @return lead persistido com dados enriquecidos
     */
    public Lead enrich(String email, String domain, String name) {
        log.info("Enriquecendo lead: nome={} email={} domain={}", name, EmailUtils.mask(email), domain);

        if (domain == null) {
            log.info("Domínio não informado — buscará redes sociais, telefones e e-mails em sites .com/.com.br via OpenSERP");
        }

        Lead existing = leadRepository.findByEmailHash(EmailUtils.hash(email)).orElse(null);
        if (existing != null) {
            log.debug("Lead já existe, reenriquecendo: ID={}", existing.getId());
        }

        return performFullEnrichment(existing, email, domain, name);
    }

    /**
     * Retorna todos os leads com status ACTIVE (paginado).
     *
     * @param pageable parâmetros de paginação (page, size, sort)
     * @return página de leads ativos
     */
    @Transactional(readOnly = true)
    public Page<Lead> listAll(Pageable pageable) {
        return leadRepository.findByStatus(DEFAULT_STATUS, pageable);
    }

    /**
     * Retorna todos os leads ativos que pertencem ao domínio informado (paginado).
     *
     * @param domain   domínio para filtrar (ex: "exemplo.com")
     * @param pageable parâmetros de paginação (page, size, sort)
     * @return página de leads do domínio, ou página vazia se domain for inválido
     */
    @Transactional(readOnly = true)
    public Page<Lead> findByDomain(String domain, Pageable pageable) {
        if (!StringUtils.hasText(domain)) return Page.empty();
        return leadRepository.findByDomainAndStatus(domain, DEFAULT_STATUS, pageable);
    }

    /**
     * Busca um lead pelo ID.
     * Retorna apenas se o lead estiver ativo (status != DELETED).
     *
     * @param id identificador do lead em formato string
     * @return Optional com o lead encontrado, ou vazio se não existir ou estiver deletado
     */
    @Transactional(readOnly = true)
    public Optional<Lead> findById(String id) {
        return LeadDeletionService.parseNumericId(id)
                .flatMap(leadRepository::findById)
                .filter(lead -> !LeadDeletionService.DELETED_STATUS.equals(lead.getStatus()));
    }

    /**
     * Atualiza os dados de um lead existente e reenriquece.
     *
     * @param id     ID do lead a ser atualizado
     * @param email  novo e-mail (pode ser null)
     * @param domain novo domínio (pode ser null, extraído do e-mail se ausente)
     * @param name   novo nome
     * @return lead atualizado e reenriquecido
     * @throws IllegalArgumentException se o ID não existir
     */
    public Lead update(String id, String email, String domain, String name) {
        Lead lead = findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lead não encontrado: " + id));

        lead.setName(name);
        lead.setEmail(email);
        lead.setDomain(domain);
        lead.setUpdatedAt(LocalDateTime.now());

        return performFullEnrichment(lead, email, domain, name);
    }

    // ==================== Métodos Privados ====================

    /**
     * Executa o pipeline completo de enrichment delegando para
     * {@link OpenSerpEnricherService} e {@link DomainEnricherService}, e persiste o resultado.
     */
    private Lead performFullEnrichment(Lead existing, String email, String domain, String name) {
        Lead lead = existing != null ? existing : createNewLead(email, domain, name);
        lead.setEmail(email);
        lead.setDomain(domain);
        lead.setName(name);
        if (lead.getCreatedAt() == null) {
            lead.setCreatedAt(LocalDateTime.now());
        }

        String logId = EmailUtils.mask(email);
        if (logId == null) logId = name;

        // Preserva dados antigos antes de resetar — se o reenriquecimento
        // retornar vazio (ex: CAPTCHA), os dados anteriores são mantidos
        // (HashMap permite valores null, ao contrário de Map.of/Map.entry)
        var oldSnapshot = new java.util.HashMap<String, Object>();
        oldSnapshot.put("dnsMxRecords", lead.getDnsMxRecords());
        oldSnapshot.put("dnsARecords", lead.getDnsARecords());
        oldSnapshot.put("dnsAaaaRecords", lead.getDnsAaaaRecords());
        oldSnapshot.put("dnsCnameRecords", lead.getDnsCnameRecords());
        oldSnapshot.put("dnsTxtRecords", lead.getDnsTxtRecords());
        oldSnapshot.put("technologies", lead.getTechnologies());
        oldSnapshot.put("socialLinks", lead.getSocialLinks());
        oldSnapshot.put("socialProfileSummaries", lead.getSocialProfileSummaries());
        oldSnapshot.put("exposedEmails", lead.getExposedEmails());
        oldSnapshot.put("exposedPhones", lead.getExposedPhones());
        oldSnapshot.put("nameMentions", lead.getNameMentions());
        oldSnapshot.put("foundDocuments", lead.getFoundDocuments());
        oldSnapshot.put("discoveredUrls", lead.getDiscoveredUrls());
        oldSnapshot.put("openSerpRawData", lead.getOpenSerpRawData());
        oldSnapshot.put("rdapRawData", lead.getRdapRawData());
        oldSnapshot.put("rdapRegistrar", lead.getRdapRegistrar());
        oldSnapshot.put("rdapRegistrantName", lead.getRdapRegistrantName());
        oldSnapshot.put("rdapRegistrantEmail", lead.getRdapRegistrantEmail());
        oldSnapshot.put("rdapRegistrationDate", lead.getRdapRegistrationDate());
        oldSnapshot.put("rdapExpirationDate", lead.getRdapExpirationDate());
        oldSnapshot.put("rdapNameservers", lead.getRdapNameservers());
        oldSnapshot.put("rdapStatus", lead.getRdapStatus());
        oldSnapshot.put("rdapTaxpayerId", lead.getRdapTaxpayerId());
        oldSnapshot.put("rdapSource", lead.getRdapSource());

        domainEnricher.resetEnrichmentData(lead);

        // OpenSERP e domínio executam em PARALELO via CompletableFuture
        // com pool de threads dedicado (enrichmentExecutor)
        CompletableFuture<Void> openSerpFuture = CompletableFuture.runAsync(() ->
                openSerpEnricherService.enrich(lead, name), enrichmentExecutor);

        // Para domínios de provedores pessoais (gmail.com, outlook.com, etc.),
        // o enriquecimento de domínio é pulado — não faz sentido consultar
        // DNS/RDAP/TechScraper do provedor, pois os dados seriam dele, não do lead.
        boolean isPersonalDomain = DataParser.isPersonalEmailDomain(domain);
        CompletableFuture<Void> domainFuture = StringUtils.hasText(domain) && !isPersonalDomain
                ? CompletableFuture.runAsync(() -> domainEnricher.enrich(lead, domain, name), enrichmentExecutor)
                : CompletableFuture.completedFuture(null);
        if (isPersonalDomain) {
            log.info("Domínio pessoal '{}' — pulando DomainEnricherService", domain);
        }

        // Aguarda ambos finalizarem com timeout de 2 minutos
        CompletableFuture.allOf(openSerpFuture, domainFuture)
                .orTimeout(2, TimeUnit.MINUTES)
                .join();

        // Quando nenhum domínio foi informado, busca redes sociais, telefones
        // e e-mails nos sites .com/.com.br encontrados pelo OpenSERP
        if (!StringUtils.hasText(domain)) {
            scrapeDotComSites(lead, name);
        }

        // Filtra socialLinks para manter apenas os que correspondem ao
        // nome exato ou e-mail exato da pessoa
        List<String> filteredSocialLinks = filterSocialLinksByPerson(
                lead.getSocialLinks(), lead.getName(), lead.getEmail());
        lead.setSocialLinks(filteredSocialLinks);

        // Se o reenriquecimento não encontrou dados novos (ex: CAPTCHA),
        // restaura os dados anteriores para não perder informação
        restoreIfEmpty(lead, "dnsMxRecords", oldSnapshot, list -> lead.setDnsMxRecords((List<String>) list));
        restoreIfEmpty(lead, "dnsARecords", oldSnapshot, list -> lead.setDnsARecords((List<String>) list));
        restoreIfEmpty(lead, "dnsAaaaRecords", oldSnapshot, list -> lead.setDnsAaaaRecords((List<String>) list));
        restoreIfEmpty(lead, "dnsCnameRecords", oldSnapshot, list -> lead.setDnsCnameRecords((List<String>) list));
        restoreIfEmpty(lead, "dnsTxtRecords", oldSnapshot, list -> lead.setDnsTxtRecords((List<String>) list));
        restoreIfEmpty(lead, "technologies", oldSnapshot, list -> lead.setTechnologies((List<String>) list));
        restoreIfEmpty(lead, "socialLinks", oldSnapshot, list -> lead.setSocialLinks((List<String>) list));
        restoreIfEmpty(lead, "socialProfileSummaries", oldSnapshot, list -> lead.setSocialProfileSummaries((List<String>) list));
        restoreIfEmpty(lead, "exposedEmails", oldSnapshot, list -> lead.setExposedEmails((List<String>) list));
        restoreIfEmpty(lead, "exposedPhones", oldSnapshot, list -> lead.setExposedPhones((List<String>) list));
        restoreIfEmpty(lead, "nameMentions", oldSnapshot, list -> lead.setNameMentions((List<String>) list));
        restoreIfEmpty(lead, "foundDocuments", oldSnapshot, list -> lead.setFoundDocuments((List<String>) list));
        restoreIfEmpty(lead, "discoveredUrls", oldSnapshot, list -> lead.setDiscoveredUrls((List<String>) list));
        restoreIfEmpty(lead, "openSerpRawData", oldSnapshot, val -> lead.setOpenSerpRawData((String) val));
        restoreIfEmpty(lead, "rdapRawData", oldSnapshot, val -> lead.setRdapRawData((String) val));
        restoreIfEmpty(lead, "rdapRegistrar", oldSnapshot, val -> lead.setRdapRegistrar((String) val));
        restoreIfEmpty(lead, "rdapRegistrantName", oldSnapshot, val -> lead.setRdapRegistrantName((String) val));
        restoreIfEmpty(lead, "rdapRegistrantEmail", oldSnapshot, val -> lead.setRdapRegistrantEmail((String) val));
        restoreIfEmpty(lead, "rdapRegistrationDate", oldSnapshot, val -> lead.setRdapRegistrationDate((LocalDateTime) val));
        restoreIfEmpty(lead, "rdapExpirationDate", oldSnapshot, val -> lead.setRdapExpirationDate((LocalDateTime) val));
        restoreIfEmpty(lead, "rdapNameservers", oldSnapshot, list -> lead.setRdapNameservers((List<String>) list));
        restoreIfEmpty(lead, "rdapStatus", oldSnapshot, list -> lead.setRdapStatus((List<String>) list));
        restoreIfEmpty(lead, "rdapTaxpayerId", oldSnapshot, val -> lead.setRdapTaxpayerId((String) val));
        restoreIfEmpty(lead, "rdapSource", oldSnapshot, val -> lead.setRdapSource((String) val));

        lead.setUpdatedAt(LocalDateTime.now());

        // Transação curta — apenas o save, sem HTTP calls
        Lead savedLead = transactionTemplate.execute(status -> leadRepository.save(lead));
        log.info("Lead enriquecido: {}", logId);
        return savedLead;
    }

    /**
     * Filtra a lista de links de redes sociais para manter apenas aqueles
     * cuja URL contenha o nome exato ou o e-mail exato da pessoa.
     * <p>
     * Critérios de correspondência (case-insensitive):
     * <ul>
     *   <li>Parte local do e-mail (ex: "joao.silva" de "joao.silva@exemplo.com")</li>
     *   <li>Cada palavra do nome com 3+ caracteres (ex: "joao", "silva")</li>
     *   <li>Nome completo (ex: "joaosilva", "joao-silva", "joão silva")</li>
     * </ul>
     *
     * @param socialLinks lista original de links sociais
     * @param name        nome completo da pessoa
     * @param email       e-mail completo da pessoa
     * @return lista filtrada contendo apenas links que correspondem à pessoa
     */
    static List<String> filterSocialLinksByPerson(List<String> socialLinks, String name, String email) {
        if (socialLinks == null || socialLinks.isEmpty()) return new ArrayList<>();
        if (name == null && email == null) return new ArrayList<>();

        // Monta termos de busca a partir do nome e email
        Set<String> searchTerms = new LinkedHashSet<>();
        String lowerName = name != null ? name.toLowerCase().strip() : "";
        String lowerEmail = email != null ? email.toLowerCase().strip() : "";

        // Parte local do e-mail (ex: "joao.silva" de "joao.silva@gmail.com")
        if (lowerEmail.contains("@")) {
            String localPart = lowerEmail.substring(0, lowerEmail.indexOf("@"));
            if (!localPart.isBlank()) {
                searchTerms.add(localPart);
                // Também tenta sem pontos (ex: "joaosilva")
                searchTerms.add(localPart.replace(".", ""));
                searchTerms.add(localPart.replace("-", ""));
                searchTerms.add(localPart.replace("_", ""));
            }
        }

        // Palavras do nome com 3+ caracteres
        if (!lowerName.isBlank()) {
            // Remove acentos para comparação
            String normalized = java.text.Normalizer.normalize(lowerName, java.text.Normalizer.Form.NFD)
                    .replaceAll("[\\u0300-\\u036f]", "");
            for (String word : normalized.split("\\s+")) {
                if (word.length() >= 3) {
                    searchTerms.add(word);
                }
            }
            // Nome completo sem espaços
            String fullNameNoSpace = normalized.replaceAll("\\s+", "");
            if (fullNameNoSpace.length() >= 5) {
                searchTerms.add(fullNameNoSpace);
            }
            // Nome completo com hífen
            String fullNameHyphen = normalized.replaceAll("\\s+", "-");
            searchTerms.add(fullNameHyphen);
            // Nome completo com underline
            String fullNameUnderscore = normalized.replaceAll("\\s+", "_");
            searchTerms.add(fullNameUnderscore);
        }

        log.debug("Termos para filtro de socialLinks: {}", searchTerms);

        // Filtra URLs que contenham qualquer termo — retorna ArrayList mutável
        // para o Hibernate conseguir gerenciar o @ElementCollection
        return socialLinks.stream()
                .filter(link -> {
                    if (link == null) return false;
                    String lowerLink = link.toLowerCase();
                    return searchTerms.stream().anyMatch(lowerLink::contains);
                })
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    /**
     * Restaura um campo do lead a partir do snapshot anterior se o valor
     * atual estiver vazio (null, lista vazia, string vazia).
     * <p>
     * Garante que reenriquecimentos com falha (ex: CAPTCHA) não destruam
     * dados que já haviam sido enriquecidos anteriormente.
     */
    @SuppressWarnings("unchecked")
    private void restoreIfEmpty(Lead lead, String fieldName, Map<String, Object> snapshot, Consumer<Object> setter) {
        Object current = switch (fieldName) {
            case "openSerpRawData", "rdapRawData", "rdapRegistrar", "rdapRegistrantName",
                 "rdapRegistrantEmail", "rdapTaxpayerId", "rdapSource" -> {
                String val = (String) switch (fieldName) {
                    case "openSerpRawData" -> lead.getOpenSerpRawData();
                    case "rdapRawData" -> lead.getRdapRawData();
                    case "rdapRegistrar" -> lead.getRdapRegistrar();
                    case "rdapRegistrantName" -> lead.getRdapRegistrantName();
                    case "rdapRegistrantEmail" -> lead.getRdapRegistrantEmail();
                    case "rdapTaxpayerId" -> lead.getRdapTaxpayerId();
                    case "rdapSource" -> lead.getRdapSource();
                    default -> null;
                };
                yield val;
            }
            case "rdapRegistrationDate", "rdapExpirationDate" -> {
                LocalDateTime val = switch (fieldName) {
                    case "rdapRegistrationDate" -> lead.getRdapRegistrationDate();
                    case "rdapExpirationDate" -> lead.getRdapExpirationDate();
                    default -> null;
                };
                yield val;
            }
            default -> {
                List<String> val = switch (fieldName) {
                    case "dnsMxRecords" -> lead.getDnsMxRecords();
                    case "dnsARecords" -> lead.getDnsARecords();
                    case "dnsAaaaRecords" -> lead.getDnsAaaaRecords();
                    case "dnsCnameRecords" -> lead.getDnsCnameRecords();
                    case "dnsTxtRecords" -> lead.getDnsTxtRecords();
                    case "technologies" -> lead.getTechnologies();
                    case "socialLinks" -> lead.getSocialLinks();
                    case "socialProfileSummaries" -> lead.getSocialProfileSummaries();
                    case "exposedEmails" -> lead.getExposedEmails();
                    case "exposedPhones" -> lead.getExposedPhones();
                    case "nameMentions" -> lead.getNameMentions();
                    case "foundDocuments" -> lead.getFoundDocuments();
                    case "discoveredUrls" -> lead.getDiscoveredUrls();
                    case "rdapNameservers" -> lead.getRdapNameservers();
                    case "rdapStatus" -> lead.getRdapStatus();
                    default -> null;
                };
                yield val;
            }
        };

        boolean isEmpty = current == null
                || (current instanceof String s && s.isBlank())
                || (current instanceof List<?> l && l.isEmpty());

        if (isEmpty) {
            Object oldValue = snapshot.get(fieldName);
            if (oldValue != null) {
                setter.accept(oldValue);
                log.debug("Campo '{}' restaurado do snapshot anterior (reenriquecimento não trouxe dados)", fieldName);
            }
        }
    }

    /**
     * Quando o domínio não foi informado, percorre os URLs descobertos pelo
     * OpenSERP que terminam com .com, .com.br ou .br e faz scraping de cada um
     * para extrair redes sociais, telefones e e-mails.
     * <p>
     * Os dados são mesclados com os resultados já obtidos pelo OpenSERP.
     */
    private void scrapeDotComSites(Lead lead, String name) {
        List<String> urls = lead.getDiscoveredUrls();
        if (urls == null || urls.isEmpty()) {
            log.info("Nenhum URL descoberto pelo OpenSERP para scraping .com/.com.br/.br");
            return;
        }

        // Filtra apenas URLs de domínios .com, .com.br ou .br
        List<String> dotComUrls = urls.stream()
                .filter(url -> {
                    String lower = url.toLowerCase();
                    try {
                        String host = new java.net.URL(lower).getHost();
                        return host != null && (host.endsWith(".com") || host.endsWith(".br"));
                    } catch (Exception e) {
                        return false;
                    }
                })
                .distinct()
                .limit(10) // limita a 10 sites para não sobrecarregar
                .toList();

        if (dotComUrls.isEmpty()) {
            log.info("Nenhum site .com/.com.br/.br encontrado entre os URLs descobertos");
            return;
        }

        log.info("Scraping de {} site(s) .com/.com.br/.br para redes sociais, telefones e e-mails", dotComUrls.size());

        Set<String> mergedSocialLinks = new LinkedHashSet<>(
                lead.getSocialLinks() != null ? lead.getSocialLinks() : List.of());
        Set<String> mergedEmails = new LinkedHashSet<>(
                lead.getExposedEmails() != null ? lead.getExposedEmails() : List.of());
        Set<String> mergedPhones = new LinkedHashSet<>(
                lead.getExposedPhones() != null ? lead.getExposedPhones() : List.of());

        for (String url : dotComUrls) {
            try {
                // Extrai o domínio da URL para usar no SocialDiscoveryService
                String domainForSocial = new java.net.URL(url).getHost();

                // 1. Redes sociais — usa o serviço especializado
                List<String> socialLinks = socialDiscoveryService.discoverSocialLinks(domainForSocial);
                if (socialLinks != null) {
                    mergedSocialLinks.addAll(socialLinks);
                }

                // 2. Telefones e e-mails — faz fetch do HTML e extrai via regex
                String html = restTemplate.getForObject(url, String.class);
                if (html != null && !html.isBlank()) {
                    Document doc = Jsoup.parse(html);
                    String pageText = doc.text();

                    // Extrai e-mails do texto da página
                    Matcher emailMatcher = DataParser.EMAIL_PATTERN.matcher(pageText);
                    while (emailMatcher.find()) {
                        String foundEmail = emailMatcher.group().toLowerCase();
                        if (!foundEmail.contains("example.com")) {
                            mergedEmails.add(foundEmail);
                        }
                    }

                    // Extrai telefones do texto da página
                    Matcher phoneMatcher = DataParser.PHONE_PATTERN.matcher(pageText);
                    while (phoneMatcher.find()) {
                        String phone = phoneMatcher.group().strip();
                        String digits = phone.replaceAll("\\D", "");
                        if (digits.length() >= 10 && digits.length() <= 15) {
                            mergedPhones.add(phone);
                        }
                    }
                }

                log.debug("Scraping concluído para {}", url);
            } catch (Exception e) {
                log.debug("Falha ao scrapear {}: {}", url, e.getMessage());
            }
        }

        // Atualiza o lead com dados mesclados
        lead.setSocialLinks(new ArrayList<>(mergedSocialLinks));
        lead.setExposedEmails(new ArrayList<>(mergedEmails));
        lead.setExposedPhones(new ArrayList<>(mergedPhones));
        lead.setDorkFindings(lead.getExposedEmails().size());

        log.info("Scraping .com/.com.br concluído: {} sociais, {} e-mails, {} telefones",
                mergedSocialLinks.size(), mergedEmails.size(), mergedPhones.size());
    }

    private Lead createNewLead(String email, String domain, String name) {
        LocalDateTime now = LocalDateTime.now();
        return Lead.builder()
                .email(email).domain(domain).name(name)
                .consentGiven(true).consentDate(now)
                .dataRetentionUntil(now.plusDays(DATA_RETENTION_DAYS))
                .createdAt(now).status(DEFAULT_STATUS)
                .build();
    }

}
