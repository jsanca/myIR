package codex.ir.ingestion.crawler.internal.product;

import codex.ir.ingestion.crawler.product.ProductImage;
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
 * Extracts product gallery images from WooCommerce HTML.
 */
public final class ProductImageExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductImageExtractor.class);
    private final ImageUrlResolver urlResolver = new ImageUrlResolver();

    public ProductImageExtractor() {
    }

    public List<ProductImage> extract(final Document doc, final URI baseUri) {
        Objects.requireNonNull(doc, "doc must not be null");
        Objects.requireNonNull(baseUri, "baseUri must not be null");
        final Elements sources = doc.select(
                ".woocommerce-product-gallery__image img, "
                        + "figure.woocommerce-product-gallery__wrapper img");
        if (sources.isEmpty()) {
            return List.of();
        }

        final List<ProductImage> images = new ArrayList<>();
        int order = 0;
        for (final Element img : sources) {
            final URI imageUrl = urlResolver.resolve(img, baseUri);
            if (imageUrl == null) {
                continue;
            }
            final String alt = Optional.ofNullable(img.attr("alt")).orElse("");
            images.add(new ProductImage(imageUrl, alt, order++));
        }
        return images;
    }
}
