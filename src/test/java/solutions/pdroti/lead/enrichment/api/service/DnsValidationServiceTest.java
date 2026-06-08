package solutions.pdroti.lead.enrichment.api.service;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DnsValidationServiceTest {

    private final DnsValidationService dnsValidationService = new DnsValidationService();

    @Test
    void shouldReturnTrueForDomainWithMxRecords() {
        boolean result = dnsValidationService.hasMxRecord("gmail.com");
        assertTrue(result, "gmail.com deve possuir registros MX válidos");
    }

    @Test
    void shouldReturnTrueForOutlookDomain() {
        boolean result = dnsValidationService.hasMxRecord("outlook.com");
        assertTrue(result, "outlook.com deve possuir registros MX válidos");
    }

    @Test
    void shouldReturnFalseForNonExistentDomain() {
        boolean result = dnsValidationService.hasMxRecord("dominioteste123456789.com");
        assertFalse(result, "Domínio inexistente não deve possuir registros MX");
    }

    @Test
    void shouldReturnFalseForInvalidDomain() {
        boolean result = dnsValidationService.hasMxRecord("invalido");
        assertFalse(result, "Domínio inválido não deve possuir registros MX");
    }

    @Test
    void shouldReturnFalseForEmptyString() {
        boolean result = dnsValidationService.hasMxRecord("");
        assertFalse(result, "String vazia não deve possuir registros MX");
    }
}