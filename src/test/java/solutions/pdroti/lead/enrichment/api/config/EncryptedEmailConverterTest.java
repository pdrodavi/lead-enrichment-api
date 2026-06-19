package solutions.pdroti.lead.enrichment.api.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import solutions.pdroti.lead.enrichment.api.service.EncryptionService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EncryptedEmailConverterTest {

    @Mock
    private EncryptionService encryptionService;

    private EncryptedEmailConverter converter;

    @BeforeEach
    void setUp() {
        converter = new EncryptedEmailConverter(encryptionService);
    }

    @Test
    void convertToDatabaseColumn_comEmailValido_deveCriptografar() {
        when(encryptionService.encrypt("joao@exemplo.com")).thenReturn("base64encrypteddata");

        String result = converter.convertToDatabaseColumn("joao@exemplo.com");

        assertEquals("ENC(base64encrypteddata)", result);
        verify(encryptionService).encrypt("joao@exemplo.com");
    }

    @Test
    void convertToDatabaseColumn_comNull_deveRetornarNull() {
        assertNull(converter.convertToDatabaseColumn(null));
        verify(encryptionService, never()).encrypt(anyString());
    }

    @Test
    void convertToDatabaseColumn_erroCriptografia_deveLancarExcecao() {
        when(encryptionService.encrypt(anyString())).thenThrow(new RuntimeException("Falha"));

        assertThrows(RuntimeException.class, () ->
            converter.convertToDatabaseColumn("joao@exemplo.com"));
    }

    @Test
    void convertToEntityAttribute_comFormatoEnc_deveDescriptografar() {
        when(encryptionService.decrypt("base64data")).thenReturn("joao@exemplo.com");

        String result = converter.convertToEntityAttribute("ENC(base64data)");

        assertEquals("joao@exemplo.com", result);
        verify(encryptionService).decrypt("base64data");
    }

    @Test
    void convertToEntityAttribute_comNull_deveRetornarNull() {
        assertNull(converter.convertToEntityAttribute(null));
        verify(encryptionService, never()).decrypt(anyString());
    }

    @Test
    void convertToEntityAttribute_semPrefixoEnc_deveRetornarTextoOriginal() {
        String result = converter.convertToEntityAttribute("texto-plano-no-banco");

        assertEquals("texto-plano-no-banco", result);
        verify(encryptionService, never()).decrypt(anyString());
    }
}
