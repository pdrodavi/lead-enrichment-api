package solutions.pdroti.lead.enrichment.api.util;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

    /**
     * Conjunto de provedores de e-mail pessoais/conhecidos onde o
     * enriquecimento de domínio (DNS, RDAP, TechScraper) não agrega valor.
     * <p>
     * Quando o lead usa um destes domínios, o pipeline pula o
     * {@code DomainEnricherService} e executa apenas o {@code OpenSerpEnricherService}
     * (busca pelo nome no Google).
     */
    public static final Set<String> COMMON_EMAIL_PROVIDERS = Set.of(
            // Brasil / Portugal
            "bol.com.br", "uol.com.br", "terra.com.br", "ig.com.br", "globo.com",
            "r7.com", "zipmail.com.br", "click21.com.br", "pop.com.br",
            "sapo.pt", "clix.pt", "mail.pt",
            // Gmail / Google
            "gmail.com", "googlemail.com", "google.com",
            // Microsoft
            "outlook.com", "outlook.com.br", "hotmail.com", "hotmail.com.br",
            "live.com", "live.com.br", "msn.com",
            // Yahoo
            "yahoo.com", "yahoo.com.br", "ymail.com", "rocketmail.com",
            // Apple / iCloud
            "icloud.com", "me.com", "mac.com",
            // Proton
            "protonmail.com", "proton.me", "pm.me",
            // Outros internacionais
            "aol.com", "mail.com", "inbox.com", "gmx.com", "gmx.net",
            "yandex.com", "yandex.ru", "mail.ru", "bk.ru", "list.ru",
            "zoho.com", "fastmail.com", "fastmail.fm", "tutanota.com",
            "tutamail.com", "keemail.me", "disroot.org", "posteo.net",
            "runbox.com", "sohu.com", "126.com", "163.com", "qq.com",
            "rediffmail.com", "libero.it", "t-online.de", "web.de",
            "freenet.de", "orange.fr", "free.fr", "laposte.net",
            "wanadoo.fr", "skynet.be", "telenet.be"
    );

    /** Regex para extração de e-mails de textos (snippets, títulos). */
    public static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    /** Regex para extração de telefones brasileiros e internacionais. */
    public static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?:(?:\\+\\d{1,3}\\s?)?(?:\\(\\d{2,3}\\)\\s?)?\\d{4,5}-?\\d{4})" +
            "|(?:(?:\\+\\d{1,3}\\s?)?\\d{2,3}\\s?\\d{4,5}-?\\d{4})");

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
            if (!foundEmail.contains("example.com") && !foundEmail.contains("@.*@")) {
                emails.add(foundEmail);
            }
        }
    }

    /**
     * Extrai números de telefone do snippet de texto.
     *
     * @param phones  lista onde os telefones encontrados serão adicionados
     * @param snippet trecho de contexto do resultado
     */
    public static void extractPhones(List<String> phones, String snippet) {
        if (snippet == null || snippet.isBlank()) return;
        var matcher = PHONE_PATTERN.matcher(snippet);
        while (matcher.find()) {
            String phone = matcher.group().strip();
            // Filtra falsos positivos (números muito curtos)
            String digits = phone.replaceAll("\\D", "");
            if (digits.length() >= 10 && digits.length() <= 15) {
                if (!phones.contains(phone)) {
                    phones.add(phone);
                }
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
     * Verifica se o domínio pertence a um provedor de e-mail pessoal/conhecido.
     * <p>
     * Domínios pessoais não têm valor para enriquecimento de domínio
     * (DNS, RDAP, TechScraper), pois as informações seriam do provedor,
     * não do lead.
     *
     * @param domain domínio a verificar (ex: "gmail.com", "empresa.com.br")
     * @return {@code true} se for um domínio de provedor pessoal conhecido
     */
    public static boolean isPersonalEmailDomain(String domain) {
        if (domain == null || domain.isBlank()) return false;
        String lower = domain.toLowerCase().strip();
        // Verifica exato e também subdomínios (ex: "mail.google.com" não é pessoal)
        return COMMON_EMAIL_PROVIDERS.contains(lower);
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
