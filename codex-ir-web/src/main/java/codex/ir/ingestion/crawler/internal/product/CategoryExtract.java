package codex.ir.ingestion.crawler.internal.product;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 * Category card extraction result, semantically separate from product cards.
 *
 * <p>Intended for transitional/internal use. Not yet part of the public API.</p>
 */
public record CategoryExtract(
        URI url,
        String name,
        ExtractedCardType cardType,
        List<ExtractionWarning> warnings
) {
    public CategoryExtract {
        Objects.requireNonNull(url, "url must not be null");
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        name = name.trim();
        Objects.requireNonNull(cardType, "cardType must not be null");
        Objects.requireNonNull(warnings, "warnings must not be null");
        warnings = List.copyOf(warnings);
    }

    public CategoryExtract withWarnings(final List<ExtractionWarning> warnings) {
        Objects.requireNonNull(warnings, "warnings must not be null");
        return new CategoryExtract(url, name, cardType, warnings);
    }
}
