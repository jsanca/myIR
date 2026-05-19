package codex.ir.ingestion.crawler.internal.sitemap;

import codex.ir.ingestion.crawler.fetcher.WebHttpFetcher;
import codex.ir.ingestion.crawler.fetcher.WebHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches and parses robots.txt to extract {@code Sitemap:} directives.
 *
 * <p>Handles case-insensitive matching, multiple sitemap lines, and
 * gracefully returns an empty list if robots.txt is missing, inaccessible,
 * or contains no sitemap directives.</p>
 */
final class RobotsParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(RobotsParser.class);

    private static final Pattern SITEMAP_PATTERN = Pattern.compile(
            "^\\s*sitemap\\s*:\\s*(.+?)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    private final WebHttpFetcher httpFetcher;

    RobotsParser(final WebHttpFetcher httpFetcher) {
        this.httpFetcher = httpFetcher;
    }

    /**
     * Fetches and parses robots.txt from the given base URL to extract
     * sitemap URLs.
     *
     * @param baseUri the site's base URL (e.g., https://example.com)
     * @return list of discovered sitemap URIs, or empty list
     */
    List<URI> discoverSitemapUris(final URI baseUri) {
        final URI robotsTxtUri = buildRobotsTxtUri(baseUri);

        try {
            final WebHttpResponse response = httpFetcher.fetch(robotsTxtUri);
            if (!response.isSuccessful()) {
                LOGGER.debug("robots.txt not available at {}: status={}", robotsTxtUri, response.statusCode());
                return Collections.emptyList();
            }

            final String body = response.body();
            if (body == null || body.isBlank()) {
                LOGGER.debug("robots.txt at {} is empty", robotsTxtUri);
                return Collections.emptyList();
            }

            final List<URI> sitemapUris = parseSitemapLines(body, baseUri);
            LOGGER.debug("Discovered {} sitemap URI(s) from {}", sitemapUris.size(), robotsTxtUri);
            return sitemapUris;
        } catch (final Exception exception) {
            LOGGER.warn("Failed to fetch or parse robots.txt at {}", robotsTxtUri, exception);
            return Collections.emptyList();
        }
    }

    private URI buildRobotsTxtUri(final URI baseUri) {
        try {
            return new URI(
                    baseUri.getScheme(),
                    baseUri.getUserInfo(),
                    baseUri.getHost(),
                    baseUri.getPort(),
                    "/robots.txt",
                    null,
                    null
            );
        } catch (final Exception exception) {
            throw new IllegalArgumentException("Could not build robots.txt URI from: " + baseUri, exception);
        }
    }

    private List<URI> parseSitemapLines(final String body, final URI baseUri) {
        final Matcher matcher = SITEMAP_PATTERN.matcher(body);
        final Set<URI> uris = new LinkedHashSet<>();
        final URI siteRootUri = buildSiteRootUri(baseUri);

        while (matcher.find()) {

            final String uriText = matcher.group(1).trim();
            if (uriText.isBlank()) {
                continue;
            }

            try {

                final URI resolved = siteRootUri.resolve(uriText);
                if (resolved.getScheme() != null && resolved.getHost() != null) {
                    uris.add(resolved);
                }
            } catch (final Exception exception) {
                LOGGER.debug("Ignoring malformed Sitemap URI: {}", uriText, exception);
            }
        }

        return List.copyOf(uris);
    }


    private URI buildSiteRootUri(final URI baseUri) {
        try {
            return new URI(
                    baseUri.getScheme(),
                    baseUri.getUserInfo(),
                    baseUri.getHost(),
                    baseUri.getPort(),
                    "/",
                    null,
                    null
            );
        } catch (final Exception exception) {
            throw new IllegalArgumentException("Could not build site root URI from: " + baseUri, exception);
        }
    }
}
