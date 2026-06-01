package codex.ir.ingestion.crawler.product;

import java.util.List;
import java.util.Objects;

/**
 * Aggregated result of running product discovery over a collection of pages.
 *
 * @param pageResults per-page discovery results, in input order
 * @param productDetails all product details extracted from PRODUCT pages
 * @param productCards all product cards extracted from CATEGORY and HOMEPAGE pages
 */
public record ProductDiscoveryReport(
        List<ProductDiscoveryResult> pageResults,
        List<ProductDetail> productDetails,
        List<ProductCard> productCards
) {
    public ProductDiscoveryReport {
        Objects.requireNonNull(pageResults, "pageResults must not be null");
        Objects.requireNonNull(productDetails, "productDetails must not be null");
        Objects.requireNonNull(productCards, "productCards must not be null");
        pageResults = List.copyOf(pageResults);
        productDetails = List.copyOf(productDetails);
        productCards = List.copyOf(productCards);
    }

    public static ProductDiscoveryReport empty() {
        return new ProductDiscoveryReport(List.of(), List.of(), List.of());
    }
}
