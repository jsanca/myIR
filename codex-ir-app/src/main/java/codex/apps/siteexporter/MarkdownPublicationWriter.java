package codex.apps.siteexporter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Produces a Markdown publication artifact from a site mirror.
 *
 * <p>For each {@link MirrorStatus#SUCCESS} page in the manifest, ordered by
 * the supplied {@link PublicationOrderingStrategy}:</p>
 * <ul>
 *   <li>pdf2htmlEX pages are routed through {@link Pdf2HtmlExTextExtractor} to
 *       produce clean paragraph text.</li>
 *   <li>Normal HTML pages are processed by a Jsoup-based paragraph extractor.</li>
 * </ul>
 *
 * <p>Output layout (one combined {@code .md} file):</p>
 * <pre>
 * # Document Title
 *
 * &lt;!-- source: https://example.com/ --&gt;
 *
 * ---
 *
 * ## section-name
 *
 * &lt;!-- source: https://example.com/section --&gt;
 *
 * Paragraph text...
 * </pre>
 *
 * <p>When {@code markdownPagesDir} is configured on the builder, one {@code .md}
 * file per page is also written under that directory.</p>
 */
public final class MarkdownPublicationWriter {

    private final PublicationSource source;
    private final PublicationOrderingStrategy orderingStrategy;
    private final Path markdownPagesDir;

    private final Pdf2HtmlExDetector detector = new Pdf2HtmlExDetector();
    private final Pdf2HtmlExTextExtractor extractor = new Pdf2HtmlExTextExtractor();

    private MarkdownPublicationWriter(final PublicationSource source,
            final PublicationOrderingStrategy orderingStrategy,
            final Path markdownPagesDir) {
        this.source = Objects.requireNonNull(source, "source");
        this.orderingStrategy = Objects.requireNonNull(orderingStrategy, "orderingStrategy");
        this.markdownPagesDir = markdownPagesDir;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Extracts content from all ordered pages and writes the combined Markdown
     * artifact to {@code output}.
     *
     * @param output destination path for the combined {@code .md} file; must not be {@code null}
     * @return artifact metadata and assembly report; never {@code null}
     * @throws IOException if the output file cannot be written
     */
    public PublicationArtifact write(final Path output) throws IOException {
        Objects.requireNonNull(output, "output");

        final List<MirroredPage> pages = orderingStrategy.order(source.manifest());
        final String title = deriveTitle(source.manifest());

        final StringBuilder combined = new StringBuilder(4096);
        combined.append("# ").append(escapeMarkdown(title)).append("\n\n");
        combined.append("<!-- source: ").append(source.manifest().startUrl()).append(" -->\n\n");

        int pagesRendered = 0;
        final List<AssemblyReport.RenderFailure> failures = new ArrayList<>();

        for (int i = 0; i < pages.size(); i++) {
            final MirroredPage page = pages.get(i);
            final Path htmlFile = source.contentDir().resolve(page.localHtmlPath());

            try {
                final String pageMarkdown = extractPageMarkdown(htmlFile, page, i + 1);
                combined.append(pageMarkdown);
                pagesRendered++;

                if (markdownPagesDir != null) {
                    Files.createDirectories(markdownPagesDir);
                    Files.writeString(
                            markdownPagesDir.resolve(htmlFile.getFileName() + ".md"),
                            pageMarkdown, StandardCharsets.UTF_8);
                }
            } catch (final IOException e) {
                final String url = page.url() != null ? page.url().toString()
                        : htmlFile.toString();
                System.err.printf("[MarkdownWriter][WARN] Extraction failed for %s: %s%n",
                        url, e.getMessage());
                failures.add(new AssemblyReport.RenderFailure(url, e.getMessage()));
            }
        }

        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        final String markdown = combined.toString();
        Files.writeString(output, markdown, StandardCharsets.UTF_8);

        final long sizeBytes = markdown.getBytes(StandardCharsets.UTF_8).length;
        final AssemblyReport report = new AssemblyReport(
                pages.size(), pagesRendered, failures.size(), sizeBytes, failures);

        System.out.printf("[MarkdownWriter] Artifact written → %s (%d bytes, %d/%d pages, %d failed)%n",
                output, sizeBytes, pagesRendered, pages.size(), failures.size());

        return new PublicationArtifact(output, PublicationFormat.MARKDOWN, sizeBytes,
                Instant.now(), report);
    }

    // ------------------------------------------------------------------
    // Per-page extraction
    // ------------------------------------------------------------------

    private String extractPageMarkdown(final Path htmlFile, final MirroredPage page,
            final int sectionIndex) throws IOException {
        final String rawHtml = Files.readString(htmlFile);
        final String baseUri = htmlFile.toAbsolutePath().getParent().toUri().toString();

        final StringBuilder sb = new StringBuilder(1024);
        sb.append("---\n\n");
        sb.append("## ").append(escapeMarkdown(deriveSection(page, sectionIndex))).append("\n\n");
        if (page.url() != null) {
            sb.append("<!-- source: ").append(page.url()).append(" -->\n\n");
        }

        if (detector.detect(rawHtml, baseUri)) {
            final ReaderDocument doc = extractor.extract(rawHtml, baseUri);
            for (final ReaderPage rp : doc.pages()) {
                for (final String para : rp.paragraphs()) {
                    if (!para.isBlank()) {
                        sb.append(escapeMarkdown(para)).append("\n\n");
                    }
                }
            }
        } else {
            extractNormalHtmlMarkdown(Jsoup.parse(rawHtml, baseUri), sb);
        }

        return sb.toString();
    }

    private static void extractNormalHtmlMarkdown(final Document doc, final StringBuilder sb) {
        final Element body = doc.body();
        if (body == null) {
            return;
        }
        final Elements elements = body.select("h1, h2, h3, h4, h5, h6, p, li");
        if (elements.isEmpty()) {
            final String text = body.text().trim();
            if (!text.isBlank()) {
                sb.append(escapeMarkdown(text)).append("\n\n");
            }
            return;
        }
        for (final Element el : elements) {
            final String text = el.text().trim();
            if (!text.isBlank()) {
                sb.append(escapeMarkdown(text)).append("\n\n");
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String deriveTitle(final MirrorManifest manifest) {
        final URI startUrl = manifest.startUrl();
        if (startUrl == null) {
            return "Publication";
        }
        final String host = startUrl.getHost();
        return (host != null && !host.isBlank()) ? host : "Publication";
    }

    private static String deriveSection(final MirroredPage page, final int index) {
        if (page.url() != null) {
            final String path = page.url().getPath();
            if (path != null && !path.equals("/")) {
                final String[] parts = path.split("/");
                for (int i = parts.length - 1; i >= 0; i--) {
                    if (!parts[i].isBlank()) {
                        return parts[i];
                    }
                }
            }
        }
        return "Page " + index;
    }

    /**
     * Escapes characters that would create unintended Markdown structure in body text:
     * backslashes, bracket pairs (prevent unintended link syntax), and leading '#'
     * sequences that would be parsed as headings.
     */
    static String escapeMarkdown(final String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text
                .replace("\\", "\\\\")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replaceAll("(?m)^(#{1,6})(\\s)", "\\\\$1$2");
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    public static final class Builder {

        private PublicationSource source;
        private PublicationOrderingStrategy orderingStrategy =
                new DiscoveredOrderPublicationOrderingStrategy();
        private Path markdownPagesDir;

        private Builder() {}

        public Builder source(final PublicationSource source) {
            this.source = Objects.requireNonNull(source, "source");
            return this;
        }

        public Builder orderingStrategy(final PublicationOrderingStrategy orderingStrategy) {
            this.orderingStrategy = Objects.requireNonNull(orderingStrategy, "orderingStrategy");
            return this;
        }

        /** Directory to write per-page {@code .md} files; {@code null} disables per-page output. */
        public Builder markdownPagesDir(final Path markdownPagesDir) {
            this.markdownPagesDir = markdownPagesDir;
            return this;
        }

        public MarkdownPublicationWriter build() {
            Objects.requireNonNull(source, "source is required");
            return new MarkdownPublicationWriter(source, orderingStrategy, markdownPagesDir);
        }
    }
}
