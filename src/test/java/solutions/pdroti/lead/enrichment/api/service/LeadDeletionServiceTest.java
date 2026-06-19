package solutions.pdroti.lead.enrichment.api.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import solutions.pdroti.lead.enrichment.api.repository.LeadRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadDeletionServiceTest {

    @Mock
    private LeadRepository leadRepository;

    @InjectMocks
    private LeadDeletionService leadDeletionService;

    // ========== hardDelete() ==========

    @Test
    void hardDelete_comIdValido_deveDeletarERetornarTrue() {
        boolean result = leadDeletionService.hardDelete("42");

        assertTrue(result);
        verify(leadRepository).deleteById(42L);
    }

    @Test
    void hardDelete_leadNaoEncontrado_deveRetornarFalse() {
        doThrow(new EmptyResultDataAccessException("Not found", 1))
            .when(leadRepository).deleteById(99L);

        boolean result = leadDeletionService.hardDelete("99");

        assertFalse(result);
        verify(leadRepository).deleteById(99L);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "abc", "12.5", "id-invalido", "0xFF"})
    void hardDelete_comIdInvalido_deveRetornarFalse(String invalidId) {
        boolean result = leadDeletionService.hardDelete(invalidId);
        assertFalse(result);
        verify(leadRepository, never()).deleteById(any());
    }

    // ========== parseNumericId() ==========

    @Test
    void parseNumericId_deveConverterIdValido() {
        var result = LeadDeletionService.parseNumericId("123");
        assertTrue(result.isPresent());
        assertEquals(123L, result.get());
    }

    @Test
    void parseNumericId_zeroDeveSerValido() {
        var result = LeadDeletionService.parseNumericId("0");
        assertTrue(result.isPresent());
        assertEquals(0L, result.get());
    }

    @Test
    void parseNumericId_numeroNegativoDeveSerValido() {
        var result = LeadDeletionService.parseNumericId("-5");
        assertTrue(result.isPresent());
        assertEquals(-5L, result.get());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "12.5", "  ", "0xFF", "1e10"})
    void parseNumericId_deveRetornarEmptyParaInvalidos(String invalidId) {
        var result = LeadDeletionService.parseNumericId(invalidId);
        assertFalse(result.isPresent());
    }
}
