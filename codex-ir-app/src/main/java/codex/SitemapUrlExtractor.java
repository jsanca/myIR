package codex;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Extracts page URLs from a sitemap or sitemap index.
 *
 * <p>Supports {@code <urlset>} (flat list) and {@code <sitemapindex>} (one level
 * of child sitemaps). URLs are filtered to HTTP/HTTPS and to the same host as
 * the root sitemap.</p>
 */
final class SitemapUrlExtractor {

    private final Function<URI, Optional<InputStream>> streamProvider;

    /** Package-private — use {@link #http()} for production or inject a provider for tests. */
    SitemapUrlExtractor(final Function<URI, Optional<InputStream>> streamProvider) {
        this.streamProvider = streamProvider;
    }

    /** Returns an extractor that fetches over real HTTP/HTTPS. */
    static SitemapUrlExtractor http() {
        return new SitemapUrlExtractor(SitemapUrlExtractor::openStream);
    }

    /**
     * Extracts up to {@code limit} page URIs from the given sitemap.
     *
     * @param sitemapUri root sitemap URL
     * @param limit      maximum number of URLs to return
     * @return discovered page URIs, in document order, up to {@code limit}
     */
    List<URI> extract(final URI sitemapUri, final int limit) {
        final List<URI> result = new ArrayList<>();
        final String host = sitemapUri.getHost();
        collectUrls(sitemapUri, host, limit, result, false);
        return List.copyOf(result);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private void collectUrls(
            final URI uri,
            final String sitemapHost,
            final int limit,
            final List<URI> acc,
            final boolean isChild) {
        if (acc.size() >= limit) {
            return;
        }
        final Optional<Document> doc = fetchDocument(uri);
        if (doc.isEmpty()) {
            return;
        }
        final String root = doc.get().getDocumentElement().getLocalName();

        if ("sitemapindex".equals(root) && !isChild) {
            final NodeList locs = doc.get().getElementsByTagNameNS("*", "loc");
            for (int i = 0; i < locs.getLength() && acc.size() < limit; i++) {
                final URI child = parseUri(locs.item(i).getTextContent().trim());
                if (child != null) {
                    collectUrls(child, sitemapHost, limit, acc, true);
                }
            }
        } else {
            final NodeList locs = doc.get().getElementsByTagNameNS("*", "loc");
            for (int i = 0; i < locs.getLength() && acc.size() < limit; i++) {
                final URI page = parseUri(locs.item(i).getTextContent().trim());
                if (page != null && isAcceptable(page, sitemapHost)) {
                    acc.add(page);
                }
            }
        }
    }

    private Optional<Document> fetchDocument(final URI uri) {
        final Optional<InputStream> stream = streamProvider.apply(uri);
        if (stream.isEmpty()) {
            return Optional.empty();
        }
        try (final InputStream in = stream.get()) {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            try {
                factory.setFeature(
                        "http://apache.org/xml/features/disallow-doctype-decl", true);
            } catch (final Exception ignored) {
                // feature not supported by this XML parser — continue without it
            }
            final DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null); // suppress Xerces stderr noise
            return Optional.of(builder.parse(in));
        } catch (final Exception e) {
            System.out.println("[WARN] Failed to parse sitemap " + uri + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    private static boolean isAcceptable(final URI uri, final String sitemapHost) {
        final String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return false;
        }
        return sitemapHost == null || sitemapHost.equalsIgnoreCase(uri.getHost());
    }

    private static URI parseUri(final String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return URI.create(raw);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    private static Optional<InputStream> openStream(final URI uri) {
        try {
            return Optional.of(uri.toURL().openStream());
        } catch (final Exception e) {
            System.out.println("[WARN] Could not fetch sitemap: " + uri + " — " + e.getMessage());
            return Optional.empty();
        }
    }
}
