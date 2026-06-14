package solutions.pdroti.lead.enrichment.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import solutions.pdroti.lead.enrichment.api.config.EncryptedEmailConverter;
import solutions.pdroti.lead.enrichment.api.util.EmailUtils;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/** Entidade JPA que representa um lead enriquecido com dados de domínio. */
@Entity
@Table(name = "leads", indexes = {
        @Index(name = "idx_lead_domain", columnList = "domain"),
        @Index(name = "idx_lead_domain_status", columnList = "domain, status"),
        @Index(name = "idx_lead_status", columnList = "status")
})
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@BatchSize(size = 10)
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

    @ElementCollection(fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
    @Column(length = 2048)
    private List<String> dnsMxRecords;

    @ElementCollection(fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
    private List<String> dnsARecords;

    @ElementCollection(fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
    private List<String> dnsAaaaRecords;

    @ElementCollection(fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
    private List<String> dnsCnameRecords;

    @ElementCollection(fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
    @Column(length = 4096)
    private List<String> dnsTxtRecords;

    @ElementCollection(fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
    private List<String> technologies;

    @ElementCollection(fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
    @Column(length = 2048)
    private List<String> socialLinks;

    @ElementCollection(fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
    @Column(length = 2048)
    private List<String> socialProfileSummaries;

    // === Google Dorks — dados de info exposta ===

    @ElementCollection(fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
    @Column(length = 2048)
    private List<String> exposedEmails;

    @ElementCollection(fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
    @Column(length = 50)
    private List<String> exposedPhones;

    private int dorkFindings;

    @ElementCollection(fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
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

    @ElementCollection(fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
    @Column(length = 2048)
    private List<String> rdapNameservers;

    @ElementCollection(fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
    @Column(length = 512)
    private List<String> rdapStatus;

    @Column(length = 20)
    private String rdapTaxpayerId;

    @Column(length = 50)
    private String rdapSource;

    // === OpenSERP — dados brutos da busca ===

    @Column(columnDefinition = "TEXT")
    private String openSerpRawData;

    @ElementCollection(fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
    @Column(length = 2048)
    private List<String> foundDocuments;

    @ElementCollection(fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
    @Column(length = 2048)
    private List<String> discoveredUrls;

    // === LGPD — consentimento e retenção ===

    private Boolean consentGiven;
    private LocalDateTime consentDate;
    private LocalDateTime dataRetentionUntil;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
    private LocalDateTime updatedAt;

    /** Versionamento para lock otimista (evita race conditions em reenriquecimento). */
    @Version
    @Builder.Default
    private Long version = 0L;

    /** Retorna true se o lead já foi persistido (id != null). */
    public boolean isPresent() {
        return id != null;
    }

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

    /**
     * Getter explícito para {@code mxStatus} porque {@code @Getter} em
     * {@code boolean} primitivo geraria {@code isMxStatus()}, mas o código
     * existente chama {@code getMxStatus()}.
     */
    public boolean getMxStatus() { return mxStatus; }
    public void setMxStatus(boolean mxStatus) { this.mxStatus = mxStatus; }

}
