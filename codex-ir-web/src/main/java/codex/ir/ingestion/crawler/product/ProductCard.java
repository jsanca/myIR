package codex.ir.ingestion.crawler.product;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * Lightweight product summary extracted from a listing/category page.
 */
public record ProductCard(
        URI url,
        String name,
        Optional<ProductPrice> regularPrice,
        Optional<ProductPrice> salePrice,
        Optional<ProductImage> thumbnail
) {
    public ProductCard {
        Objects.requireNonNull(url, "url must not be null");
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        name = name.trim();
        regularPrice = Objects.requireNonNull(regularPrice, "regularPrice must not be null");
        salePrice = Objects.requireNonNull(salePrice, "salePrice must not be null");
        thumbnail = Objects.requireNonNull(thumbnail, "thumbnail must not be null");
    }
}
