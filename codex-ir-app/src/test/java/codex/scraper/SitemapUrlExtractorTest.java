package codex.scraper;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SitemapUrlExtractorTest {

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static SitemapUrlExtractor extractorFor(final Map<URI, String> fixtures) {
        return new SitemapUrlExtractor(uri ->
                Optional.ofNullable(fixtures.get(uri))
                        .map(xml -> (InputStream) new ByteArrayInputStream(
                                xml.getBytes(StandardCharsets.UTF_8))));
    }

    private static String urlset(final String... locs) {
        final StringBuilder sb = new StringBuilder(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (final String loc : locs) {
            sb.append("  <url><loc>").append(loc).append("</loc></url>\n");
        }
        return sb.append("</urlset>").toString();
    }

    private static String sitemapIndex(final String... childLocs) {
        final StringBuilder sb = new StringBuilder(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (final String loc : childLocs) {
            sb.append("  <sitemap><loc>").append(loc).append("</loc></sitemap>\n");
        }
        return sb.append("</sitemapindex>").toString();
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    void shouldExtractUrlsFromFlatUrlset() {
        final URI sitemap = URI.create("https://example.com/sitemap.xml");
        final Map<URI, String> fixtures = Map.of(sitemap, urlset(
                "https://example.com/product/alpha",
                "https://example.com/product/beta",
                "https://example.com/category/shoes"
        ));

        final List<URI> urls = extractorFor(fixtures).extract(sitemap, 100);

        assertEquals(3, urls.size());
        assertTrue(urls.contains(URI.create("https://example.com/product/alpha")));
        assertTrue(urls.contains(URI.create("https://example.com/product/beta")));
        assertTrue(urls.contains(URI.create("https://example.com/category/shoes")));
    }

    @Test
    void shouldFollowSitemapIndexAndAggregateChildUrls() {
        final URI indexUri  = URI.create("https://example.com/sitemap-index.xml");
        final URI childA    = URI.create("https://example.com/sitemap-products.xml");
        final URI childB    = URI.create("https://example.com/sitemap-blog.xml");

        final Map<URI, String> fixtures = new HashMap<>();
        fixtures.put(indexUri, sitemapIndex(childA.toString(), childB.toString()));
        fixtures.put(childA, urlset(
                "https://example.com/product/1",
                "https://example.com/product/2"
        ));
        fixtures.put(childB, urlset(
                "https://example.com/blog/post-1"
        ));

        final List<URI> urls = extractorFor(fixtures).extract(indexUri, 100);

        assertEquals(3, urls.size());
        assertTrue(urls.contains(URI.create("https://example.com/product/1")));
        assertTrue(urls.contains(URI.create("https://example.com/product/2")));
        assertTrue(urls.contains(URI.create("https://example.com/blog/post-1")));
    }

    @Test
    void limitShouldCapTotalUrlsReturned() {
        final URI sitemap = URI.create("https://example.com/sitemap.xml");
        final Map<URI, String> fixtures = Map.of(sitemap, urlset(
                "https://example.com/page/1",
                "https://example.com/page/2",
                "https://example.com/page/3",
                "https://example.com/page/4",
                "https://example.com/page/5"
        ));

        final List<URI> urls = extractorFor(fixtures).extract(sitemap, 3);

        assertEquals(3, urls.size());
    }

    @Test
    void limitShouldCapAcrossChildSitemaps() {
        final URI indexUri = URI.create("https://example.com/sitemap-index.xml");
        final URI childA   = URI.create("https://example.com/sitemap-a.xml");
        final URI childB   = URI.create("https://example.com/sitemap-b.xml");

        final Map<URI, String> fixtures = new HashMap<>();
        fixtures.put(indexUri, sitemapIndex(childA.toString(), childB.toString()));
        fixtures.put(childA, urlset(
                "https://example.com/a/1",
                "https://example.com/a/2",
                "https://example.com/a/3"
        ));
        fixtures.put(childB, urlset(
                "https://example.com/b/1",
                "https://example.com/b/2"
        ));

        final List<URI> urls = extractorFor(fixtures).extract(indexUri, 4);

        assertEquals(4, urls.size());
    }

    @Test
    void shouldReturnEmptyForUnreachableSitemap() {
        final URI sitemap = URI.create("https://example.com/missing.xml");
        // no fixture → provider returns empty
        final List<URI> urls = extractorFor(Map.of()).extract(sitemap, 100);

        assertTrue(urls.isEmpty());
    }

    @Test
    void shouldReturnEmptyForMalformedXml() {
        final URI sitemap = URI.create("https://example.com/bad.xml");
        final Map<URI, String> fixtures = Map.of(sitemap, "this is not xml at all <<<");

        final List<URI> urls = extractorFor(fixtures).extract(sitemap, 100);

        assertTrue(urls.isEmpty(), "Malformed XML should produce an empty result, not throw");
    }

    @Test
    void shouldFilterOutNonHttpUrls() {
        final URI sitemap = URI.create("https://example.com/sitemap.xml");
        final Map<URI, String> fixtures = Map.of(sitemap, urlset(
                "https://example.com/page/valid",
                "ftp://example.com/file.zip",
                "mailto:info@example.com"
        ));

        final List<URI> urls = extractorFor(fixtures).extract(sitemap, 100);

        assertEquals(1, urls.size());
        assertEquals(URI.create("https://example.com/page/valid"), urls.get(0));
    }

    @Test
    void shouldFilterOutCrossDomainUrls() {
        final URI sitemap = URI.create("https://example.com/sitemap.xml");
        final Map<URI, String> fixtures = Map.of(sitemap, urlset(
                "https://example.com/page/local",
                "https://other-domain.com/page/external"
        ));

        final List<URI> urls = extractorFor(fixtures).extract(sitemap, 100);

        assertEquals(1, urls.size());
        assertEquals(URI.create("https://example.com/page/local"), urls.get(0));
    }
}
