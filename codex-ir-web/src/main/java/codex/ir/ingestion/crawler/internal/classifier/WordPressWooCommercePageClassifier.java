package codex.ir.ingestion.crawler.internal.classifier;

import codex.ir.ingestion.WebPage;
import codex.ir.ingestion.crawler.classifier.ClassifiedUrl;
import codex.ir.ingestion.crawler.classifier.PageClassification;
import codex.ir.ingestion.crawler.classifier.PageClassifier;
import codex.ir.ingestion.crawler.classifier.UrlClassifier;
import codex.ir.ingestion.crawler.classifier.UrlType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Objects;

/**
 * Page classifier that detects WordPress and WooCommerce signals in HTML,
 * refining the URL-based classification.
 *
 * <p>URL types that are clearly not content pages are returned as-is without
 * HTML inspection. For other types, WooCommerce CSS classes and JSON-LD may
 * override the URL-derived type.</p>
 */
public final class WordPressWooCommercePageClassifier implements PageClassifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(WordPressWooCommercePageClassifier.class);

    private final UrlClassifier urlClassifier;

    public WordPressWooCommercePageClassifier(final UrlClassifier urlClassifier) {
        this.urlClassifier = Objects.requireNonNull(urlClassifier, "urlClassifier must not be null");
    }

    @Override
    public PageClassification classify(final WebPage page) {
        final URI uri = page.url();
        final ClassifiedUrl urlClassification = urlClassifier.classify(uri);
        final UrlType urlType = urlClassification.type();

        if (HtmlSignals.PASS_THROUGH_TYPES.contains(urlType)) {
            return new PageClassification(uri, urlType, urlClassification, false, false);
        }

        final String html = page.rawHtml();
        if (html == null || html.isBlank()) {
            return new PageClassification(uri, urlType, urlClassification, false, false);
        }

        final Document doc;
        try {
            doc = Jsoup.parse(html, uri.toString());
        } catch (final Exception exception) {
            LOGGER.debug("Failed to parse HTML for {}", uri, exception);
            return new PageClassification(uri, urlType, urlClassification, false, false);
        }

        final boolean wpDetected = detectWordPress(doc);
        final boolean wcDetected = detectWooCommerce(doc);
        final UrlType refinedType = refineType(doc, urlClassification, wcDetected);

        return new PageClassification(uri, refinedType, urlClassification, wpDetected, wcDetected);
    }

    private boolean detectWordPress(final Document doc) {
        if (HtmlSignals.selectFirst(doc, "meta[name=generator][content*=WordPress]") != null) {
            return true;
        }
        if (HtmlSignals.selectFirst(doc, "link[rel=\"https://api.w.org/\"]") != null) {
            return true;
        }
        if (HtmlSignals.selectFirst(doc, "link[href*=\"wp-content/\"], script[src*=\"wp-content/\"]") != null) {
            return true;
        }
        if (HtmlSignals.selectFirst(doc, "link[href*=\"wp-includes/\"], script[src*=\"wp-includes/\"]") != null) {
            return true;
        }
        return false;
    }

    private boolean detectWooCommerce(final Document doc) {
        if (doc.body() != null) {
            if (doc.body().hasClass("woocommerce")
                    || doc.body().hasClass("woocommerce-page")
                    || doc.body().hasClass("single-product")) {
                return true;
            }
        }
        if (HtmlSignals.selectFirst(doc, "link[href*=\"woocommerce\"], script[src*=\"woocommerce\"]") != null) {
            return true;
        }
        if (HtmlSignals.selectFirst(doc, ".woocommerce-breadcrumb") != null) {
            return true;
        }
        if (HtmlSignals.selectFirst(doc, ".woocommerce-Price-amount") != null) {
            return true;
        }
        return false;
    }

    private UrlType refineType(
            final Document doc,
            final ClassifiedUrl urlClassification,
            final boolean wcDetected
    ) {
        if (isProductPage(doc)) {
            return UrlType.PRODUCT;
        }
        if (isHomePage(doc, urlClassification)) {
            return UrlType.HOMEPAGE;
        }
        if (isCategoryPage(doc, wcDetected)) {
            return UrlType.CATEGORY;
        }
        return urlClassification.type();
    }

    private boolean isProductPage(final Document doc) {
        if (doc.body() != null && doc.body().hasClass("single-product")) {
            return true;
        }
        if (HtmlSignals.selectFirst(doc, ".product_title, h1.product_title") != null) {
            return true;
        }
        if (HtmlSignals.selectFirst(doc, ".single_add_to_cart_button, form.cart") != null) {
            return true;
        }
        if (HtmlSignals.hasJsonLdType(doc, "Product")) {
            return true;
        }
        return false;
    }

    private boolean isCategoryPage(final Document doc, final boolean wcDetected) {
        if (doc.body() != null) {
            final Element body = doc.body();
            if (body.hasClass("tax-product_cat")
                    || body.hasClass("post-type-archive-product")
                    || (body.hasClass("archive") && body.hasClass("woocommerce"))) {
                return true;
            }
        }
        if (!wcDetected) {
            return false;
        }
        if (HtmlSignals.selectFirst(doc, ".products") != null
                && HtmlSignals.selectFirst(doc, "li.product, .product.type-product") != null) {
            return true;
        }
        if (HtmlSignals.selectFirst(doc, ".woocommerce-loop-product__title") != null) {
            return true;
        }
        if (HtmlSignals.selectFirst(doc, ".woocommerce-pagination") != null) {
            return true;
        }
        return false;
    }

    private boolean isHomePage(final Document doc, final ClassifiedUrl urlClassification) {
        if (urlClassification.type() == UrlType.HOMEPAGE) {
            return true;
        }
        final String path = urlClassification.uri().getRawPath();
        if (path == null || path.equals("/") || path.isEmpty()) {
            return true;
        }
        return doc.body() != null && doc.body().hasClass("home");
    }
}
