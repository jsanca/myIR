package codex.ir.ingestion.crawler.product;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Extracted product detail from a WooCommerce product page.
 */
public record ProductDetail(
        URI url,
        String name,
        Optional<String> sku,
        Optional<ProductPrice> regularPrice,
        Optional<ProductPrice> salePrice,
        Optional<String> shortDescription,
        List<ProductImage> images
) {
}
