package codex.ir.ingestion.crawler.product;

import codex.ir.ingestion.crawler.classifier.PageClassification;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of running product discovery against a single fetched page.
 *
 * @param pageUri the URI of the inspected page
 * @param pageClassification the full page classification (type, URL signals, HTML signals)
 * @param productDetail the extracted product detail for PRODUCT pages, otherwise empty
 * @param productCards the extracted product cards for CATEGORY and HOMEPAGE pages, otherwise empty
 */
public record ProductDiscoveryResult(
        URI pageUri,
        PageClassification pageClassification,
        Optional<ProductDetail> productDetail,
        List<ProductCard> productCards
) {
    public ProductDiscoveryResult {
        Objects.requireNonNull(pageUri, "pageUri must not be null");
        Objects.requireNonNull(pageClassification, "pageClassification must not be null");
        Objects.requireNonNull(productDetail, "productDetail must not be null");
        Objects.requireNonNull(productCards, "productCards must not be null");
        productCards = List.copyOf(productCards);
    }
}
