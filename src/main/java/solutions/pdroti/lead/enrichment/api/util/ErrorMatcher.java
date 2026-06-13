package solutions.pdroti.lead.enrichment.api.util;

/**
 * Functional interface para classificar erros de scraping por
 * tipo de exceção ou mensagem.
 *
 * @see solutions.pdroti.lead.enrichment.api.enums.ScrapeError
 */
@FunctionalInterface
public interface ErrorMatcher {
    boolean matches(Exception e, String message);
}
