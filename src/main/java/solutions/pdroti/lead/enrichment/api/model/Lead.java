package solutions.pdroti.lead.enrichment.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import solutions.pdroti.lead.enrichment.api.config.EncryptedEmailConverter;
import solutions.pdroti.lead.enrichment.api.util.EmailUtils;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/** Entidade JPA que representa um lead enriquecido com dados de domínio. */
@Entity
@Table(name = "leads")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Lead implements Serializable {

    // === Identidade ===

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = EncryptedEmailConverter.class)
    private String email;

    /**
     * Hash SHA-256 do e-mail (lowercase) para consulta por e-mail.
     * O e-mail em si é criptografado no banco, impossibilitando consultas
     * diretas. Este campo permite lookup sem expor o dado original.
     * <p>
     * {@code unique = true} garante que não haja leads duplicados por e-mail.
     */
    @Column(length = 64, unique = true)
    private String emailHash;

    // === Dados de domínio (enriquecidos) ===

    private String domain;
    private String name;
    private boolean mxStatus;
    private String status;

    // === DNS — todos os registros consultados ===

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> dnsMxRecords;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> dnsARecords;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> dnsAaaaRecords;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> dnsCnameRecords;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> dnsTxtRecords;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> technologies;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> socialLinks;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> socialProfileSummaries;

    // === Google Dorks — dados de info exposta ===

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> exposedEmails;

    private int dorkFindings;

    @ElementCollection(fetch = FetchType.EAGER)
    @Column(length = 2048)
    private List<String> nameMentions;

    // === RDAP — dados de registro de domínio ===

    @Column(columnDefinition = "TEXT")
    private String rdapRawData;

    @Column(length = 200)
    private String rdapRegistrar;

    @Column(length = 200)
    private String rdapRegistrantName;

    @Column(length = 255)
    private String rdapRegistrantEmail;

    private LocalDateTime rdapRegistrationDate;
    private LocalDateTime rdapExpirationDate;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> rdapNameservers;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> rdapStatus;

    @Column(length = 20)
    private String rdapTaxpayerId;

    @Column(length = 50)
    private String rdapSource;

    // === OpenSERP — dados brutos da busca ===

    @Column(columnDefinition = "TEXT")
    private String serperRawData;

    // === LGPD — consentimento e retenção ===

    private Boolean consentGiven;
    private LocalDateTime consentDate;
    private LocalDateTime dataRetentionUntil;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;

    /** Retorna true se o lead já foi persistido (id != null). */
    public boolean isPresent() {
        return id != null;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }

    /**
     * Define o e-mail e automaticamente computa o hash SHA-256 para lookup.
     * Se o e-mail for null ou vazio, o hash também é null.
     */
    public void setEmail(String email) {
        this.email = email;
        this.emailHash = email != null && !email.isBlank()
                ? EmailUtils.hash(email)
                : null;
    }

    public String getEmailHash() { return emailHash; }
    public void setEmailHash(String emailHash) { this.emailHash = emailHash; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean getMxStatus() { return mxStatus; }
    public void setMxStatus(boolean mxValid) { this.mxStatus = mxValid; }

    public List<String> getDnsMxRecords() { return dnsMxRecords; }
    public void setDnsMxRecords(List<String> dnsMxRecords) { this.dnsMxRecords = dnsMxRecords; }

    public List<String> getDnsARecords() { return dnsARecords; }
    public void setDnsARecords(List<String> dnsARecords) { this.dnsARecords = dnsARecords; }

    public List<String> getDnsAaaaRecords() { return dnsAaaaRecords; }
    public void setDnsAaaaRecords(List<String> dnsAaaaRecords) { this.dnsAaaaRecords = dnsAaaaRecords; }

    public List<String> getDnsCnameRecords() { return dnsCnameRecords; }
    public void setDnsCnameRecords(List<String> dnsCnameRecords) { this.dnsCnameRecords = dnsCnameRecords; }

    public List<String> getDnsTxtRecords() { return dnsTxtRecords; }
    public void setDnsTxtRecords(List<String> dnsTxtRecords) { this.dnsTxtRecords = dnsTxtRecords; }

    public List<String> getTechnologies() { return technologies; }
    public void setTechnologies(List<String> technologies) { this.technologies = technologies; }

    public List<String> getSocialLinks() { return socialLinks; }
    public void setSocialLinks(List<String> socialLinks) { this.socialLinks = socialLinks; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<String> getSocialProfileSummaries() { return socialProfileSummaries; }
    public void setSocialProfileSummaries(List<String> socialProfileSummaries) {
        this.socialProfileSummaries = socialProfileSummaries;
    }

    public List<String> getExposedEmails() { return exposedEmails; }
    public void setExposedEmails(List<String> exposedEmails) { this.exposedEmails = exposedEmails; }

    public int getDorkFindings() { return dorkFindings; }
    public void setDorkFindings(int dorkFindings) { this.dorkFindings = dorkFindings; }

    public List<String> getNameMentions() { return nameMentions; }
    public void setNameMentions(List<String> nameMentions) { this.nameMentions = nameMentions; }

    // === RDAP Getters/Setters ===

    public String getRdapRawData() { return rdapRawData; }
    public void setRdapRawData(String rdapRawData) { this.rdapRawData = rdapRawData; }

    public String getRdapRegistrar() { return rdapRegistrar; }
    public void setRdapRegistrar(String rdapRegistrar) { this.rdapRegistrar = rdapRegistrar; }

    public String getRdapRegistrantName() { return rdapRegistrantName; }
    public void setRdapRegistrantName(String rdapRegistrantName) { this.rdapRegistrantName = rdapRegistrantName; }

    public String getRdapRegistrantEmail() { return rdapRegistrantEmail; }
    public void setRdapRegistrantEmail(String rdapRegistrantEmail) { this.rdapRegistrantEmail = rdapRegistrantEmail; }

    public LocalDateTime getRdapRegistrationDate() { return rdapRegistrationDate; }
    public void setRdapRegistrationDate(LocalDateTime rdapRegistrationDate) { this.rdapRegistrationDate = rdapRegistrationDate; }

    public LocalDateTime getRdapExpirationDate() { return rdapExpirationDate; }
    public void setRdapExpirationDate(LocalDateTime rdapExpirationDate) { this.rdapExpirationDate = rdapExpirationDate; }

    public List<String> getRdapNameservers() { return rdapNameservers; }
    public void setRdapNameservers(List<String> rdapNameservers) { this.rdapNameservers = rdapNameservers; }

    public List<String> getRdapStatus() { return rdapStatus; }
    public void setRdapStatus(List<String> rdapStatus) { this.rdapStatus = rdapStatus; }

    public String getRdapTaxpayerId() { return rdapTaxpayerId; }
    public void setRdapTaxpayerId(String rdapTaxpayerId) { this.rdapTaxpayerId = rdapTaxpayerId; }

    public String getRdapSource() { return rdapSource; }
    public void setRdapSource(String rdapSource) { this.rdapSource = rdapSource; }

    public String getSerperRawData() { return serperRawData; }
    public void setSerperRawData(String serperRawData) { this.serperRawData = serperRawData; }

    public Boolean isConsentGiven() { return consentGiven; }
    public void setConsentGiven(Boolean consentGiven) { this.consentGiven = consentGiven; }

    public LocalDateTime getConsentDate() { return consentDate; }
    public void setConsentDate(LocalDateTime consentDate) { this.consentDate = consentDate; }

    public LocalDateTime getDataRetentionUntil() { return dataRetentionUntil; }
    public void setDataRetentionUntil(LocalDateTime dataRetentionUntil) { this.dataRetentionUntil = dataRetentionUntil; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

}
