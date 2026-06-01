package codex.ir.ingestion.crawler.internal.product;

import java.net.URI;
import java.util.Objects;

/**
 * Stable canonical key for grouping product cards and details that refer
 * to the same product, even when URLs differ by trailing slash, fragment,
 * or query parameters.
 *
 * <p>The key is the normalized URL path. Scheme, host, port, query string,
 * and fragment are discarded. Only product-path identity matters.</p>
 *
 * <p>Examples that produce the same key {@code /producto/sole-wallet}:</p>
 * <ul>
 *   <li>{@code https://syjleathers.com/producto/sole-wallet/}</li>
 *   <li>{@code https://syjleathers.com/producto/sole-wallet}</li>
 *   <li>{@code https://syjleathers.com/producto/sole-wallet/?foo=bar}</li>
 *   <li>{@code https://syjleathers.com/producto/sole-wallet/#categories}</li>
 * </ul>
 */
public record CanonicalProductKey(String value) {

    public CanonicalProductKey {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    /**
     * Creates a canonical product key from a product page URL.
     *
     * <p>Normalization rules applied:
     * <ol>
     *   <li>Remove fragment ({@code #})</li>
     *   <li>Remove query parameters ({@code ?})</li>
     *   <li>Remove trailing slash from the path</li>
     *   <li>Use only the path component as the identity key</li>
     * </ol>
     *
     * <p>Scheme, host, and port are intentionally discarded — cross-domain
     * equivalence is out of scope.</p>
     *
     * @param url the product page URL, must not be null
     * @return a canonical product key
     * @throws NullPointerException     if {@code url} is null
     * @throws IllegalArgumentException if the URL has no meaningful path
     */
    public static CanonicalProductKey fromUrl(final URI url) {
        Objects.requireNonNull(url, "url must not be null");

        String path = url.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }

        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        if (path.equals("/") || path.isBlank()) {
            throw new IllegalArgumentException(
                    "URL has no meaningful product path: " + url);
        }

        return new CanonicalProductKey(path);
    }
}
