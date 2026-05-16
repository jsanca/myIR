package codex.ir.ingestion.crawler.sitemap;

import codex.ir.ingestion.crawler.fetcher.WebHttpFetcher;
import codex.ir.ingestion.crawler.fetcher.WebHttpResponse;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RobotsParserTest {

    private static final URI BASE_URI = URI.create("https://example.com");

    @Test
    void shouldDiscoverSingleSitemapFromRobotsTxt() {
        final WebHttpFetcher fetcher = uri -> new WebHttpResponse(
                uri, 200,
                "Sitemap: https://example.com/sitemap.xml",
                "text/plain", java.util.Map.of()
        );
        final RobotsParser parser = new RobotsParser(fetcher);

        final List<URI> uris = parser.discoverSitemapUris(BASE_URI);

        assertEquals(1, uris.size());
        assertEquals(URI.create("https://example.com/sitemap.xml"), uris.get(0));
    }

    @Test
    void shouldDiscoverMultipleSitemapDirectives() {
        final WebHttpFetcher fetcher = uri -> new WebHttpResponse(
                uri, 200,
                """
                User-agent: *
                Disallow: /admin/
                Sitemap: https://example.com/sitemap-posts.xml
                Sitemap: https://example.com/sitemap-pages.xml
                """,
                "text/plain", java.util.Map.of()
        );
        final RobotsParser parser = new RobotsParser(fetcher);

        final List<URI> uris = parser.discoverSitemapUris(BASE_URI);

        assertEquals(2, uris.size());
        assertEquals(URI.create("https://example.com/sitemap-posts.xml"), uris.get(0));
        assertEquals(URI.create("https://example.com/sitemap-pages.xml"), uris.get(1));
    }

    @Test
    void shouldHandleCaseInsensitiveSitemapDirective() {
        final WebHttpFetcher fetcher = uri -> new WebHttpResponse(
                uri, 200,
                "SITEMAP: https://example.com/sitemap.xml",
                "text/plain", java.util.Map.of()
        );
        final RobotsParser parser = new RobotsParser(fetcher);

        final List<URI> uris = parser.discoverSitemapUris(BASE_URI);

        assertEquals(1, uris.size());
    }

    @Test
    void shouldHandleMixedCaseSitemapDirective() {
        final WebHttpFetcher fetcher = uri -> new WebHttpResponse(
                uri, 200,
                "SiteMap: https://example.com/sitemap.xml",
                "text/plain", java.util.Map.of()
        );
        final RobotsParser parser = new RobotsParser(fetcher);

        final List<URI> uris = parser.discoverSitemapUris(BASE_URI);

        assertEquals(1, uris.size());
    }

    @Test
    void shouldHandleWhitespaceAroundSitemapDirective() {
        final WebHttpFetcher fetcher = uri -> new WebHttpResponse(
                uri, 200,
                "  Sitemap:   https://example.com/sitemap.xml  ",
                "text/plain", java.util.Map.of()
        );
        final RobotsParser parser = new RobotsParser(fetcher);

        final List<URI> uris = parser.discoverSitemapUris(BASE_URI);

        assertEquals(1, uris.size());
        assertEquals(URI.create("https://example.com/sitemap.xml"), uris.get(0));
    }

    @Test
    void shouldReturnEmptyWhenNoSitemapDirective() {
        final WebHttpFetcher fetcher = uri -> new WebHttpResponse(
                uri, 200,
                """
                User-agent: *
                Disallow: /admin/
                Disallow: /wp-admin/
                """,
                "text/plain", java.util.Map.of()
        );
        final RobotsParser parser = new RobotsParser(fetcher);

        final List<URI> uris = parser.discoverSitemapUris(BASE_URI);

        assertTrue(uris.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenRobotsTxtIsEmpty() {
        final WebHttpFetcher fetcher = uri -> new WebHttpResponse(
                uri, 200,
                "",
                "text/plain", java.util.Map.of()
        );
        final RobotsParser parser = new RobotsParser(fetcher);

        final List<URI> uris = parser.discoverSitemapUris(BASE_URI);

        assertTrue(uris.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenRobotsTxtReturnsNon200() {
        final WebHttpFetcher fetcher = uri -> new WebHttpResponse(
                uri, 404,
                "Not Found",
                "text/plain", java.util.Map.of()
        );
        final RobotsParser parser = new RobotsParser(fetcher);

        final List<URI> uris = parser.discoverSitemapUris(BASE_URI);

        assertTrue(uris.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenRobotsTxtReturns500() {
        final WebHttpFetcher fetcher = uri -> new WebHttpResponse(
                uri, 500,
                "Internal Server Error",
                "text/plain", java.util.Map.of()
        );
        final RobotsParser parser = new RobotsParser(fetcher);

        final List<URI> uris = parser.discoverSitemapUris(BASE_URI);

        assertTrue(uris.isEmpty());
    }

    @Test
    void shouldHandleExceptionFromFetcher() {
        final WebHttpFetcher fetcher = uri -> {
            throw new IllegalStateException("Connection refused");
        };
        final RobotsParser parser = new RobotsParser(fetcher);

        final List<URI> uris = parser.discoverSitemapUris(BASE_URI);

        assertTrue(uris.isEmpty());
    }

    @Test
    void shouldResolveRelativeSitemapUri() {
        final WebHttpFetcher fetcher = uri -> new WebHttpResponse(
                uri, 200,
                "Sitemap: /custom-sitemap.xml",
                "text/plain", java.util.Map.of()
        );
        final RobotsParser parser = new RobotsParser(fetcher);

        final List<URI> uris = parser.discoverSitemapUris(BASE_URI);

        assertEquals(1, uris.size());
        assertEquals(URI.create("https://example.com/custom-sitemap.xml"), uris.get(0));
    }

    @Test
    void shouldFetchFromCorrectRobotsTxtUri() {
        final URI[] fetchedUri = new URI[1];
        final WebHttpFetcher fetcher = uri -> {
            fetchedUri[0] = uri;
            return new WebHttpResponse(uri, 200, "", "text/plain", java.util.Map.of());
        };
        final RobotsParser parser = new RobotsParser(fetcher);

        parser.discoverSitemapUris(URI.create("https://example.com:8080/blog/"));

        assertEquals(URI.create("https://example.com:8080/robots.txt"), fetchedUri[0],
                "Expected robots.txt to be fetched from the root of the host, ignoring path");
    }

    @Test
    void shouldIgnoreMalformedSitemapUri() {
        final WebHttpFetcher fetcher = uri -> new WebHttpResponse(
                uri, 200,
                """
                Sitemap: https://example.com/valid-sitemap.xml
                Sitemap: not-a-valid-uri:##
                """,
                "text/plain", java.util.Map.of()
        );
        final RobotsParser parser = new RobotsParser(fetcher);

        final List<URI> uris = parser.discoverSitemapUris(BASE_URI);

        assertEquals(1, uris.size());
        assertEquals(URI.create("https://example.com/valid-sitemap.xml"), uris.get(0));
    }
}
