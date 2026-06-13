package solutions.pdroti.lead.enrichment.api.util;

import lombok.experimental.UtilityClass;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utilitário para operações com e-mail.
 * <p>
 * Atende aos requisitos da LGPD para mascaramento de dados
 * pessoais em logs e respostas da API.
 */
@UtilityClass
public class EmailUtils {

    /** Número de caracteres visíveis antes da máscara. */
    private static final int MASK_VISIBLE_CHARS = 3;

    /** String de substituição para caracteres ocultos. */
    private static final String MASK = "***";

    /** Algoritmo de hash para lookup de e-mail. */
    private static final String HASH_ALGORITHM = "SHA-256";

    /**
     * Cache thread-safe do MessageDigest para evitar recriação
     * a cada chamada de {@link #hash(String)}.
     */
    private static final ThreadLocal<MessageDigest> DIGEST_CACHE = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algoritmo de hash não disponível: " + HASH_ALGORITHM, e);
        }
    });

    /**
     * Ofusca um e-mail para exibição segura (LGPD).
     * <p>
     * Mantém visíveis apenas os primeiros 3 caracteres do local-part
     * (parte antes do @) e oculta o restante com {@code ***}.
     * <p>
     * Exemplos:
     * <ul>
     *   <li>{@code "pedro@pdroti.com"} → {@code "ped***@pdroti.com"}</li>
     *   <li>{@code "ab@cd.com"} → {@code "ab***@cd.com"}</li>
     *   <li>{@code null} → {@code null}</li>
     *   <li>{@code ""} → {@code null}</li>
     * </ul>
     *
     * @param email e-mail a ser mascarado
     * @return e-mail mascarado, ou null se inválido/vazio
     */
    public static String mask(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) return null;
        int at = email.indexOf("@");
        String local = email.substring(0, at);
        String domain = email.substring(at);
        int visible = Math.min(local.length(), MASK_VISIBLE_CHARS);
        return local.substring(0, visible) + MASK + domain;
    }

    /**
     * Computa o hash SHA-256 do e-mail (normalizado para lowercase)
     * para consulta no banco de dados.
     * <p>
     * Usa {@link ThreadLocal} para cache do {@link MessageDigest},
     * evitando recriação a cada chamada sem sacrificar thread-safety.
     * <p>
     * Útil porque o e-mail em si é criptografado (AES-GCM) e não pode
     * ser usado em consultas JPA. O hash permite lookup sem expor o dado.
     *
     * @param email e-mail para hash (case-insensitive)
     * @return hash hexadecimal SHA-256, ou null se o e-mail for inválido
     */
    public static String hash(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) return null;
        try {
            MessageDigest md = DIGEST_CACHE.get();
            md.reset();
            byte[] digest = md.digest(email.strip().toLowerCase().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } finally {
            DIGEST_CACHE.remove();
        }
    }
}
