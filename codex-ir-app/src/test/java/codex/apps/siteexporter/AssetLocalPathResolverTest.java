package codex.apps.siteexporter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AssetLocalPathResolverTest {

    @Test
    void shouldResolveImageUrlToPathUnderAssetsSubdir(@TempDir final Path tempDir) {
        final AssetLocalPathResolver resolver = new AssetLocalPathResolver(tempDir);

        final Path resolved = resolver.resolve(URI.create("https://example.com/img/logo.png"));

        assertTrue(resolved.toString().contains("assets"));
        assertTrue(resolved.toString().endsWith("logo.png"));
    }

    @Test
    void shouldRelativizeResolvedPathToOutputDir(@TempDir final Path tempDir) {
        final AssetLocalPathResolver resolver = new AssetLocalPathResolver(tempDir);

        final Path resolved = resolver.resolve(URI.create("https://example.com/img/logo.png"));
        final String relative = resolver.relativize(resolved);

        assertEquals("assets/img/logo.png", relative);
    }

    @Test
    void shouldRelativizeCssPath(@TempDir final Path tempDir) {
        final AssetLocalPathResolver resolver = new AssetLocalPathResolver(tempDir);

        final Path resolved = resolver.resolve(URI.create("https://example.com/css/style.css"));
        final String relative = resolver.relativize(resolved);

        assertEquals("assets/css/style.css", relative);
    }

    @Test
    void shouldRelativizeScriptPath(@TempDir final Path tempDir) {
        final AssetLocalPathResolver resolver = new AssetLocalPathResolver(tempDir);

        final Path resolved = resolver.resolve(URI.create("https://example.com/js/app.js"));
        final String relative = resolver.relativize(resolved);

        assertEquals("assets/js/app.js", relative);
    }

    @Test
    void shouldUseAssetsAsPrefix(@TempDir final Path tempDir) {
        final AssetLocalPathResolver resolver = new AssetLocalPathResolver(tempDir);

        final Path resolved = resolver.resolve(URI.create("https://example.com/deep/nested/file.png"));
        final String relative = resolver.relativize(resolved);

        assertTrue(relative.startsWith("assets/"), "relative path must start with assets/");
    }

    @Test
    void dotDotSegmentsShouldBeStrippedNotEscapeOutputDir(@TempDir final Path tempDir) {
        final AssetLocalPathResolver resolver = new AssetLocalPathResolver(tempDir);

        // '..' segments are stripped by sanitization, so the path stays safely within outputDir/assets/
        final Path resolved = resolver.resolve(URI.create("https://example.com/../../../etc/passwd"));
        final String relative = resolver.relativize(resolved);

        assertTrue(relative.startsWith("assets/"), "path must remain within outputDir/assets/");
        assertFalse(relative.contains(".."), "sanitized path must not contain '..'");
    }

    @Test
    void shouldSanitizeDotDotSegmentsInUrl(@TempDir final Path tempDir) {
        final AssetLocalPathResolver resolver = new AssetLocalPathResolver(tempDir);

        // after getPath() decoding, ..'s are stripped — the resolved path stays inside outputDir
        final Path resolved = resolver.resolve(URI.create("https://example.com/img/../logo.png"));
        final String relative = resolver.relativize(resolved);

        // '..' stripped so path collapses to assets/logo.png
        assertTrue(relative.startsWith("assets/"));
        assertFalse(relative.contains(".."));
    }

    @Test
    void useForwardSlashOnAllPlatforms(@TempDir final Path tempDir) {
        final AssetLocalPathResolver resolver = new AssetLocalPathResolver(tempDir);

        final Path resolved = resolver.resolve(URI.create("https://example.com/a/b/c.png"));
        final String relative = resolver.relativize(resolved);

        assertFalse(relative.contains("\\"), "path separator must be forward slash");
    }
}
