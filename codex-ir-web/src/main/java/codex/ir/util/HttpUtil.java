package codex.ir.util;

import java.net.URI;

/**
 * Utility helpers for HTTP/HTTPS URI checks.
 *
 * <p>This class centralizes simple protocol-related validations used across
 * the crawling and ingestion layers.</p>
 *
 * @author jsanca & elo
 */
public class HttpUtil {

    /**
     * Determines whether the given URI uses an HTTP or HTTPS scheme.
     *
     * <p>This method performs a case-insensitive comparison of the URI scheme
     * against "http" and "https".</p>
     *
     * @param uri URI to evaluate
     * @return {@code true} if the URI scheme is HTTP or HTTPS; {@code false} otherwise
     */
    public static boolean isHttpUri(final URI uri) {
        if (null == uri) {
            return false;
        }
        final String scheme = uri.getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }
}
