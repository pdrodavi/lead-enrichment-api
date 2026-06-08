package solutions.pdroti.lead.enrichment.api.util;

import lombok.experimental.UtilityClass;

/** Utilitário para operações com e-mail (mascaramento LGPD, extração de domínio). */
@UtilityClass
public class EmailUtils {

    private static final int MASK_VISIBLE_CHARS = 3;
    private static final String MASK = "***";

    /**
     * Ofusca e-mail para logging: exibe os primeiros 3 caracteres do local-part.
     * Ex: "pedro@pdroti.com" → "ped***@pdroti.com"
     *     "ab@cd.com"        → "a***@cd.com"
     */
    public static String mask(String email) {
        if (email == null || !email.contains("@")) return email;
        int at = email.indexOf("@");
        String local = email.substring(0, at);
        String domain = email.substring(at);
        int visible = Math.min(local.length(), MASK_VISIBLE_CHARS);
        return local.substring(0, visible) + MASK + domain;
    }
}
