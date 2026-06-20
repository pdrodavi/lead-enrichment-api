package solutions.pdroti.lead.enrichment.api.service;

import lombok.extern.slf4j.Slf4j;
import solutions.pdroti.lead.enrichment.api.model.Lead;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Gerencia snapshot e restauração dos campos de um {@link Lead}
 * durante o reenriquecimento.
 * <p>
 * Se o reenriquecimento falhar (ex: CAPTCHA no OpenSERP) e não trouxer
 * dados novos, os valores anteriores são preservados automaticamente
 * para evitar perda de informação.
 * <p>
 * Uso:
 * <pre>{@code
 *   var snapshot = EnrichmentSnapshotManager.takeSnapshot(lead);
 *   // ... realiza enrichment ...
 *   snapshot.restoreIfEmpty(lead);
 * }</pre>
 */
@Slf4j
public final class EnrichmentSnapshotManager {

    private static final String LOG_RESTORE = "Campo '{}' restaurado do snapshot";

    private final Map<String, Object> snapshot;

    private EnrichmentSnapshotManager(Map<String, Object> snapshot) {
        this.snapshot = snapshot;
    }

    /**
     * Cria um snapshot de todos os campos enriquecíveis do lead.
     * Usa {@link HashMap} que permite valores {@code null},
     * ao contrário de {@code Map.of()} / {@code Map.entry()}.
     *
     * @param lead lead antes do reset de enriquecimento
     * @return gerenciador com o snapshot pronto para restauração
     */
    public static EnrichmentSnapshotManager takeSnapshot(Lead lead) {
        Map<String, Object> snap = new HashMap<>();
        snap.put("dnsMxRecords", lead.getDnsMxRecords());
        snap.put("dnsARecords", lead.getDnsARecords());
        snap.put("dnsAaaaRecords", lead.getDnsAaaaRecords());
        snap.put("dnsCnameRecords", lead.getDnsCnameRecords());
        snap.put("dnsTxtRecords", lead.getDnsTxtRecords());
        snap.put("technologies", lead.getTechnologies());
        snap.put("socialLinks", lead.getSocialLinks());
        snap.put("socialProfileSummaries", lead.getSocialProfileSummaries());
        snap.put("exposedEmails", lead.getExposedEmails());
        snap.put("exposedPhones", lead.getExposedPhones());
        snap.put("nameMentions", lead.getNameMentions());
        snap.put("foundDocuments", lead.getFoundDocuments());
        snap.put("discoveredUrls", lead.getDiscoveredUrls());
        snap.put("openSerpRawData", lead.getOpenSerpRawData());
        snap.put("rdapRawData", lead.getRdapRawData());
        snap.put("rdapRegistrar", lead.getRdapRegistrar());
        snap.put("rdapRegistrantName", lead.getRdapRegistrantName());
        snap.put("rdapRegistrantEmail", lead.getRdapRegistrantEmail());
        snap.put("rdapRegistrationDate", lead.getRdapRegistrationDate());
        snap.put("rdapExpirationDate", lead.getRdapExpirationDate());
        snap.put("rdapNameservers", lead.getRdapNameservers());
        snap.put("rdapStatus", lead.getRdapStatus());
        snap.put("rdapTaxpayerId", lead.getRdapTaxpayerId());
        snap.put("rdapSource", lead.getRdapSource());
        log.debug("Snapshot criado com {} campos", snap.size());
        return new EnrichmentSnapshotManager(snap);
    }

    /**
     * Restaura campos do lead que ficaram vazios após o reenriquecimento.
     * Para cada campo, se o valor atual estiver vazio (null, lista vazia
     * ou string blank) e existir um valor no snapshot, o valor do snapshot
     * é restaurado.
     *
     * @param lead lead a ser verificado e possivelmente restaurado
     */
    public void restoreIfEmpty(Lead lead) {
        restoreString(lead, "openSerpRawData", lead.getOpenSerpRawData(), lead::setOpenSerpRawData);
        restoreString(lead, "rdapRawData", lead.getRdapRawData(), lead::setRdapRawData);
        restoreString(lead, "rdapRegistrar", lead.getRdapRegistrar(), lead::setRdapRegistrar);
        restoreString(lead, "rdapRegistrantName", lead.getRdapRegistrantName(), lead::setRdapRegistrantName);
        restoreString(lead, "rdapRegistrantEmail", lead.getRdapRegistrantEmail(), lead::setRdapRegistrantEmail);
        restoreString(lead, "rdapTaxpayerId", lead.getRdapTaxpayerId(), lead::setRdapTaxpayerId);
        restoreString(lead, "rdapSource", lead.getRdapSource(), lead::setRdapSource);
        restoreDate(lead, "rdapRegistrationDate", lead.getRdapRegistrationDate(), lead::setRdapRegistrationDate);
        restoreDate(lead, "rdapExpirationDate", lead.getRdapExpirationDate(), lead::setRdapExpirationDate);
        restoreList(lead, "dnsMxRecords", lead.getDnsMxRecords(), lead::setDnsMxRecords);
        restoreList(lead, "dnsARecords", lead.getDnsARecords(), lead::setDnsARecords);
        restoreList(lead, "dnsAaaaRecords", lead.getDnsAaaaRecords(), lead::setDnsAaaaRecords);
        restoreList(lead, "dnsCnameRecords", lead.getDnsCnameRecords(), lead::setDnsCnameRecords);
        restoreList(lead, "dnsTxtRecords", lead.getDnsTxtRecords(), lead::setDnsTxtRecords);
        restoreList(lead, "technologies", lead.getTechnologies(), lead::setTechnologies);
        restoreList(lead, "socialLinks", lead.getSocialLinks(), lead::setSocialLinks);
        restoreList(lead, "socialProfileSummaries", lead.getSocialProfileSummaries(), lead::setSocialProfileSummaries);
        restoreList(lead, "exposedEmails", lead.getExposedEmails(), lead::setExposedEmails);
        restoreList(lead, "exposedPhones", lead.getExposedPhones(), lead::setExposedPhones);
        restoreList(lead, "nameMentions", lead.getNameMentions(), lead::setNameMentions);
        restoreList(lead, "foundDocuments", lead.getFoundDocuments(), lead::setFoundDocuments);
        restoreList(lead, "discoveredUrls", lead.getDiscoveredUrls(), lead::setDiscoveredUrls);
        restoreList(lead, "rdapNameservers", lead.getRdapNameservers(), lead::setRdapNameservers);
        restoreList(lead, "rdapStatus", lead.getRdapStatus(), lead::setRdapStatus);
    }

    private void restoreString(Lead lead, String fieldName, String current, Consumer<String> setter) {
        if (current == null || current.isBlank()) {
            String old = (String) snapshot.get(fieldName);
            if (old != null) {
                setter.accept(old);
                log.debug(LOG_RESTORE, fieldName);
            }
        }
    }

    private void restoreDate(Lead lead, String fieldName, LocalDateTime current, Consumer<LocalDateTime> setter) {
        if (current == null) {
            LocalDateTime old = (LocalDateTime) snapshot.get(fieldName);
            if (old != null) {
                setter.accept(old);
                log.debug(LOG_RESTORE, fieldName);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void restoreList(Lead lead, String fieldName, List<String> current, Consumer<List<String>> setter) {
        if (current == null || current.isEmpty()) {
            List<String> old = (List<String>) snapshot.get(fieldName);
            if (old != null && !old.isEmpty()) {
                setter.accept(old);
                log.debug(LOG_RESTORE, fieldName);
            }
        }
    }
}
