package solutions.pdroti.lead.enrichment.api.model;

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

    // === Dados de domínio (enriquecidos) ===

    private String domain;
    private boolean mxStatus;
    private String status;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> technologies;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> socialLinks;

    // === Google Dorks — dados de info exposta ===

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> exposedEmails;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> exposedPhones;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> exposedAdminPaths;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> exposedDocuments;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> exposedConfigFiles;

    private int dorkFindings;

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
    public void setEmail(String email) { this.email = email; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public boolean getMxStatus() { return mxStatus; }
    public void setMxStatus(boolean mxValid) { this.mxStatus = mxValid; }

    public List<String> getTechnologies() { return technologies; }
    public void setTechnologies(List<String> technologies) { this.technologies = technologies; }

    public List<String> getSocialLinks() { return socialLinks; }
    public void setSocialLinks(List<String> socialLinks) { this.socialLinks = socialLinks; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<String> getExposedEmails() { return exposedEmails; }
    public void setExposedEmails(List<String> exposedEmails) { this.exposedEmails = exposedEmails; }

    public List<String> getExposedPhones() { return exposedPhones; }
    public void setExposedPhones(List<String> exposedPhones) { this.exposedPhones = exposedPhones; }

    public List<String> getExposedAdminPaths() { return exposedAdminPaths; }
    public void setExposedAdminPaths(List<String> exposedAdminPaths) { this.exposedAdminPaths = exposedAdminPaths; }

    public List<String> getExposedDocuments() { return exposedDocuments; }
    public void setExposedDocuments(List<String> exposedDocuments) { this.exposedDocuments = exposedDocuments; }

    public List<String> getExposedConfigFiles() { return exposedConfigFiles; }
    public void setExposedConfigFiles(List<String> exposedConfigFiles) { this.exposedConfigFiles = exposedConfigFiles; }

    public int getDorkFindings() { return dorkFindings; }
    public void setDorkFindings(int dorkFindings) { this.dorkFindings = dorkFindings; }

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
