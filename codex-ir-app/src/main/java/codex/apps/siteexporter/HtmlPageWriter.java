package codex.apps.siteexporter;

import codex.ir.ingestion.WebPage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Writes the raw HTML of a {@link WebPage} to a local file resolved by a
 * {@link LocalPathResolver}.
 *
 * <p>Creates any missing parent directories before writing.</p>
 */
public final class HtmlPageWriter {

    private final LocalPathResolver resolver;

    public HtmlPageWriter(final LocalPathResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /**
     * Writes {@code page.rawHtml()} to the resolved local path.
     *
     * @param page the web page to write; must not be {@code null}
     * @return the path that was written
     * @throws IOException if the file cannot be created or written
     */
    public Path write(final WebPage page) throws IOException {
        Objects.requireNonNull(page, "page");
        final Path localPath = resolver.resolve(page.url());
        Files.createDirectories(localPath.getParent());
        final String html = page.rawHtml() != null ? page.rawHtml() : "";
        Files.writeString(localPath, html, StandardCharsets.UTF_8);
        return localPath;
    }
}
