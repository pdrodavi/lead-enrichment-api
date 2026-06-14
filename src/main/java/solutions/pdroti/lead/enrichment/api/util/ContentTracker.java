package solutions.pdroti.lead.enrichment.api.util;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utilitário para detectar mudanças em dados cacheados usando
 * fingerprint SHA-256.
 * <p>
 * Funciona em conjunto com um cache de dados com TTL curto:
 * quando o dado expira e é refetchado, o {@link #trackChange}
 * compara o hash do novo resultado com o hash anterior,
 * permitindo saber se o conteúdo mudou desde a última consulta.
 * <p>
 * Exemplo de uso com Caffeine:
 * <pre>{@code
 *   var tracker = new ContentTracker(hashCache, "OpenSERP");
 *   String newHash = tracker.computeHash(jsonData);
 *   boolean changed = tracker.trackChange(cacheKey, newHash);
 * }</pre>
 */
@Slf4j
public final class ContentTracker {

    private static final String HASH_ALGORITHM = "SHA-256";

    /** ThreadLocal cache do MessageDigest para evitar recriação. */
    private static final ThreadLocal<MessageDigest> DIGEST =
            ThreadLocal.withInitial(() -> {
                try {
                    return MessageDigest.getInstance(HASH_ALGORITHM);
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException("Algoritmo de hash não disponível: " + HASH_ALGORITHM, e);
                }
            });

    private final Cache<String, String> hashCache;
    private final String sourceName;

    /**
     * @param hashCache  cache Caffeine que armazena os hashes dos conteúdos
     * @param sourceName nome amigável para logs (ex: "OpenSERP", "RDAP")
     */
    public ContentTracker(Cache<String, String> hashCache, String sourceName) {
        this.hashCache = hashCache;
        this.sourceName = sourceName;
    }

    /**
     * Computa o hash SHA-256 de uma string.
     *
     * @param content conteúdo para hash
     * @return hash hexadecimal em lowercase
     */
    public String computeHash(String content) {
        if (content == null || content.isBlank()) return "";
        MessageDigest md = DIGEST.get();
        md.reset();
        byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(64);
        for (byte b : digest) {
            hex.append(String.format("%02x", b & 0xff));
        }
        return hex.toString();
    }

    /**
     * Verifica se o conteúdo mudou em relação ao hash armazenado,
     * atualiza o hash e loga o resultado.
     *
     * @param cacheKey chave única que identifica o conteúdo
     * @param newHash  hash do novo conteúdo (gerado por {@link #computeHash})
     * @return {@code true} se o conteúdo mudOU, {@code false} se está inalterado
     */
    public boolean trackChange(String cacheKey, String newHash) {
        String oldHash = hashCache.getIfPresent(cacheKey);

        if (oldHash == null) {
            // Primeira vez — sem referência anterior
            hashCache.put(cacheKey, newHash);
            return false;
        }

        if (newHash.equals(oldHash)) {
            log.debug("{}: conteúdo inalterado para '{}'", sourceName, cacheKey);
            return false;
        }

        log.info("{}: conteúdo mudou para '{}'", sourceName, cacheKey);
        hashCache.put(cacheKey, newHash);
        return true;
    }

    /**
     * Método conveniente que computa o hash e rastreia a mudança em um passo.
     *
     * @param cacheKey chave única que identifica o conteúdo
     * @param content  conteúdo bruto (string) a ser hashado e comparado
     * @return {@code true} se o conteúdo mudou, {@code false} se está inalterado
     */
    public boolean trackContentChange(String cacheKey, String content) {
        String newHash = computeHash(content);
        return trackChange(cacheKey, newHash);
    }
}
