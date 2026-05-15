
package codex.ir.canonicalizer;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Factory methods for building {@link UriCanonicalizer} instances.
 * @author jsanca & elo
 */
public final class UriCanonicalizers {

    private UriCanonicalizers() {
    }

    private static final List<UriCanonicalizer> DEFAULT_CANONICALIZERS = List.of(
            UriCanonicalizers::removeFragment,
            UriCanonicalizers::lowercaseSchemeAndHost,
            UriCanonicalizers::removeDefaultPort,
            UriCanonicalizers::normalizePath,
            UriCanonicalizers::sortQueryParameters
    );

    /**
     * Creates a default web-oriented URI canonicalizer.
     * <p>
     * The resulting pipeline applies a small set of generic web canonicalization
     * rules and then executes any custom canonicalizers provided by the caller.
     *
     * @param customCanonicalizers optional custom canonicalizers appended to the end of the pipeline
     * @return a pipeline-based URI canonicalizer
     */
    public static UriCanonicalizer defaultWeb(final UriCanonicalizer... customCanonicalizers) {

        if (customCanonicalizers != null && customCanonicalizers.length > 0) {
            final List<UriCanonicalizer> canonicalizers = new ArrayList<>(DEFAULT_CANONICALIZERS);
            canonicalizers.addAll(Arrays.asList(customCanonicalizers));
            return new PipelineUriCanonicalizer(canonicalizers);
        }

        return new PipelineUriCanonicalizer(DEFAULT_CANONICALIZERS);
    }

    private static URI removeFragment(final URI uri) {
        Objects.requireNonNull(uri, "uri cannot be null");

        try {
            return new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    uri.getHost(),
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    null
            );
        } catch (final URISyntaxException e) {
            throw new IllegalArgumentException("Could not remove fragment from URI: " + uri, e);
        }
    }

    private static URI lowercaseSchemeAndHost(final URI uri) {
        Objects.requireNonNull(uri, "uri cannot be null");

        final String scheme = uri.getScheme() == null
                ? null
                : uri.getScheme().toLowerCase(Locale.ROOT);
        final String host = uri.getHost() == null
                ? null
                : uri.getHost().toLowerCase(Locale.ROOT);

        try {
            return new URI(
                    scheme,
                    uri.getUserInfo(),
                    host,
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment()
            );
        } catch (final URISyntaxException e) {
            throw new IllegalArgumentException("Could not lowercase scheme and host for URI: " + uri, e);
        }
    }

    private static URI removeDefaultPort(final URI uri) {
        Objects.requireNonNull(uri, "uri cannot be null");

        final int port;
        if ("http".equalsIgnoreCase(uri.getScheme()) && uri.getPort() == 80) {
            port = -1;
        } else if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getPort() == 443) {
            port = -1;
        } else {
            port = uri.getPort();
        }

        try {
            return new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    uri.getHost(),
                    port,
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment()
            );
        } catch (final URISyntaxException e) {
            throw new IllegalArgumentException("Could not remove default port from URI: " + uri, e);
        }
    }

    private static URI normalizePath(final URI uri) {
        Objects.requireNonNull(uri, "uri cannot be null");
        return uri.normalize();
    }

    private static URI sortQueryParameters(final URI uri) {
        Objects.requireNonNull(uri, "uri cannot be null");

        final String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return uri;
        }

        final String sortedQuery = Arrays.stream(query.split("&"))
                .filter(parameter -> !parameter.isBlank())
                .sorted()
                .collect(Collectors.joining("&"));

        try {
            return new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    uri.getHost(),
                    uri.getPort(),
                    uri.getPath(),
                    sortedQuery,
                    uri.getFragment()
            );
        } catch (final URISyntaxException e) {
            throw new IllegalArgumentException("Could not sort query parameters for URI: " + uri, e);
        }
    }

    /**
     * {@link UriCanonicalizer} implementation that applies a sequence of
     * canonicalization steps to the same URI.
     */
    private static final class PipelineUriCanonicalizer implements UriCanonicalizer {

        private final Collection<UriCanonicalizer> canonicalizers;

        private PipelineUriCanonicalizer(final Collection<UriCanonicalizer> canonicalizers) {
            this.canonicalizers = List.copyOf(Objects.requireNonNull(canonicalizers,
                    "canonicalizers cannot be null"));
        }

        @Override
        public URI canonicalize(final URI uri) {
            Objects.requireNonNull(uri, "uri cannot be null");

            URI current = uri;
            for (final UriCanonicalizer canonicalizer : this.canonicalizers) {
                current = Objects.requireNonNull(canonicalizer.canonicalize(current),
                        "canonicalized URI cannot be null");
            }

            return current;
        }
    }
}
