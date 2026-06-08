package solutions.pdroti.lead.enrichment.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class RedisCacheService {

    private static final int CACHE_TTL_HOURS = 24;

    private final RedisTemplate<String, Object> redisTemplate;

    /** Armazena valor no cache com TTL de {@value #CACHE_TTL_HOURS} horas. */
    public void put(String key, Object value) {
        if (key == null) return;
        redisTemplate.opsForValue().set(key, value, CACHE_TTL_HOURS, TimeUnit.HOURS);
        log.debug("Cache put: {}", key);
    }

    /** Recupera valor do cache ou {@link Optional#empty()} se ausente/expirado. */
    public Optional<Object> get(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    /** Remove entrada do cache (usado em soft/hard delete). */
    public void evict(String key) {
        if (key == null) return;
        redisTemplate.delete(key);
        log.debug("Cache evict: {}", key);
    }
}