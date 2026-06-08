package solutions.pdroti.lead.enrichment.api.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/** Converter JPA que criptografa/descriptografa e-mails em trânsito para o banco. */
@Component
@Converter
public class EncryptedEmailConverter implements AttributeConverter<String, String> {

    private static final String ENC_PREFIX = "ENC(";
    private static final String ENC_SUFFIX = ")";

    private EncryptionService encryptionService;

    public EncryptedEmailConverter(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null) return null;
        try {
            return ENC_PREFIX + encryptionService.encrypt(plainText) + ENC_SUFFIX;
        } catch (Exception e) {
            return plainText;
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        if (dbData.startsWith(ENC_PREFIX) && dbData.endsWith(ENC_SUFFIX)) {
            try {
                String encrypted = dbData.substring(ENC_PREFIX.length(), dbData.length() - 1);
                return encryptionService.decrypt(encrypted);
            } catch (Exception e) {
                return dbData;
            }
        }
        return dbData;
    }
}
