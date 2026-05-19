package codex.ir.ingestion.crawler.internal.product;

import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Comparator;

/**
 * Resolves image URLs from Jsoup elements, supporting data-src and srcset.
 */
public final class ImageUrlResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageUrlResolver.class);

    public ImageUrlResolver() {
    }

    /**
     * Resolves the best image URL from the given element, preferring
     * {@code data-large_image} → {@code data-src} → largest srcset → {@code src}.
     */
    public URI resolve(final Element img, final URI baseUri) {
        String url = img.attr("data-large_image");
        if (url.isBlank()) {
            url = img.attr("data-src");
        }
        if (url.isBlank()) {
            url = resolveLargestSrcset(img);
        }
        if (url.isBlank()) {
            url = img.attr("src");
        }
        if (url.isBlank()) {
            return null;
        }
        try {
            return baseUri.resolve(url);
        } catch (final Exception exception) {
            LOGGER.debug("Could not resolve image URL: {}", url, exception);
            return null;
        }
    }

    private String resolveLargestSrcset(final Element img) {
        final String srcset = img.attr("srcset");
        if (srcset == null || srcset.isBlank()) {
            return "";
        }
        return java.util.Arrays.stream(srcset.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.split("\\s+"))
                .filter(parts -> parts.length >= 1)
                .max(Comparator.comparingInt(this::parseWidth))
                .map(parts -> parts[0])
                .orElse("");
    }

    private int parseWidth(final String[] parts) {
        if (parts.length < 2) {
            return 0;
        }
        final String descriptor = parts[parts.length - 1];
        if (descriptor.endsWith("w")) {
            try {
                return Integer.parseInt(descriptor.substring(0, descriptor.length() - 1));
            } catch (final NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
