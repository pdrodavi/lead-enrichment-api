package solutions.pdroti.lead.enrichment.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisCacheServiceTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private RedisCacheService redisCacheService;

    @BeforeEach
    void setUp() {
        redisCacheService = new RedisCacheService(redis);
    }

    @Test
    void constructor_comRedisDisponivel_deveLogar() {
        assertNotNull(redisCacheService);
    }

    @Test
    void constructor_comRedisNull_deveOperarSemRedis() {
        RedisCacheService service = new RedisCacheService(null);
        assertNull(service.get("chave"));
    }

    @Test
    void get_comChaveExistente_deveRetornarValor() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("lead-enrich:minha-chave")).thenReturn("valor-cache");

        String result = redisCacheService.get("minha-chave");

        assertEquals("valor-cache", result);
        verify(valueOps).get("lead-enrich:minha-chave");
    }

    @Test
    void get_comChaveInexistente_deveRetornarNull() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("lead-enrich:chave-ausente")).thenReturn(null);

        String result = redisCacheService.get("chave-ausente");

        assertNull(result);
    }

    @Test
    void get_comRedisIndisponivel_deveRetornarNull() {
        RedisCacheService service = new RedisCacheService(null);
        assertNull(service.get("chave"));
    }

    @Test
    void get_quandoRedisLancaExcecao_deveRetornarNull() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("Redis down"));

        String result = redisCacheService.get("chave");

        assertNull(result);
    }

    @Test
    void evict_comChaveExistente_deveDeletar() {
        redisCacheService.evict("minha-chave");

        verify(redis).delete("lead-enrich:minha-chave");
    }

    @Test
    void evict_quandoRedisLancaExcecao_deveIgnorar() {
        doThrow(new RuntimeException("Redis down")).when(redis).delete(anyString());

        assertDoesNotThrow(() -> redisCacheService.evict("chave"));
    }

    @Test
    void setAsync_deveArmazenarAssincronamente() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOps);

        redisCacheService.setAsync("minha-chave", 1800, "valor");

        // Aguarda a execução assíncrona
        Thread.sleep(500);
        verify(valueOps).set(eq("lead-enrich:minha-chave"), eq("valor"), eq(Duration.ofSeconds(1800)));
    }

    @Test
    void setAsync_comRedisNull_deveIgnorar() {
        RedisCacheService service = new RedisCacheService(null);

        assertDoesNotThrow(() -> service.setAsync("chave", 1800, "valor"));
    }
}
