package codex.ir.ingestion.crawler.product;

import codex.ir.ingestion.WebPage;

import java.util.Optional;

/**
 * Extracts a {@link ProductDetail} from a fetched {@link WebPage}.
 */
@FunctionalInterface
public interface ProductDetailExtractor {

    /**
     * Attempts to extract product detail from the given page.
     *
     * @param page the fetched web page with HTML content
     * @return the extracted product detail, or empty if the page
     *         does not appear to be a product page
     */
    Optional<ProductDetail> extract(WebPage page);
}
