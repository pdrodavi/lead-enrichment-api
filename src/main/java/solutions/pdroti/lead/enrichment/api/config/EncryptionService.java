package solutions.pdroti.lead.enrichment.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** Serviço de criptografia AES-GCM para proteção de dados sensíveis (PII). */
@Service
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final SecretKey secretKey;

    public EncryptionService(@Value("${api.encryption.secret}") String secret) {
        byte[] raw = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (raw.length < 16) {
            throw new IllegalArgumentException("Chave deve ter pelo menos 16 bytes");
        }
        this.secretKey = new SecretKeySpec(Arrays.copyOf(raw, 16), "AES");
    }

    /** Criptografa texto plano com AES-GCM: IV (12) + ciphertext (N) → Base64. */
    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            SecureRandom.getInstanceStrong().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] cipherText = cipher.doFinal(plainText.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            byte[] combined = Arrays.copyOf(iv, IV_LENGTH + cipherText.length);
            System.arraycopy(cipherText, 0, combined, IV_LENGTH, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao criptografar", e);
        }
    }

    /** Descriptografa Base64 → IV (12) + ciphertext → texto plano. */
    public String decrypt(String encryptedData) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedData);

            byte[] iv = Arrays.copyOf(combined, IV_LENGTH);
            byte[] cipherText = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao descriptografar", e);
        }
    }
}
