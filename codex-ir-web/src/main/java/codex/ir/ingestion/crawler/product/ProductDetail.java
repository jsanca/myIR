package codex.ir.ingestion.crawler.product;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Extracted product detail from a product page.
 */
public record ProductDetail(
        URI url,
        String name,
        Optional<String> sku,
        Optional<ProductPrice> regularPrice,
        Optional<ProductPrice> salePrice,
        Optional<String> shortDescription,
        List<ProductImage> images,
        Optional<String> brand,
        Optional<String> availability
) {
    public ProductDetail {
        Objects.requireNonNull(url, "url must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(sku, "sku must not be null");
        Objects.requireNonNull(regularPrice, "regularPrice must not be null");
        Objects.requireNonNull(salePrice, "salePrice must not be null");
        Objects.requireNonNull(shortDescription, "shortDescription must not be null");
        Objects.requireNonNull(images, "images must not be null");
        Objects.requireNonNull(brand, "brand must not be null");
        Objects.requireNonNull(availability, "availability must not be null");
        images = List.copyOf(images);
    }
}
