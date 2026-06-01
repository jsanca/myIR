package codex.ir.ingestion.crawler.internal.product;

import codex.ir.ingestion.crawler.product.ProductDetail;
import codex.ir.ingestion.crawler.product.ProductImage;
import codex.ir.ingestion.crawler.product.ProductPrice;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Full product detail extraction result with canonical key.
 *
 * <p>Intended for transitional/internal use. Not yet part of the public API.</p>
 */
public record ProductDetailExtract(
        CanonicalProductKey canonicalKey,
        URI url,
        String name,
        Optional<String> sku,
        Optional<String> brand,
        Optional<ProductPrice> regularPrice,
        Optional<ProductPrice> salePrice,
        Optional<String> availability,
        Optional<String> shortDescription,
        List<ProductImage> images,
        List<ExtractionWarning> warnings
) {
    public ProductDetailExtract {
        Objects.requireNonNull(canonicalKey, "canonicalKey must not be null");
        Objects.requireNonNull(url, "url must not be null");
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        name = name.trim();
        Objects.requireNonNull(sku, "sku must not be null");
        Objects.requireNonNull(brand, "brand must not be null");
        Objects.requireNonNull(regularPrice, "regularPrice must not be null");
        Objects.requireNonNull(salePrice, "salePrice must not be null");
        Objects.requireNonNull(availability, "availability must not be null");
        Objects.requireNonNull(shortDescription, "shortDescription must not be null");
        Objects.requireNonNull(images, "images must not be null");
        Objects.requireNonNull(warnings, "warnings must not be null");
        images = List.copyOf(images);
        warnings = List.copyOf(warnings);
    }

    public static ProductDetailExtract fromProductDetail(final ProductDetail detail) {
        Objects.requireNonNull(detail, "detail must not be null");
        return new ProductDetailExtract(
                CanonicalProductKey.fromUrl(detail.url()),
                detail.url(),
                detail.name(),
                detail.sku(),
                detail.brand(),
                detail.regularPrice(),
                detail.salePrice(),
                detail.availability(),
                detail.shortDescription(),
                detail.images(),
                List.of()
        );
    }

    public ProductDetailExtract withWarnings(final List<ExtractionWarning> warnings) {
        Objects.requireNonNull(warnings, "warnings must not be null");
        return new ProductDetailExtract(
                canonicalKey, url, name, sku, brand, regularPrice, salePrice,
                availability, shortDescription, images, warnings);
    }
}
