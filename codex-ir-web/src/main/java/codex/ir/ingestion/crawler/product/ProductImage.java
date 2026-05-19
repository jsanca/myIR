package codex.ir.ingestion.crawler.product;

import java.net.URI;
import java.util.Objects;

/**
 * Product image with URL, alt text, and display order.
 */
public record ProductImage(URI url, String altText, int displayOrder) {

    public ProductImage {
        Objects.requireNonNull(url, "url must not be null");
        if (displayOrder < 0) {
            throw new IllegalArgumentException("displayOrder must not be negative");
        }
        altText = altText == null ? "" : altText.trim();
    }
}
