package codex.ir.ingestion.crawler.product;

import codex.ir.ingestion.WebPage;
import codex.ir.ingestion.crawler.classifier.PageClassification;
import codex.ir.ingestion.crawler.classifier.PageClassifier;
import codex.ir.ingestion.crawler.classifier.PageClassifiers;
import codex.ir.ingestion.crawler.classifier.UrlType;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Factory for {@link ProductDiscoverer} implementations.
 */
public final class ProductDiscoverers {

    private ProductDiscoverers() {
    }

    /**
     * Returns a general-purpose discoverer that composes the jsoupDefault
     * classifier, detail extractor, and card extractor.
     *
     * <p>PRODUCT pages → {@link ProductDetail} extraction only.<br>
     * CATEGORY and HOMEPAGE pages → {@link ProductCard} list only.<br>
     * All other types → empty result.</p>
     *
     * @return a Jsoup-based product discoverer
     */
    public static ProductDiscoverer jsoupDefault() {
        return new JsoupProductDiscoverer(
                PageClassifiers.jsoupDefault(),
                ProductDetailExtractors.jsoupDefault(),
                ProductCardExtractors.jsoupDefault()
        );
    }

    private static final class JsoupProductDiscoverer implements ProductDiscoverer {

        private final PageClassifier pageClassifier;
        private final ProductDetailExtractor detailExtractor;
        private final ProductCardExtractor cardExtractor;

        private JsoupProductDiscoverer(
                final PageClassifier pageClassifier,
                final ProductDetailExtractor detailExtractor,
                final ProductCardExtractor cardExtractor
        ) {
            this.pageClassifier = Objects.requireNonNull(pageClassifier, "pageClassifier must not be null");
            this.detailExtractor = Objects.requireNonNull(detailExtractor, "detailExtractor must not be null");
            this.cardExtractor = Objects.requireNonNull(cardExtractor, "cardExtractor must not be null");
        }

        @Override
        public ProductDiscoveryResult discover(final WebPage page) {
            Objects.requireNonNull(page, "page must not be null");
            final PageClassification classification = pageClassifier.classify(page);

            return switch (classification.type()) {
                case PRODUCT -> {
                    final Optional<ProductDetail> detail = detailExtractor.extract(page);
                    yield new ProductDiscoveryResult(page.url(), classification, detail, List.of());
                }
                case CATEGORY, HOMEPAGE -> {
                    final List<ProductCard> cards = cardExtractor.extract(page);
                    yield new ProductDiscoveryResult(page.url(), classification, Optional.empty(), cards);
                }
                default ->
                        new ProductDiscoveryResult(page.url(), classification, Optional.empty(), List.of());
            };
        }
    }
}
