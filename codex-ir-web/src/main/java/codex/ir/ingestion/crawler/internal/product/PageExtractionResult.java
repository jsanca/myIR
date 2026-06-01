package codex.ir.ingestion.crawler.internal.product;

import codex.ir.ingestion.crawler.classifier.PageClassification;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregated extraction result for a single page.
 *
 * <p>Groups product detail, product cards, and category cards detected on one page.
 * Intended for transitional/internal use. Not yet part of the public API.</p>
 */
public record PageExtractionResult(
        URI pageUri,
        PageClassification pageClassification,
        Optional<ProductDetailExtract> productDetail,
        List<ProductCardExtract> productCards,
        List<CategoryExtract> categories
) {
    public PageExtractionResult {
        Objects.requireNonNull(pageUri, "pageUri must not be null");
        Objects.requireNonNull(pageClassification, "pageClassification must not be null");
        Objects.requireNonNull(productDetail, "productDetail must not be null");
        Objects.requireNonNull(productCards, "productCards must not be null");
        Objects.requireNonNull(categories, "categories must not be null");
        productCards = List.copyOf(productCards);
        categories = List.copyOf(categories);
    }

    public static PageExtractionResult empty(final URI pageUri, final PageClassification pageClassification) {
        Objects.requireNonNull(pageUri, "pageUri must not be null");
        Objects.requireNonNull(pageClassification, "pageClassification must not be null");
        return new PageExtractionResult(
                pageUri, pageClassification,
                Optional.empty(), List.of(), List.of());
    }

    public static Builder builder(final URI pageUri, final PageClassification pageClassification) {
        Objects.requireNonNull(pageUri, "pageUri must not be null");
        Objects.requireNonNull(pageClassification, "pageClassification must not be null");
        return new Builder(pageUri, pageClassification);
    }

    public static final class Builder {
        private final URI pageUri;
        private final PageClassification pageClassification;
        private ProductDetailExtract productDetail;
        private List<ProductCardExtract> productCards = List.of();
        private List<CategoryExtract> categories = List.of();

        private Builder(final URI pageUri, final PageClassification pageClassification) {
            this.pageUri = pageUri;
            this.pageClassification = pageClassification;
        }

        public Builder productDetail(final ProductDetailExtract extract) {
            this.productDetail = Objects.requireNonNull(extract, "productDetail must not be null");
            return this;
        }

        public Builder productCards(final List<ProductCardExtract> cards) {
            this.productCards = Objects.requireNonNull(cards, "productCards must not be null");
            return this;
        }

        public Builder categories(final List<CategoryExtract> categories) {
            this.categories = Objects.requireNonNull(categories, "categories must not be null");
            return this;
        }

        public PageExtractionResult build() {
            return new PageExtractionResult(
                    pageUri, pageClassification,
                    Optional.ofNullable(productDetail),
                    productCards, categories);
        }
    }
}
