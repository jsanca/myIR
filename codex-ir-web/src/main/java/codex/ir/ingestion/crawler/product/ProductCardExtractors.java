package codex.ir.ingestion.crawler.product;

import codex.ir.ingestion.WebPage;
import codex.ir.ingestion.crawler.internal.product.ImageUrlResolver;
import codex.ir.ingestion.crawler.internal.product.ProductPriceParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Factory for {@link ProductCardExtractor} implementations.
 */
public final class ProductCardExtractors {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductCardExtractors.class);

    private ProductCardExtractors() {
    }

    /**
     * Returns a WooCommerce-specific card extractor for listing/category pages.
     */
    public static ProductCardExtractor woocommerceDefault() {
        return new WooCommerceProductCardExtractor(
                new ProductPriceParser(),
                new ImageUrlResolver()
        );
    }

    private static final class WooCommerceProductCardExtractor implements ProductCardExtractor {

        private final ProductPriceParser priceParser;
        private final ImageUrlResolver urlResolver;

        private WooCommerceProductCardExtractor(
                final ProductPriceParser priceParser,
                final ImageUrlResolver urlResolver
        ) {
            this.priceParser = Objects.requireNonNull(priceParser);
            this.urlResolver = Objects.requireNonNull(urlResolver);
        }

        @Override
        public List<ProductCard> extract(final WebPage page) {
            Objects.requireNonNull(page, "page must not be null");
            final String html = page.rawHtml();
            if (html == null || html.isBlank()) {
                return List.of();
            }

            final Document doc;
            try {
                doc = Jsoup.parse(html, page.url().toString());
            } catch (final Exception exception) {
                LOGGER.debug("Failed to parse HTML for {}", page.url(), exception);
                return List.of();
            }

            final Elements cards = doc.select(
                    "li.product, .product.type-product, .products > .product");
            if (cards.isEmpty()) {
                return List.of();
            }

            final List<ProductCard> result = new ArrayList<>();
            for (final Element card : cards) {
                extractCard(card, page.url()).ifPresent(result::add);
            }
            return result;
        }

        private Optional<ProductCard> extractCard(final Element card, final URI baseUri) {
            final URI productUrl = extractUrl(card, baseUri);
            if (productUrl == null) {
                return Optional.empty();
            }

            final String name = extractName(card);
            if (name == null || name.isBlank()) {
                return Optional.empty();
            }

            final Optional<ProductPrice> regularPrice = extractRegularPrice(card);
            final Optional<ProductPrice> salePrice = extractSalePrice(card);
            final Optional<ProductImage> thumbnail = extractThumbnail(card, baseUri);

            return Optional.of(new ProductCard(
                    productUrl, name, regularPrice, salePrice, thumbnail));
        }

        private URI extractUrl(final Element card, final URI baseUri) {
            final Element link = selectFirst(card,
                    "a.woocommerce-LoopProduct-link, "
                            + "a.woocommerce-loop-product__link, "
                            + "a[href]");
            if (link == null) {
                return null;
            }
            final String href = link.absUrl("href");
            if (href == null || href.isBlank()) {
                return null;
            }
            try {
                return baseUri.resolve(href);
            } catch (final Exception exception) {
                LOGGER.debug("Could not resolve product URL: {}", href, exception);
                return null;
            }
        }

        private String extractName(final Element card) {
            final Element nameEl = selectFirst(card,
                    ".woocommerce-loop-product__title, h2, h3");
            if (nameEl != null) {
                final String text = nameEl.text().trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
            final Element link = selectFirst(card, "a[href]");
            if (link != null) {
                final String text = link.text().trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
            return null;
        }

        private Optional<ProductPrice> extractRegularPrice(final Element card) {
            final Element delPrice = selectFirst(card,
                    ".price del .woocommerce-Price-amount");
            if (delPrice != null) {
                return priceParser.parse(delPrice);
            }
            final Element amount = selectFirst(card,
                    ".price .woocommerce-Price-amount");
            if (amount != null) {
                return priceParser.parse(amount);
            }
            return Optional.empty();
        }

        private Optional<ProductPrice> extractSalePrice(final Element card) {
            final Element insPrice = selectFirst(card,
                    ".price ins .woocommerce-Price-amount");
            if (insPrice != null) {
                return priceParser.parse(insPrice);
            }
            return Optional.empty();
        }

        private Optional<ProductImage> extractThumbnail(final Element card, final URI baseUri) {
            final Element img = selectFirst(card, "img");
            if (img == null) {
                return Optional.empty();
            }
            final URI imageUrl = urlResolver.resolve(img, baseUri);
            if (imageUrl == null) {
                return Optional.empty();
            }
            final String alt = Optional.ofNullable(img.attr("alt")).orElse("");
            return Optional.of(new ProductImage(imageUrl, alt, 0));
        }

        private static Element selectFirst(final Element context, final String cssQuery) {
            final Elements elements = context.select(cssQuery);
            return elements.isEmpty() ? null : elements.first();
        }
    }
}
