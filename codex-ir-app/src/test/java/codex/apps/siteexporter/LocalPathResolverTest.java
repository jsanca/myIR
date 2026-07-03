package codex.apps.siteexporter;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalPathResolverTest {

    private static final Path BASE = Path.of("/mirror");
    private final LocalPathResolver resolver = new LocalPathResolver(BASE);

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
}
