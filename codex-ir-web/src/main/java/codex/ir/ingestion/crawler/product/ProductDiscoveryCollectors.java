package codex.ir.ingestion.crawler.product;

import codex.ir.ingestion.WebPage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Factory for {@link ProductDiscoveryCollector} implementations.
 */
public final class ProductDiscoveryCollectors {

    private ProductDiscoveryCollectors() {
    }

    /**
     * Returns a collector that composes {@link ProductDiscoverers#jsoupDefault()}.
     *
     * @return a Jsoup-based product discovery collector
     */
    public static ProductDiscoveryCollector jsoupDefault() {
        return new AggregatingCollector(ProductDiscoverers.jsoupDefault());
    }

    private static final class AggregatingCollector implements ProductDiscoveryCollector {

        private final ProductDiscoverer discoverer;

        private AggregatingCollector(final ProductDiscoverer discoverer) {
            this.discoverer = Objects.requireNonNull(discoverer, "discoverer must not be null");
        }

        @Override
        public ProductDiscoveryReport collect(final List<WebPage> pages) {
            Objects.requireNonNull(pages, "pages must not be null");
            if (pages.isEmpty()) {
                return ProductDiscoveryReport.empty();
            }

            final List<ProductDiscoveryResult> pageResults = new ArrayList<>(pages.size());
            final List<ProductDetail> productDetails = new ArrayList<>();
            final List<ProductCard> productCards = new ArrayList<>();

            for (final WebPage page : pages) {
                final ProductDiscoveryResult result = discoverer.discover(page);
                pageResults.add(result);
                result.productDetail().ifPresent(productDetails::add);
                productCards.addAll(result.productCards());
            }

            return new ProductDiscoveryReport(pageResults, productDetails, productCards);
        }
    }
}
