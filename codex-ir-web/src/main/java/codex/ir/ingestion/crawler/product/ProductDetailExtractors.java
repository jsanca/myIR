package codex.ir.ingestion.crawler.product;

import codex.ir.ingestion.WebPage;
import codex.ir.ingestion.crawler.internal.product.JsonLdProductData;
import codex.ir.ingestion.crawler.internal.product.JsonLdProductExtractor;
import codex.ir.ingestion.crawler.internal.product.ProductImageExtractor;
import codex.ir.ingestion.crawler.internal.product.ProductPriceParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Factory for {@link ProductDetailExtractor} implementations.
 */
public final class ProductDetailExtractors {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductDetailExtractors.class);

    private ProductDetailExtractors() {
    }

    /**
     * Returns a WooCommerce-specific extractor using CSS selectors
     * with JSON-LD fallback.
     */
    public static ProductDetailExtractor woocommerceDefault() {
        return new WooCommerceProductDetailExtractor(
                new ProductPriceParser(),
                new ProductImageExtractor(),
                new JsonLdProductExtractor()
        );
    }

    private static final class WooCommerceProductDetailExtractor implements ProductDetailExtractor {

        private final ProductPriceParser priceParser;
        private final ProductImageExtractor imageExtractor;
        private final JsonLdProductExtractor jsonLdExtractor;

        private WooCommerceProductDetailExtractor(
                final ProductPriceParser priceParser,
                final ProductImageExtractor imageExtractor,
                final JsonLdProductExtractor jsonLdExtractor
        ) {
            this.priceParser = Objects.requireNonNull(priceParser);
            this.imageExtractor = Objects.requireNonNull(imageExtractor);
            this.jsonLdExtractor = Objects.requireNonNull(jsonLdExtractor);
        }

        @Override
        public Optional<ProductDetail> extract(final WebPage page) {
            Objects.requireNonNull(page, "page must not be null");
            final String html = page.rawHtml();
            if (html == null || html.isBlank()) {
                return Optional.empty();
            }

            final Document doc;
            try {
                doc = Jsoup.parse(html, page.url().toString());
            } catch (final Exception exception) {
                LOGGER.debug("Failed to parse HTML for {}", page.url(), exception);
                return Optional.empty();
            }

            final JsonLdProductData jsonLd = jsonLdExtractor.extract(doc, page.url());

            final String name = Optional.ofNullable(extractName(doc))
                    .filter(n -> !n.isBlank())
                    .orElse(jsonLd.name());
            if (name == null || name.isBlank()) {
                return Optional.empty();
            }

            final Optional<String> sku = extractSku(doc)
                    .or(() -> Optional.ofNullable(jsonLd.sku()));
            final Optional<ProductPrice> regularPrice = extractRegularPrice(doc)
                    .or(() -> Optional.ofNullable(jsonLd.regularPrice()));
            final Optional<ProductPrice> salePrice = extractSalePrice(doc);
            final Optional<String> shortDescription = extractShortDescription(doc)
                    .or(() -> Optional.ofNullable(jsonLd.description()));
            final List<ProductImage> cssImages = imageExtractor.extract(doc, page.url());
            final List<ProductImage> mergedImages = cssImages.isEmpty()
                    ? jsonLd.images() : cssImages;

            return Optional.of(new ProductDetail(
                    page.url(), name, sku, regularPrice, salePrice, shortDescription, mergedImages
            ));
        }

        private String extractName(final Document doc) {
            final Element titleEl = selectFirst(doc, "h1.product_title");
            if (titleEl != null) {
                return titleEl.text().trim();
            }
            final Element productTitle = selectFirst(doc, ".product_title");
            if (productTitle != null) {
                return productTitle.text().trim();
            }
            final Element ogTitle = selectFirst(doc, "meta[property=\"og:title\"]");
            if (ogTitle != null) {
                return ogTitle.attr("content").trim();
            }
            return null;
        }

        private Optional<String> extractSku(final Document doc) {
            final Element skuEl = selectFirst(doc, ".sku");
            if (skuEl != null) {
                final String text = skuEl.text().trim();
                if (!text.isBlank()) {
                    return Optional.of(text);
                }
            }
            final Element metaSku = selectFirst(doc, ".product_meta .sku");
            if (metaSku != null) {
                final String text = metaSku.text().trim();
                if (!text.isBlank()) {
                    return Optional.of(text);
                }
            }
            return Optional.empty();
        }

        private Optional<ProductPrice> extractRegularPrice(final Document doc) {
            final Element delPrice = selectFirst(doc, "p.price del .woocommerce-Price-amount, "
                    + ".price del .woocommerce-Price-amount");
            if (delPrice != null) {
                return priceParser.parse(delPrice);
            }
            return extractPriceAmount(doc, ".summary .price .woocommerce-Price-amount, "
                    + "p.price .woocommerce-Price-amount, .price .woocommerce-Price-amount");
        }

        private Optional<ProductPrice> extractSalePrice(final Document doc) {
            final Element insPrice = selectFirst(doc, "p.price ins .woocommerce-Price-amount, "
                    + ".price ins .woocommerce-Price-amount");
            if (insPrice != null) {
                return priceParser.parse(insPrice);
            }
            return Optional.empty();
        }

        private Optional<ProductPrice> extractPriceAmount(final Document doc, final String cssQuery) {
            final Element el = selectFirst(doc, cssQuery);
            if (el != null) {
                return priceParser.parse(el);
            }
            return Optional.empty();
        }

        private Optional<String> extractShortDescription(final Document doc) {
            final Element descEl = selectFirst(doc, ".woocommerce-product-details__short-description");
            if (descEl != null) {
                final String text = descEl.text().trim();
                if (!text.isBlank()) {
                    return Optional.of(text);
                }
            }
            return Optional.empty();
        }

        private static Element selectFirst(final Document doc, final String cssQuery) {
            final Elements elements = doc.select(cssQuery);
            return elements.isEmpty() ? null : elements.first();
        }
    }
}
