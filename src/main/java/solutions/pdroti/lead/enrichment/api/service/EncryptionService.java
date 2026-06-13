package solutions.pdroti.lead.enrichment.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Serviço de criptografia AES-GCM para proteção de dados sensíveis (PII).
 * <p>
 * Utiliza AES-128-GCM com IV aleatório de 12 bytes para cada operação.
 * O formato do dado criptografado é: {@code Base64(IV(12) + ciphertext(N))}.
 * Atende aos requisitos de proteção de dados pessoais (LGPD).
 */
@Service
public class EncryptionService {

    /** Algoritmo de criptografia: AES no modo GCM sem padding. */
    private static final String ALGORITHM = "AES/GCM/NoPadding";

    /** Tamanho da tag de autenticação GCM em bits. */
    private static final int GCM_TAG_LENGTH = 128;

    /** Tamanho do vetor de inicialização (IV) em bytes. */
    private static final int IV_LENGTH = 12;

    /** Tamanho da chave AES em bytes (128 bits). */
    private static final int KEY_SIZE_BYTES = 16;

    private final SecretKey secretKey;

    /**
     * Construtor que deriva a chave AES-128 a partir de um secret configurável.
     * O secret deve ter pelo menos 16 bytes de comprimento.
     *
     * @param secret string secreta para derivação da chave
     */
    public EncryptionService(@Value("${api.encryption.secret}") String secret) {
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        if (raw.length < KEY_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "Chave de criptografia deve ter pelo menos " + KEY_SIZE_BYTES + " bytes");
        }
        this.secretKey = new SecretKeySpec(Arrays.copyOf(raw, KEY_SIZE_BYTES), "AES");
    }

    /**
     * Criptografa um texto plano com AES-128-GCM.
     *
     * @param plainText texto a ser criptografado
     * @return Base64(IV(12) + ciphertext)
     */
    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = Arrays.copyOf(iv, IV_LENGTH + cipherText.length);
            System.arraycopy(cipherText, 0, combined, IV_LENGTH, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao criptografar", e);
        }
    }

    /**
     * Descriptografa um texto previamente criptografado com {@link #encrypt(String)}.
     *
     * @param encryptedData Base64(IV + ciphertext) gerado por encrypt()
     * @return texto plano original
     */
    public String decrypt(String encryptedData) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedData);

            byte[] iv = Arrays.copyOf(combined, IV_LENGTH);
            byte[] cipherText = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao descriptografar", e);
        }
    }
}
