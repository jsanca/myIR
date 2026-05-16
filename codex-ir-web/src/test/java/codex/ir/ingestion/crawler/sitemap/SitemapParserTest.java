package codex.ir.ingestion.crawler.sitemap;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SitemapParserTest {

    private static final URI SOURCE_URI = URI.create("https://example.com/sitemap.xml");
    private final SitemapParser parser = new SitemapParser();

    @Test
    void shouldParseSimpleUrlset() {
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                    <url>
                        <loc>https://example.com/page1</loc>
                    </url>
                    <url>
                        <loc>https://example.com/page2</loc>
                    </url>
                    <url>
                        <loc>https://example.com/page3</loc>
                    </url>
                </urlset>""";

        final SitemapEntries entries = parser.parse(xml, SOURCE_URI);

        assertEquals(3, entries.urlEntries().size());
        assertTrue(entries.sitemapRefs().isEmpty());
        assertEquals(URI.create("https://example.com/page1"), entries.urlEntries().get(0).loc());
        assertEquals(URI.create("https://example.com/page2"), entries.urlEntries().get(1).loc());
        assertEquals(URI.create("https://example.com/page3"), entries.urlEntries().get(2).loc());
    }

    @Test
    void shouldParseUrlsetWithLastmod() {
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                    <url>
                        <loc>https://example.com/page1</loc>
                        <lastmod>2024-01-15T10:30:00Z</lastmod>
                    </url>
                </urlset>""";

        final SitemapEntries entries = parser.parse(xml, SOURCE_URI);

        assertEquals(1, entries.urlEntries().size());
        assertEquals(URI.create("https://example.com/page1"), entries.urlEntries().get(0).loc());
        assertTrue(entries.urlEntries().get(0).lastModified().isPresent());
    }

    @Test
    void shouldParseSitemapIndex() {
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                    <sitemap>
                        <loc>https://example.com/sitemap-posts.xml</loc>
                    </sitemap>
                    <sitemap>
                        <loc>https://example.com/sitemap-pages.xml</loc>
                    </sitemap>
                </sitemapindex>""";

        final SitemapEntries entries = parser.parse(xml, SOURCE_URI);

        assertTrue(entries.urlEntries().isEmpty());
        assertEquals(2, entries.sitemapRefs().size());
        assertEquals(URI.create("https://example.com/sitemap-posts.xml"), entries.sitemapRefs().get(0).loc());
        assertEquals(URI.create("https://example.com/sitemap-pages.xml"), entries.sitemapRefs().get(1).loc());
    }

    @Test
    void shouldResolveRelativeUrls() {
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                    <url>
                        <loc>/page1</loc>
                    </url>
                </urlset>""";

        final SitemapEntries entries = parser.parse(xml, SOURCE_URI);

        assertEquals(1, entries.urlEntries().size());
        assertEquals(URI.create("https://example.com/page1"), entries.urlEntries().get(0).loc());
    }

    @Test
    void shouldIgnoreUrlWithBlankLoc() {
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                    <url>
                        <loc>https://example.com/valid</loc>
                    </url>
                    <url>
                        <loc>   </loc>
                    </url>
                    <url>
                        <loc></loc>
                    </url>
                </urlset>""";

        final SitemapEntries entries = parser.parse(xml, SOURCE_URI);

        assertEquals(1, entries.urlEntries().size());
        assertEquals(URI.create("https://example.com/valid"), entries.urlEntries().get(0).loc());
    }

    @Test
    void shouldIgnoreUrlWithMissingLoc() {
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                    <url>
                        <loc>https://example.com/valid</loc>
                    </url>
                    <url>
                        <lastmod>2024-01-15T10:30:00Z</lastmod>
                    </url>
                </urlset>""";

        final SitemapEntries entries = parser.parse(xml, SOURCE_URI);

        assertEquals(1, entries.urlEntries().size());
    }

    @Test
    void shouldHandleMalformedXml() {
        final SitemapEntries entries = parser.parse("not valid xml", SOURCE_URI);

        assertTrue(entries.isEmpty());
    }

    @Test
    void shouldHandleNullInput() {
        final SitemapEntries entries = parser.parse(null, SOURCE_URI);

        assertTrue(entries.isEmpty());
    }

    @Test
    void shouldHandleBlankInput() {
        final SitemapEntries entries = parser.parse("   ", SOURCE_URI);

        assertTrue(entries.isEmpty());
    }

    @Test
    void shouldHandleUnknownRootElement() {
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <unknown xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                </unknown>""";

        final SitemapEntries entries = parser.parse(xml, SOURCE_URI);

        assertTrue(entries.isEmpty());
    }

    @Test
    void shouldParseNestedSitemapIndexWithUrlsets() {
        final String indexXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                    <sitemap>
                        <loc>https://example.com/sitemap-products.xml</loc>
                    </sitemap>
                </sitemapindex>""";

        final SitemapEntries indexEntries = parser.parse(indexXml, SOURCE_URI);

        assertEquals(1, indexEntries.sitemapRefs().size());
        assertEquals(URI.create("https://example.com/sitemap-products.xml"),
                indexEntries.sitemapRefs().get(0).loc());
    }

    @Test
    void shouldDeduplicateUrlsWhenMultipleUrlEntriesExist() {
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                    <url>
                        <loc>https://example.com/page1</loc>
                    </url>
                    <url>
                        <loc>https://example.com/page1</loc>
                    </url>
                    <url>
                        <loc>https://example.com/page2</loc>
                    </url>
                </urlset>""";

        final SitemapEntries entries = parser.parse(xml, SOURCE_URI);

        assertEquals(3, entries.urlEntries().size(),
                "Parser preserves duplicate entries; deduplication happens at the strategy level");
    }

    @Test
    void shouldIgnoreMalformedLoc() {
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                    <url>
                        <loc>not-a-uri:##</loc>
                    </url>
                    <url>
                        <loc>https://example.com/valid</loc>
                    </url>
                </urlset>""";

        final SitemapEntries entries = parser.parse(xml, SOURCE_URI);

        assertEquals(1, entries.urlEntries().size());
        assertEquals(URI.create("https://example.com/valid"), entries.urlEntries().get(0).loc());
    }
}
