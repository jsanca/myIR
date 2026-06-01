package codex.ir.ingestion.crawler.internal.product;

import codex.ir.ingestion.crawler.product.ProductCard;
import codex.ir.ingestion.crawler.product.ProductImage;
import codex.ir.ingestion.crawler.product.ProductPrice;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Product card extraction result with canonical key and card type.
 *
 * <p>Intended for transitional/internal use. Not yet part of the public API.</p>
 */
public record ProductCardExtract(
        CanonicalProductKey canonicalKey,
        URI url,
        String name,
        Optional<ProductPrice> regularPrice,
        Optional<ProductPrice> salePrice,
        Optional<ProductImage> thumbnail,
        ExtractedCardType cardType,
        List<ExtractionWarning> warnings
) {
    public ProductCardExtract {
        Objects.requireNonNull(canonicalKey, "canonicalKey must not be null");
        Objects.requireNonNull(url, "url must not be null");
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        name = name.trim();
        Objects.requireNonNull(regularPrice, "regularPrice must not be null");
        Objects.requireNonNull(salePrice, "salePrice must not be null");
        Objects.requireNonNull(thumbnail, "thumbnail must not be null");
        Objects.requireNonNull(cardType, "cardType must not be null");
        Objects.requireNonNull(warnings, "warnings must not be null");
        warnings = List.copyOf(warnings);
    }

    public static ProductCardExtract fromProductCard(final ProductCard card) {
        Objects.requireNonNull(card, "card must not be null");
        return new ProductCardExtract(
                CanonicalProductKey.fromUrl(card.url()),
                card.url(),
                card.name(),
                card.regularPrice(),
                card.salePrice(),
                card.thumbnail(),
                ExtractedCardType.PRODUCT,
                List.of()
        );
    }

    public ProductCardExtract withWarnings(final List<ExtractionWarning> warnings) {
        Objects.requireNonNull(warnings, "warnings must not be null");
        return new ProductCardExtract(
                canonicalKey, url, name, regularPrice, salePrice, thumbnail, cardType, warnings);
    }
}
