package codex.apps.siteexporter;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalPathResolverTest {

    private static final Path BASE = Path.of("/mirror");
    private final LocalPathResolver resolver = new LocalPathResolver(BASE);

    // ------------------------------------------------------------------
    // Basic mapping rules
    // ------------------------------------------------------------------

    @Test
    void rootUriShouldResolveToIndexHtml() {
        assertEquals(BASE.resolve("index.html"), resolver.resolve(URI.create("https://example.com/")));
    }

    @Test
    void emptyPathShouldResolveToIndexHtml() {
        assertEquals(BASE.resolve("index.html"), resolver.resolve(URI.create("https://example.com")));
    }

    @Test
    void pathWithTrailingSlashShouldAppendIndexHtml() {
        assertEquals(BASE.resolve("products/index.html"), resolver.resolve(URI.create("https://example.com/products/")));
    }

    @Test
    void pathWithNoExtensionShouldBecomeDirectoryIndex() {
        assertEquals(BASE.resolve("about/index.html"), resolver.resolve(URI.create("https://example.com/about")));
    }

    @Test
    void nestedPathWithNoExtensionShouldBecomeDirectoryIndex() {
        assertEquals(BASE.resolve("blog/post-one/index.html"), resolver.resolve(URI.create("https://example.com/blog/post-one")));
    }

    @Test
    void pathWithExtensionShouldBeUsedVerbatim() {
        assertEquals(BASE.resolve("style.css"), resolver.resolve(URI.create("https://example.com/style.css")));
    }

    @Test
    void htmlExtensionShouldBeUsedVerbatim() {
        assertEquals(BASE.resolve("about.html"), resolver.resolve(URI.create("https://example.com/about.html")));
    }

    @Test
    void deepNestedPathWithExtensionShouldBeUsedVerbatim() {
        assertEquals(BASE.resolve("assets/img/logo.png"), resolver.resolve(URI.create("https://example.com/assets/img/logo.png")));
    }

    // ------------------------------------------------------------------
    // Segment sanitization — dot and dot-dot
    // ------------------------------------------------------------------

    @Test
    void dotDotSegmentsShouldBeStripped() {
        // /../../../etc/passwd → all '..' stripped → etc/passwd/index.html
        final Path resolved = resolver.resolve(URI.create("https://example.com/../../etc/passwd"));
        assertTrue(resolved.startsWith(BASE.toAbsolutePath()),
                "Resolved path must remain within outputDir");
        assertEquals(BASE.resolve("etc/passwd/index.html"), resolved);
    }

    @Test
    void dotSegmentsShouldBeStripped() {
        assertEquals(BASE.resolve("about/index.html"),
                resolver.resolve(URI.create("https://example.com/./about")));
    }

    @Test
    void pathConsistingOnlyOfDotDotShouldFallBackToIndex() {
        final Path resolved = resolver.resolve(URI.create("https://example.com/../.."));
        assertTrue(resolved.startsWith(BASE.toAbsolutePath()),
                "Resolved path must remain within outputDir");
        assertEquals(BASE.resolve("index.html"), resolved);
    }

    // ------------------------------------------------------------------
    // Segment sanitization — filesystem-unsafe characters
    // ------------------------------------------------------------------

    @Test
    void colonInSegmentShouldBeReplaced() {
        final Path resolved = resolver.resolve(URI.create("https://example.com/path:name"));
        assertEquals(BASE.resolve("path_name/index.html"), resolved);
    }

    @Test
    void encodedBackslashInSegmentShouldBeReplaced() {
        // %5C decodes to backslash via URI.getPath(); backslash is a path separator on Windows
        final Path resolved = resolver.resolve(URI.create("https://example.com/%5Cbackslash"));
        assertTrue(resolved.toAbsolutePath().startsWith(BASE.toAbsolutePath()),
                "Resolved path must remain within outputDir");
        assertFalse(resolved.toString().contains("\\"), "Backslash must be replaced");
    }

    // ------------------------------------------------------------------
    // Traversal guard — resolved path must always be within outputDir
    // ------------------------------------------------------------------

    @Test
    void resolvedPathShouldAlwaysBeWithinOutputDir() {
        final URI malicious = URI.create("https://example.com/%2e%2e%2fetc%2fpasswd");
        final Path resolved = resolver.resolve(malicious);
        assertTrue(resolved.toAbsolutePath().normalize().startsWith(BASE.toAbsolutePath().normalize()),
                "Resolved path must stay within outputDir, got: " + resolved);
    }

    @Test
    void deepTraversalShouldNotEscapeOutputDir() {
        final Path resolved = resolver.resolve(
                URI.create("https://example.com/a/b/../../../../../../../../etc/shadow"));
        assertTrue(resolved.toAbsolutePath().normalize().startsWith(BASE.toAbsolutePath().normalize()),
                "Resolved path must stay within outputDir, got: " + resolved);
    }
}
