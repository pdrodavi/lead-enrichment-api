package solutions.pdroti.lead.enrichment.api.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContentTrackerTest {

    private Cache<String, String> hashCache;
    private ContentTracker tracker;

    @BeforeEach
    void setUp() {
        hashCache = Caffeine.newBuilder().build();
        tracker = new ContentTracker(hashCache, "TestSource");
    }

    @Test
    void computeHash_deveGerarHashSha256() {
        String hash = tracker.computeHash("conteúdo de teste");
        assertNotNull(hash);
        assertFalse(hash.isBlank());
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void computeHash_deveSerDeterministico() {
        String hash1 = tracker.computeHash("mesmo conteúdo");
        String hash2 = tracker.computeHash("mesmo conteúdo");
        assertEquals(hash1, hash2);
    }

    @Test
    void computeHash_deveRetornarVazioParaNullOuBlank() {
        assertEquals("", tracker.computeHash(null));
        assertEquals("", tracker.computeHash(""));
        assertEquals("", tracker.computeHash("   "));
    }

    @Test
    void trackChange_primeiraVez_deveRetornarFalse() {
        boolean changed = tracker.trackChange("chave1", "abc123");
        assertFalse(changed);
    }

    @Test
    void trackChange_mesmoHash_deveRetornarFalse() {
        tracker.trackChange("chave1", "abc123");
        boolean changed = tracker.trackChange("chave1", "abc123");
        assertFalse(changed);
    }

    @Test
    void trackChange_hashDiferente_deveRetornarTrue() {
        tracker.trackChange("chave1", "abc123");
        boolean changed = tracker.trackChange("chave1", "def456");
        assertTrue(changed);
    }

    @Test
    void trackContentChange_deveFuncionarEmUmPasso() {
        boolean first = tracker.trackContentChange("url1", "conteúdo original");
        assertFalse(first);

        boolean second = tracker.trackContentChange("url1", "conteúdo original");
        assertFalse(second);

        boolean third = tracker.trackContentChange("url1", "conteúdo modificado");
        assertTrue(third);
    }

    @Test
    void chavesDiferentes_devemSerRastreadasIndependentemente() {
        tracker.trackChange("chaveA", "hashA");
        tracker.trackChange("chaveB", "hashB");

        boolean changedA = tracker.trackChange("chaveA", "hashA");
        boolean changedB = tracker.trackChange("chaveB", "hashB");

        assertFalse(changedA);
        assertFalse(changedB);
    }
}
