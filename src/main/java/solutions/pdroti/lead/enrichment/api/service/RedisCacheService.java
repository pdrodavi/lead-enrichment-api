package solutions.pdroti.lead.enrichment.api.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class RedisCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @SuppressWarnings("null")
    public void put(String key, Object value) {
        redisTemplate.opsForValue().set(key, value, 24, TimeUnit.HOURS);
    }

    @SuppressWarnings("null")
    public Optional<Object> get(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }
}