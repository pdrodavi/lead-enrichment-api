package solutions.pdroti.lead.enrichment.api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solutions.pdroti.lead.enrichment.api.dto.SocialProfileData;
import solutions.pdroti.lead.enrichment.api.model.Lead;
import solutions.pdroti.lead.enrichment.api.repository.LeadRepository;
import solutions.pdroti.lead.enrichment.api.util.EmailUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
    private final NameSearchService nameSearchService;
    private static final int DATA_RETENTION_DAYS = 365;
    private static final String DEFAULT_STATUS = "ACTIVE";
    private static final String DELETED_STATUS = "DELETED";

    /**
     * Enriquece um lead: busca por email ou nome e atualiza se existir,
     * ou cria novo se não encontrar.
     * Apenas o nome é obrigatório; email e domain são opcionais.
     */
    @Transactional
    public Lead enrich(String email, String domain, String name) {
        log.info("Enriquecendo lead: nome={} email={} domain={}", name, maskEmail(email), domain);

        Lead existing = findExistingLead(email, name);
        if (existing != null) {
            log.info("Lead já existe, reenriquecendo: ID={}", existing.getId());
        }

        return performFullEnrichment(existing, email, domain, name);
    }

    /**
     * Enriquece um lead extraindo o domínio do e-mail (se houver).
     */
    @Transactional
    public Lead enrichLead(Lead lead) {
        String domain = lead.getEmail() != null
                ? extractDomainFromEmail(lead.getEmail())
                : null;
        return enrich(lead.getEmail(), domain, lead.getName());
    }

    /**
     * Retorna todos os leads ativos (exclui soft-deleted).
     */
    public List<Lead> listAll() {
        return leadRepository.findByStatus(DEFAULT_STATUS);
    }

    /**
     * Busca lead por ID string (convertido internamente para Long).
     * Retorna apenas se não estiver soft-deleted.
     */
    public Optional<Lead> findById(String id) {
        return parseNumericId(id)
                .flatMap(leadRepository::findById)
                .filter(lead -> !DELETED_STATUS.equals(lead.getStatus()));
    }

    /**
     * Soft delete: marca data/hora de exclusão e altera status.
     */
    @Transactional
    public boolean softDelete(String id) {
        return parseNumericId(id)
                .flatMap(leadRepository::findById)
                .map(this::performSoftDelete)
                .orElse(false);
    }

    /**
     * Hard delete: remove fisicamente do banco.
     */
    @Transactional
    public boolean hardDelete(String id) {
        return parseNumericId(id)
                .filter(leadRepository::existsById)
                .map(this::performHardDelete)
                .orElse(false);
    }

    // ==================== Métodos Privados ====================

    /** Busca lead existente por email (prioridade) ou nome. */
    private Lead findExistingLead(String email, String name) {
        if (hasText(email)) {
            return leadRepository.findByEmail(email).orElse(null);
        }
        if (hasText(name)) {
            return leadRepository.findByName(name).orElse(null);
        }
        return null;
    }

    /** Executa todas as etapas de enrichment e persiste o resultado. */
    private Lead performFullEnrichment(Lead existing, String email, String domain, String name) {
        Lead lead = existing != null ? existing : createNewLead(email, domain, name);

        // Atualiza campos caso exista
        lead.setEmail(email);
        lead.setDomain(domain);
        lead.setName(name);
        lead.setCreatedAt(LocalDateTime.now());

        String logId = maskEmail(email);
        if (logId == null) logId = name;

        // Reseta dados de enriquecimento anterior (para reenriquecer limpo)
        lead.setMxStatus(false);
        lead.setTechnologies(null);
        lead.setSocialLinks(null);
        lead.setSocialProfileSummaries(null);
        lead.setExposedEmails(null);
        lead.setExposedPhones(null);
        lead.setExposedAdminPaths(null);
        lead.setExposedDocuments(null);
        lead.setExposedConfigFiles(null);
        lead.setNameMentions(null);
        lead.setDorkFindings(0);

        if (hasText(domain)) {
            executeSafely(() -> dnsValidationService.hasMxRecord(domain),
                    lead::setMxStatus, logId);

            lead.setTechnologies(scrapeSafely(
                    () -> techScraperService.scrapeTechnologies(domain)));

            List<String> socialLinks = scrapeSafely(
                    () -> socialDiscoveryService.discoverSocialLinks(domain));
            lead.setSocialLinks(socialLinks);
            if (!socialLinks.isEmpty()) {
                lead.setSocialProfileSummaries(
                        socialDiscoveryService.scrapeSocialProfiles(socialLinks)
                                .stream().map(SocialProfileData::toSummary).toList()
                );
            }

            enrichWithDorks(lead, domain);

            // Só persiste se o nome completo for encontrado no HTML do domínio
            boolean fullNameFound = lead.getNameMentions() != null
                    && lead.getNameMentions().stream()
                            .anyMatch(m -> m.startsWith("Nome completo encontrado"));
            if (!fullNameFound) {
                String msg = "Nome \"" + name + "\" não encontrado no domínio " + domain;
                log.warn(msg);
                throw new IllegalArgumentException(msg);
            }
        } else {
            // Sem domínio — busca pelo nome no DuckDuckGo
            log.info("Sem domínio — buscando '{}' no DuckDuckGo", name);

            lead.setSocialLinks(scrapeSafely(
                    () -> nameSearchService.searchSocialLinks(name)));

            List<String> emails = scrapeSafely(
                    () -> nameSearchService.searchEmails(name));
            lead.setExposedEmails(emails);
            if (!emails.isEmpty()) {
                lead.setDorkFindings(emails.size());
            }

            // Scrapeia perfis sociais encontrados na busca
            if (!lead.getSocialLinks().isEmpty()) {
                lead.setSocialProfileSummaries(
                        socialDiscoveryService.scrapeSocialProfiles(lead.getSocialLinks())
                                .stream().map(SocialProfileData::toSummary).toList()
                );
            }

            // Verifica se o nome aparece nos resultados
            lead.setNameMentions(scrapeSafely(
                    () -> nameSearchService.searchNameMentions(name)));
        }

        Lead savedLead = leadRepository.save(lead);
        log.info("Lead enriquecido: {}", logId);
        return savedLead;
    }

    /** Enriquece com dados de Google Dorks (emails, docs expostos, info pública, nome). */
    private void enrichWithDorks(Lead lead, String domain) {
        try {
            String name = lead.getName();
            var dorkResult = techScraperService.scanDorks(domain, name);
            lead.setExposedEmails(toMutable(dorkResult.exposedEmails()));
            lead.setExposedPhones(toMutable(dorkResult.exposedPhones()));
            lead.setExposedAdminPaths(toMutable(dorkResult.exposedAdminPaths()));
            lead.setExposedDocuments(toMutable(dorkResult.exposedDocuments()));
            lead.setExposedConfigFiles(toMutable(dorkResult.exposedConfigFiles()));
            lead.setNameMentions(toMutable(dorkResult.nameMentions()));
            lead.setDorkFindings(dorkResult.totalFindings());
            if (dorkResult.totalFindings() > 0) {
                log.info("Dorks encontrou {} itens para {}", dorkResult.totalFindings(), domain);
            }
        } catch (Exception e) {
            log.debug("Dorks scan ignorado para {}: {}", domain, e.getMessage());
        }
    }

    /** Cria lead com valores padrão (consentimento, retenção LGPD, status, nome). */
    private Lead createNewLead(String email, String domain, String name) {
        LocalDateTime now = LocalDateTime.now();
        return Lead.builder()
                .email(email).domain(domain).name(name)
                .consentGiven(true).consentDate(now)
                .dataRetentionUntil(now.plusDays(DATA_RETENTION_DAYS))
                .createdAt(now).status(DEFAULT_STATUS)
                .build();
    }

    /** Marca o lead como deletado (soft delete). */
    private boolean performSoftDelete(Lead lead) {
        lead.setDeletedAt(LocalDateTime.now());
        lead.setStatus(DELETED_STATUS);
        leadRepository.save(lead);
        log.info("Lead soft deleted: ID={}", lead.getId());
        return true;
    }

    /** Remove lead do banco pelo ID (hard delete). */
    private boolean performHardDelete(Long id) {
        var lead = leadRepository.findById(id);
        if (lead.isEmpty()) {
            log.warn("Lead não encontrado para hard delete: ID={}", id);
            return false;
        }
        leadRepository.deleteById(id);
        log.info("Lead hard deleted: ID={}", id);
        return true;
    }

    /**
     * Executa um supplier e aplica o resultado via setter com fallback em caso de
     * erro.
     */
    private <T> void executeSafely(Supplier<T> supplier, Consumer<T> setter, String logId) {
        executeSafely(supplier, setter, logId, null);
    }

    /**
     * Executa um supplier e aplica o resultado via setter com fallback em caso de
     * erro.
     */
    private <T> void executeSafely(Supplier<T> supplier, Consumer<T> setter, String logId, T fallback) {
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

    /** Extrai domínio do e-mail (parte após @). */
    private String extractDomainFromEmail(String email) {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Email inválido: " + email);
        }
        return email.substring(email.indexOf("@") + 1);
    }

    /** Retorna true se a string não for nula nem blank. */
    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * Executa supplier de lista com try-catch e converte resultado em ArrayList mutável.
     * Evita UnsupportedOperationException do Hibernate com listas imutáveis.
     */
    private static List<String> scrapeSafely(java.util.function.Supplier<List<String>> supplier) {
        try {
            List<String> result = supplier.get();
            return result != null ? new java.util.ArrayList<>(result) : new java.util.ArrayList<>();
        } catch (Exception e) {
            log.warn("Erro ao buscar dados: {}", e.getMessage());
            return new java.util.ArrayList<>();
        }
    }

    /** Converte lista imutável em mutável para o Hibernate persistir. */
    private static <T> List<T> toMutable(List<T> list) {
        return list != null ? new java.util.ArrayList<>(list) : new java.util.ArrayList<>();
    }

    /** Converte ID string para Long com log em caso de formato inválido. */
    private Optional<Long> parseNumericId(String id) {
        try {
            return Optional.of(Long.parseLong(id));
        } catch (NumberFormatException e) {
            log.warn("ID inválido: {}", id);
            return Optional.empty();
        }
    }

    /** Ofusca e-mail para logging (delega ao utilitário). */
    private String maskEmail(String email) {
        return email != null ? EmailUtils.mask(email) : null;
    }
}