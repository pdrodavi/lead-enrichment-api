package solutions.pdroti.lead.enrichment.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Serviço de cache em dois níveis (L1 Caffeine + L2 Redis).
 * <p>
 * Cache distribuído via Redis compartilhado entre instâncias.
 * Se Redis estiver indisponível ou {@code spring.data.redis.host} não
 * configurado, opera apenas com cache local Caffeine (fallback transparente).
 * <p>
 * Estratégia:
 * <ol>
 *   <li>Sempre verifica Caffeine primeiro (L1 — rápido, em memória)</li>
 *   <li>Redis como L2 — lê de forma síncrona com timeout de 5s</li>
 *   <li>Escrita no Redis é fire-and-forget em virtual thread (nunca bloqueia a resposta)</li>
 *   <li>Prefixo de chave: {@code lead-enrich:}</li>
 * </ol>
 */
@Slf4j
@Service
public class RedisCacheService {

    private static final String KEY_PREFIX = "lead-enrich:";

    private final StringRedisTemplate redis;
    private final boolean redisAvailable;

    public RedisCacheService(@Autowired(required = false) StringRedisTemplate redis) {
        this.redis = redis;
        this.redisAvailable = redis != null;
        if (this.redisAvailable) {
            log.info("Redis cache L2 disponível");
        } else {
            log.info("Redis cache L2 desabilitado — apenas cache local Caffeine");
        }
    }

    /**
     * Tenta ler do Redis (L2). Se falhar ou não encontrado, retorna null.
     * <b>Nunca bloqueia por mais de 5s</b> (timeout configurado no application.yml).
     *
     * @param key chave única do cache (sem prefixo)
     * @return valor string do cache, ou null se não encontrado / indisponível
     */
    public String get(String key) {
        return execute(key, "get", () -> {
            String cached = redis.opsForValue().get(buildKey(key));
            if (cached != null) log.debug("Redis L2 hit: {}", key);
            return cached;
        });
    }

    /**
     * Armazena no Redis de forma assíncrona (fire-and-forget).
     * Nunca bloqueia a thread principal — executa em virtual thread.
     *
     * @param key   chave única
     * @param ttl   tempo de vida em segundos
     * @param value valor string a ser armazenado
     */
    public void setAsync(String key, long ttl, String value) {
        if (!redisAvailable || value == null) return;
        CompletableFuture.runAsync(() ->
            execute(key, "set", () -> {
                redis.opsForValue().set(buildKey(key), value, Duration.ofSeconds(ttl));
                return null;
            })
        );
    }

    /**
     * Remove uma chave do cache Redis.
     */
    public void evict(String key) {
        execute(key, "evict", () -> {
            redis.delete(buildKey(key));
            return null;
        });
    }

    /**
     * Executa uma operação Redis com fallback silencioso em caso de erro.
     * Se Redis não estiver disponível, retorna null sem executar.
     */
    private <T> T execute(String key, String operation, java.util.function.Supplier<T> action) {
        if (!redisAvailable) return null;
        try {
            return action.get();
        } catch (Exception e) {
            log.debug("Redis L2 {} falhou: {} - {}", operation, key, e.getMessage());
            return null;
        }
    }

    private String buildKey(String key) {
        return KEY_PREFIX + key;
    }
}
