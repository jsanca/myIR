package codex.ir.ingestion.crawler.product;

import codex.ir.ingestion.WebPage;
import codex.ir.ingestion.crawler.internal.product.ImageUrlResolver;
import codex.ir.ingestion.crawler.internal.product.JsonLdProductData;
import codex.ir.ingestion.crawler.internal.product.JsonLdProductExtractor;
import codex.ir.ingestion.crawler.internal.product.ProductImageExtractor;
import codex.ir.ingestion.crawler.internal.product.ProductPriceParser;
import codex.ir.ingestion.crawler.internal.text.HtmlTextDecoder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
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
     * Returns a general-purpose Jsoup-based extractor using a priority chain:
     * JSON-LD → OpenGraph meta tags → HTML selectors → document title/description fallback.
     *
     * <p>Returns {@code Optional.empty()} only when no name can be resolved
     * from any source.</p>
     *
     * @return a generic HTML product extractor
     */
    public static ProductDetailExtractor jsoupDefault() {
        return new JsoupGenericProductDetailExtractor();
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

    private static final class JsoupGenericProductDetailExtractor implements ProductDetailExtractor {

        private final JsonLdProductExtractor jsonLdExtractor;
        private final ImageUrlResolver imageResolver;

        private JsoupGenericProductDetailExtractor() {
            this.jsonLdExtractor = new JsonLdProductExtractor();
            this.imageResolver = new ImageUrlResolver();
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
            } catch (final Exception ex) {
                LOGGER.debug("Failed to parse HTML for {}", page.url(), ex);
                return Optional.empty();
            }

            final URI uri = page.url();
            final JsonLdProductData jsonLd = jsonLdExtractor.extract(doc, uri);

            final String name = HtmlTextDecoder.decode(firstNonBlank(
                    jsonLd.name(),
                    metaContent(doc, "meta[property=og:title]"),
                    selectText(doc, "h1"),
                    doc.title()
            ));
            if (name == null) {
                return Optional.empty();
            }

            final Optional<String> sku = Optional.ofNullable(HtmlTextDecoder.decode(firstNonBlank(
                    jsonLd.sku(),
                    selectText(doc, "[itemprop=sku], [class*=sku]")
            )));

            final Optional<String> brand = Optional.ofNullable(HtmlTextDecoder.decode(firstNonBlank(
                    jsonLd.brand(),
                    selectText(doc, "[itemprop=brand], [class*=brand]")
            )));

            final Optional<String> shortDescription = Optional.ofNullable(HtmlTextDecoder.decode(firstNonBlank(
                    jsonLd.description(),
                    metaContent(doc, "meta[property=og:description]"),
                    metaContent(doc, "meta[name=description]")
            )));

            final Optional<ProductPrice> regularPrice = jsonLd.regularPrice() != null
                    ? Optional.of(jsonLd.regularPrice())
                    : extractOgPrice(doc);

            final Optional<String> availability = Optional.ofNullable(HtmlTextDecoder.decode(firstNonBlank(
                    jsonLd.availability(),
                    metaContent(doc, "meta[property=product:availability]"),
                    selectText(doc, "[itemprop=availability], [class*=availability]")
            )));

            final List<ProductImage> images = extractImages(doc, uri, jsonLd);

            final URI resolvedUrl = resolveUrl(uri, firstNonBlank(
                    jsonLd.url(),
                    metaContent(doc, "meta[property=og:url]"),
                    canonicalHref(doc)
            ));

            return Optional.of(new ProductDetail(
                    resolvedUrl, name, sku, regularPrice, Optional.empty(),
                    shortDescription, images, brand, availability
            ));
        }

        private List<ProductImage> extractImages(
                final Document doc, final URI baseUri, final JsonLdProductData jsonLd
        ) {
            if (!jsonLd.images().isEmpty()) {
                return jsonLd.images();
            }

            final String ogImage = metaContent(doc, "meta[property=og:image]");
            if (ogImage != null && !ogImage.isBlank()) {
                try {
                    return List.of(new ProductImage(baseUri.resolve(ogImage.trim()), "", 0));
                } catch (final Exception ignored) {
                }
            }

            final Elements imgs = doc.select(
                    "img[itemprop=image], img[class*=product], .product img, .product-image img"
            );
            final List<ProductImage> result = new ArrayList<>();
            int order = 0;
            for (final Element img : imgs) {
                final URI imageUri = imageResolver.resolve(img, baseUri);
                if (imageUri == null) {
                    continue;
                }
                final String alt = HtmlTextDecoder.decode(img.attr("alt"));
                result.add(new ProductImage(imageUri, alt != null ? alt : "", order++));
                if (order >= 5) {
                    break;
                }
            }
            return result;
        }

        private Optional<ProductPrice> extractOgPrice(final Document doc) {
            final String amount = metaContent(doc, "meta[property=product:price:amount]");
            if (amount == null || amount.isBlank()) {
                return Optional.empty();
            }
            try {
                final String currency = metaContent(doc, "meta[property=product:price:currency]");
                return Optional.of(new ProductPrice(new BigDecimal(amount.trim()), currency));
            } catch (final NumberFormatException ignored) {
                return Optional.empty();
            }
        }

        private static URI resolveUrl(final URI baseUri, final String candidate) {
            if (candidate == null || candidate.isBlank()) {
                return baseUri;
            }
            try {
                return baseUri.resolve(candidate.trim());
            } catch (final Exception ignored) {
                return baseUri;
            }
        }

        private static String canonicalHref(final Document doc) {
            final Element link = doc.selectFirst("link[rel=canonical]");
            return link != null ? link.attr("href").trim() : null;
        }

        private static String firstNonBlank(final String... candidates) {
            for (final String s : candidates) {
                if (s != null && !s.isBlank()) {
                    return s.trim();
                }
            }
            return null;
        }

        private static String metaContent(final Document doc, final String cssQuery) {
            final Element el = doc.selectFirst(cssQuery);
            return el != null ? el.attr("content") : null;
        }

        private static String selectText(final Document doc, final String cssQuery) {
            final Element el = doc.selectFirst(cssQuery);
            if (el == null) {
                return null;
            }
            final String text = el.text().trim();
            return text.isBlank() ? null : text;
        }
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

            final String name = HtmlTextDecoder.decode(
                    Optional.ofNullable(extractName(doc))
                    .filter(n -> !n.isBlank())
                    .orElse(jsonLd.name()));
            if (name == null || name.isBlank()) {
                return Optional.empty();
            }

            final Optional<String> sku = extractSku(doc)
                    .or(() -> Optional.ofNullable(jsonLd.sku()))
                    .map(HtmlTextDecoder::decode);
            final Optional<ProductPrice> regularPrice = extractRegularPrice(doc)
                    .or(() -> Optional.ofNullable(jsonLd.regularPrice()));
            final Optional<ProductPrice> salePrice = extractSalePrice(doc);
            final Optional<String> shortDescription = extractShortDescription(doc)
                    .or(() -> Optional.ofNullable(jsonLd.description()))
                    .map(HtmlTextDecoder::decode);
            final List<ProductImage> cssImages = imageExtractor.extract(doc, page.url());
            final List<ProductImage> mergedImages = cssImages.isEmpty()
                    ? jsonLd.images() : cssImages;

            return Optional.of(new ProductDetail(
                    page.url(), name, sku, regularPrice, salePrice, shortDescription, mergedImages,
                    Optional.empty(), Optional.empty()
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
