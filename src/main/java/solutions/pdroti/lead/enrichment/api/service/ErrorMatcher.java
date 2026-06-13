package solutions.pdroti.lead.enrichment.api.service;

/**
 * Functional interface para classificar erros de scraping por
 * tipo de exceção ou mensagem.
 *
 * @see ScrapeError
 */
@FunctionalInterface
public interface ErrorMatcher {
    boolean matches(Exception e, String message);
}
