package solutions.pdroti.lead.enrichment.api.util;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utilitário com parsers e validadores usados no pipeline de enriquecimento.
 * <p>
 * Agrupa funções extraídas do {@code LeadService} para manter
 * a responsabilidade única e facilitar testes unitários.
 */
@Slf4j
public final class DataParser {

    private DataParser() { throw new UnsupportedOperationException("Utility class"); }

    /** Regex para extração de e-mails de textos (snippets, títulos). */
    public static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    /**
     * Extrai endereços de e-mail do snippet e título de um resultado de busca.
     * <p>
     * Usa {@link #EMAIL_PATTERN} para encontrar e-mails no formato padrão.
     * Filtra e-mails falsos como {@code *@example.com}.
     *
     * @param emails  lista onde os e-mails encontrados serão adicionados
     * @param snippet trecho de contexto do resultado
     * @param title   título do resultado
     */
    public static void extractEmails(List<String> emails, String snippet, String title) {
        var matcher = EMAIL_PATTERN.matcher(snippet + " " + title);
        while (matcher.find()) {
            String foundEmail = matcher.group().toLowerCase();
            if (!foundEmail.contains("example.com")) {
                emails.add(foundEmail);
            }
        }
    }

    /**
     * Verifica se o nome completo aparece no texto como uma palavra/frase
     * distinta, evitando matches parciais dentro de outras palavras.
     * <p>
     * Exemplos:
     * <ul>
     *   <li>"João Silva" → match em "sobre João Silva" ✓</li>
     *   <li>"João Silva" → NÃO match em "João Silveira" ✗</li>
     *   <li>"João Silva" → NÃO match em "João Silvares" ✗</li>
     * </ul>
     *
     * @param text texto onde buscar (snippet, título, HTML)
     * @param name nome completo a ser encontrado
     * @return true se o nome completo foi encontrado como termo distinto
     */
    public static boolean nameMatchesExactly(String text, String name) {
        if (text == null || name == null) return false;
        String lowerText = text.toLowerCase();
        String lowerName = name.toLowerCase();

        int idx = lowerText.indexOf(lowerName);
        if (idx < 0) return false;

        // Verifica se não há caractere alfanumérico antes do nome
        if (idx > 0) {
            char before = lowerText.charAt(idx - 1);
            if (Character.isLetterOrDigit(before)) return false;
        }

        // Verifica se não há caractere alfanumérico depois do nome
        int endIdx = idx + lowerName.length();
        if (endIdx < lowerText.length()) {
            char after = lowerText.charAt(endIdx);
            if (Character.isLetterOrDigit(after)) return false;
        }

        return true;
    }

    /**
     * Extrai o domínio de um e-mail (tudo após o caractere @).
     *
     * @param email endereço de e-mail completo
     * @return domínio extraído (ex: "exemplo.com")
     * @throws IllegalArgumentException se o e-mail for nulo ou não contiver @
     */
    public static String extractDomainFromEmail(String email) {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Email inválido: " + email);
        }
        return email.substring(email.indexOf("@") + 1);
    }

    /**
     * Converte uma string de data ISO-8601 (ex: "2026-06-05T15:19:47.754Z")
     * em {@link LocalDateTime}.
     * <p>
     * Trata variações comuns:
     * <ul>
     *   <li>Suprime o sufixo "Z" (indicador de UTC)</li>
     *   <li>Trunca frações de segundos com mais de 9 dígitos</li>
     * </ul>
     *
     * @param dateStr string de data no formato ISO-8601
     * @return LocalDateTime convertido, ou null se a string for inválida
     */
    public static LocalDateTime parseIsoDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            String normalized = dateStr.strip();
            // Remove sufixo "Z" (indicador UTC)
            if (normalized.endsWith("Z")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            // Trunca fração de segundos com mais de 9 dígitos (nanossegundos)
            int dotIndex = normalized.indexOf('.');
            if (dotIndex > 0) {
                String fraction = normalized.substring(dotIndex + 1);
                if (fraction.length() > 9) {
                    normalized = normalized.substring(0, dotIndex + 10);
                }
            }
            return LocalDateTime.parse(normalized);
        } catch (Exception e) {
            log.debug("Não foi possível converter data: {}", dateStr);
            return null;
        }
    }

    /**
     * Converte uma lista imutável (ex: List.of()) em ArrayList mutável
     * para que o Hibernate consiga persistir corretamente.
     *
     * @param list lista imutável ou null
     * @return ArrayList mutável (nunca null)
     */
    public static <T> List<T> toMutable(List<T> list) {
        return list != null ? new ArrayList<>(list) : new ArrayList<>();
    }
}
