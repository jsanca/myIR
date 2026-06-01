package codex.ir.ingestion.crawler.internal.product;

import codex.ir.ingestion.crawler.product.ProductImage;
import codex.ir.ingestion.crawler.product.ProductPrice;

import java.util.List;

/**
 * Internal data record for JSON-LD extracted product fields.
 */
public record JsonLdProductData(
        String name,
        String sku,
        String description,
        String brand,
        ProductPrice regularPrice,
        String availability,
        String url,
        List<ProductImage> images
) {
    static final JsonLdProductData EMPTY = new JsonLdProductData(
            null, null, null, null, null, null, null, List.of());

    boolean hasAnyField() {
        return name != null || sku != null || description != null || brand != null
                || regularPrice != null || !images.isEmpty();
    }
}
