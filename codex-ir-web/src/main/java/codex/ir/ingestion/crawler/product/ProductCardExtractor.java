package codex.ir.ingestion.crawler.product;

import codex.ir.ingestion.WebPage;

import java.util.List;

/**
 * Extracts {@link ProductCard} summaries from listing/category pages.
 */
@FunctionalInterface
public interface ProductCardExtractor {

    /**
     * Extracts product cards from the given listing page.
     *
     * @param page the fetched listing/category page
     * @return list of extracted product cards, or empty if none found
     */
    List<ProductCard> extract(WebPage page);
}
