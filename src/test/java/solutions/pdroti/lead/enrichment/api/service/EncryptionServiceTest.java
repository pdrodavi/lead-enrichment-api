package solutions.pdroti.lead.enrichment.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionServiceTest {

    private static final String VALID_SECRET = "0123456789abcdef"; // 16 bytes
    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService(VALID_SECRET);
    }

    @Test
    void encrypt_decrypt_deveCriptografarEDescriptografar() {
        String original = "joao.silva@exemplo.com";
        String encrypted = encryptionService.encrypt(original);
        String decrypted = encryptionService.decrypt(encrypted);

        assertEquals(original, decrypted);
    }

    @Test
    void encrypt_deveRetornarBase64Valido() {
        String encrypted = encryptionService.encrypt("teste@email.com");
        assertNotNull(encrypted);
        assertTrue(encrypted.matches("[A-Za-z0-9+/=]+"));
    }

    @Test
    void decrypt_valoresDiferentesDevemProduzirResultadosDiferentes() {
        String enc1 = encryptionService.encrypt("email1@teste.com");
        String enc2 = encryptionService.encrypt("email2@teste.com");
        assertNotEquals(enc1, enc2);
    }

    @Test
    void encrypt_mesmoTextoDeveProduzirCiphertextDiferente() {
        // AES-GCM usa IV aleatório, então mesmo texto produz ciphertext diferente
        String enc1 = encryptionService.encrypt("mesmo@email.com");
        String enc2 = encryptionService.encrypt("mesmo@email.com");
        assertNotEquals(enc1, enc2);
    }

    @Test
    void decrypt_deveLancarExcecaoParaDadoInvalido() {
        assertThrows(RuntimeException.class, () ->
            encryptionService.decrypt("dado-invalido-nao-base64"));
    }

    @Test
    void constructor_secretCurtoDemais_deveLancarExcecao() {
        assertThrows(IllegalArgumentException.class, () ->
            new EncryptionService("curto"));
    }

    @Test
    void validateConfig_comSecretValido_devePassar() {
        // validateConfig() é chamado pelo Spring via @PostConstruct
        // Como package-private, podemos chamar diretamente no teste
        assertDoesNotThrow(() -> encryptionService.validateConfig());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "joao.silva@gmail.com",
        "maria@empresa.com.br",
        "contato@sub.dominio.com",
        "user+tag@protonmail.com",
        "um@dois.tres.quatro.cinco.com"
    })
    void encryptDecrypt_variosEmailsDevemFuncionar(String email) {
        String encrypted = encryptionService.encrypt(email);
        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(email, decrypted);
    }

    @Test
    void decrypt_comTextosLongos() {
        String longText = "a".repeat(1000) + "@teste.com";
        String encrypted = encryptionService.encrypt(longText);
        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(longText, decrypted);
    }
}
