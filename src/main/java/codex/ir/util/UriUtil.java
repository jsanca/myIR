package codex.ir.util;

import codex.ir.ingestion.WebCrawlingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * Utility helpers for URI normalization and crawling-domain rule evaluation.
 *
 * <p>This class centralizes small but important URI-related decisions used by the
 * crawling layer, such as canonical normalization and domain filtering based on
 * the active {@link WebCrawlingConfig}.</p>
 *
 * @author jsanca & elo
 */
public class UriUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(UriUtil.class);

    /**
     * Normalizes the given URI into a canonical form suitable for crawling.
     *
     * <p>The current normalization strategy lowercases the scheme and host,
     * preserves user info, port, path, and query, removes the fragment, and then
     * applies {@link URI#normalize()} to collapse path navigation segments.</p>
     *
     * <p>If the URI is {@code null}, lacks a scheme or host, or cannot be
     * reconstructed safely, this method returns {@code null}.</p>
     *
     * @param uri URI to normalize
     * @return normalized URI, or {@code null} if normalization is not possible
     */
    public static URI normalizeUri(final URI uri) {

        if (uri == null) {
            return null;
        }

        final String scheme = uri.getScheme();
        final String host = uri.getHost();
        if (scheme == null || host == null) {
            return null;
        }

        try {
            return new URI(
                    scheme.toLowerCase(),
                    uri.getUserInfo(),
                    host.toLowerCase(),
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    null
            ).normalize();
        } catch (final Exception exception) {
            LOGGER.debug("Ignoring URI that could not be normalized: {}", uri, exception);
            return null;
        }
    }

    /**
     * Evaluates whether a candidate URI is allowed to be traversed according to
     * the configured domain rules.
     *
     * <p>The decision considers:
     * <ul>
     *     <li>Whether the candidate host is present and non-blank</li>
     *     <li>Whether the host is explicitly allowed by {@code allowedDomains}</li>
     *     <li>Whether traversal is restricted to the same domain as the root URI</li>
     *     <li>Whether external links are allowed at all</li>
     * </ul>
     * </p>
     *
     * @param candidateUri URI being considered for traversal
     * @param rootUri root URI that defines the traversal origin
     * @param config crawling configuration containing the domain rules
     * @return {@code true} if the URI is allowed by the configured domain rules;
     *         {@code false} otherwise
     */
    public static boolean isAllowedByDomainRules(final URI candidateUri,
                                                 final URI rootUri,
                                                 final WebCrawlingConfig config) {

        final String candidateHost = candidateUri.getHost();
        if (candidateHost == null || candidateHost.isBlank()) {
            return false;
        }

        if (!config.allowedDomains().isEmpty() && !config.allowedDomains().contains(candidateHost)) {
            return false;
        }

        if (config.sameDomainOnly()) {
            final String rootHost = rootUri.getHost();
            if (!candidateHost.equalsIgnoreCase(rootHost)) {
                return false;
            }
        }

        if (!config.followExternalLinks() && rootUri.getHost() != null
                && !candidateHost.equalsIgnoreCase(rootUri.getHost())) {
            return false;
        }

        return true;
    }
}
