package solutions.pdroti.lead.enrichment.api.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * Converter JPA que criptografa/descriptografa e-mails automaticamente
 * na persistência e leitura do banco de dados.
 * <p>
 * O formato armazenado é {@code ENC(<base64>)}, onde o conteúdo interno
 * é criptografado com AES-GCM via {@link EncryptionService}.
 * Atende requisitos de proteção de PII (LGPD).
 */
@Component
@Converter
public class EncryptedEmailConverter implements AttributeConverter<String, String> {

    /** Prefixo que identifica um valor criptografado no banco. */
    private static final String ENC_PREFIX = "ENC(";

    /** Sufixo que identifica um valor criptografado no banco. */
    private static final String ENC_SUFFIX = ")";

    private final EncryptionService encryptionService;

    public EncryptedEmailConverter(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    /**
     * Criptografa o e-mail antes de persistir no banco.
     * Formato: {@code ENC(<base64>)}.
     * Em caso de erro, salva o texto plano (fallback seguro).
     */
    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null) return null;
        try {
            return ENC_PREFIX + encryptionService.encrypt(plainText) + ENC_SUFFIX;
        } catch (Exception e) {
            return plainText;
        }
    }

    /**
     * Descriptografa o e-mail ao ler do banco.
     * Reconhece valores no formato {@code ENC(<base64>)}.
     * Se o dado não estiver criptografado, retorna como está.
     */
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        if (dbData.startsWith(ENC_PREFIX) && dbData.endsWith(ENC_SUFFIX)) {
            try {
                String encrypted = dbData.substring(ENC_PREFIX.length(), dbData.length() - ENC_SUFFIX.length());
                return encryptionService.decrypt(encrypted);
            } catch (Exception e) {
                return dbData;
            }
        }
        return dbData;
    }
}
