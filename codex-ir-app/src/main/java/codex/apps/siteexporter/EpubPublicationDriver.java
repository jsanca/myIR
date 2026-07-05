package codex.apps.siteexporter;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * {@link PublicationDriver} that produces an EPUB 3 publication from a site mirror.
 *
 * <p>Each {@link MirrorStatus#SUCCESS} page becomes one EPUB chapter. Text is
 * extracted via {@link ReadablePageExtractor}: pdf2htmlEX pages use the
 * coordinate-based reader pipeline; normal HTML pages use Jsoup paragraph
 * extraction. No images or CSS from the original mirror are included.</p>
 *
 * <p>The EPUB is assembled as a ZIP file using {@code java.util.zip} — no
 * third-party EPUB library is required.</p>
 *
 * <p>EPUB structure written:</p>
 * <pre>
 * mimetype                          (STORED, no compression — required by spec)
 * META-INF/container.xml
 * OEBPS/content.opf
 * OEBPS/nav.xhtml
 * OEBPS/chapter-001.xhtml
 * OEBPS/chapter-002.xhtml
 * ...
 * </pre>
 */
public final class EpubPublicationDriver implements PublicationDriver {

    private final Path outputDir;
    private final ReadablePageExtractor pageExtractor = new ReadablePageExtractor();
    private final PublicationOrderingStrategy orderingStrategy =
            new DiscoveredOrderPublicationOrderingStrategy();

    /**
     * @param outputDir mirror output directory; used to resolve relative HTML paths;
     *                  must not be {@code null}
     */
    public EpubPublicationDriver(final Path outputDir) {
        this.outputDir = Objects.requireNonNull(outputDir, "outputDir");
    }

    @Override
    public boolean requiresAssetProcessing() {
        return false;
    }

    @Override
    public PublicationArtifact publish(final PublicationSource source,
            final PublicationExportOptions options) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(options, "options");

        final List<MirroredPage> pages = orderingStrategy.order(source.manifest());
        final String bookTitle = deriveTitle(source.manifest());

        final List<EpubChapter> chapters = new ArrayList<>(pages.size());
        final List<AssemblyReport.RenderFailure> failures = new ArrayList<>();

        for (int i = 0; i < pages.size(); i++) {
            final MirroredPage page = pages.get(i);
            final Path htmlFile = source.contentDir().resolve(page.localHtmlPath());
            try {
                final ReaderDocument doc = pageExtractor.extract(htmlFile);
                final String id = String.format("chapter-%03d", i + 1);
                final String chapterTitle = deriveChapterTitle(page, i + 1, doc);
                chapters.add(new EpubChapter(id, chapterTitle, buildChapterXhtml(chapterTitle, doc)));
            } catch (final IOException e) {
                final String url = page.url() != null ? page.url().toString()
                        : htmlFile.toString();
                System.err.printf("[EpubDriver][WARN] Extraction failed for %s: %s%n",
                        url, e.getMessage());
                failures.add(new AssemblyReport.RenderFailure(url, e.getMessage()));
            }
        }

        final Path outputPath = options.outputPath();
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        writeEpub(outputPath, bookTitle, chapters);

        final long sizeBytes = Files.size(outputPath);
        final AssemblyReport report = new AssemblyReport(
                pages.size(), chapters.size(), failures.size(), sizeBytes, failures);

        System.out.printf("[EpubDriver] Artifact written → %s (%d bytes, %d/%d chapters, %d failed)%n",
                outputPath, sizeBytes, chapters.size(), pages.size(), failures.size());

        return new PublicationArtifact(outputPath, PublicationFormat.EPUB, sizeBytes,
                Instant.now(), report);
    }

    // ------------------------------------------------------------------
    // EPUB ZIP assembly
    // ------------------------------------------------------------------

    private static void writeEpub(final Path output, final String title,
            final List<EpubChapter> chapters) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(output)))) {

            // mimetype must be the FIRST entry and STORED (uncompressed) per EPUB spec
            final byte[] mimeBytes = "application/epub+zip".getBytes(StandardCharsets.UTF_8);
            final ZipEntry mimeEntry = new ZipEntry("mimetype");
            mimeEntry.setMethod(ZipEntry.STORED);
            mimeEntry.setSize(mimeBytes.length);
            mimeEntry.setCompressedSize(mimeBytes.length);
            mimeEntry.setCrc(crc32(mimeBytes));
            zos.putNextEntry(mimeEntry);
            zos.write(mimeBytes);
            zos.closeEntry();

            zos.setMethod(ZipOutputStream.DEFLATED);
            addEntry(zos, "META-INF/container.xml", containerXml());
            addEntry(zos, "OEBPS/content.opf", contentOpf(title, chapters));
            addEntry(zos, "OEBPS/nav.xhtml", navXhtml(title, chapters));
            for (final EpubChapter ch : chapters) {
                addEntry(zos, "OEBPS/" + ch.id() + ".xhtml", ch.xhtml());
            }
        }
    }

    private static void addEntry(final ZipOutputStream zos, final String name,
            final String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static long crc32(final byte[] bytes) {
        final CRC32 crc = new CRC32();
        crc.update(bytes);
        return crc.getValue();
    }

    // ------------------------------------------------------------------
    // XML / XHTML content builders
    // ------------------------------------------------------------------

    private static String containerXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf"\
                 media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """;
    }

    private static String contentOpf(final String title, final List<EpubChapter> chapters) {
        final String uid = "urn:uuid:" + UUID.randomUUID();
        final String modified = Instant.now().toString().replaceFirst("\\.\\d+Z$", "Z");

        final StringBuilder manifest = new StringBuilder();
        manifest.append("    <item id=\"nav\" href=\"nav.xhtml\"")
                .append(" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n");
        for (final EpubChapter ch : chapters) {
            manifest.append("    <item id=\"").append(ch.id()).append("\" href=\"")
                    .append(ch.id()).append(".xhtml\" media-type=\"application/xhtml+xml\"/>\n");
        }

        final StringBuilder spine = new StringBuilder();
        for (final EpubChapter ch : chapters) {
            spine.append("    <itemref idref=\"").append(ch.id()).append("\"/>\n");
        }

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<package version=\"3.0\" xmlns=\"http://www.idpf.org/2007/opf\""
                + " unique-identifier=\"uid\" dir=\"ltr\">\n"
                + "  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n"
                + "    <dc:title>" + escapeXml(title) + "</dc:title>\n"
                + "    <dc:identifier id=\"uid\">" + uid + "</dc:identifier>\n"
                + "    <dc:language>en</dc:language>\n"
                + "    <meta property=\"dcterms:modified\">" + modified + "</meta>\n"
                + "  </metadata>\n"
                + "  <manifest>\n" + manifest
                + "  </manifest>\n"
                + "  <spine>\n" + spine
                + "  </spine>\n"
                + "</package>\n";
    }

    private static String navXhtml(final String title, final List<EpubChapter> chapters) {
        final StringBuilder items = new StringBuilder();
        for (final EpubChapter ch : chapters) {
            items.append("      <li><a href=\"").append(ch.id()).append(".xhtml\">")
                    .append(escapeXml(ch.title())).append("</a></li>\n");
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE html>\n"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\""
                + " xmlns:epub=\"http://www.idpf.org/2007/ops\" lang=\"en\">\n"
                + "<head><meta charset=\"UTF-8\"/><title>"
                + escapeXml(title) + "</title></head>\n"
                + "<body>\n"
                + "  <nav epub:type=\"toc\" id=\"toc\">\n"
                + "    <h1>" + escapeXml(title) + "</h1>\n"
                + "    <ol>\n" + items
                + "    </ol>\n"
                + "  </nav>\n"
                + "</body>\n"
                + "</html>\n";
    }

    private static String buildChapterXhtml(final String title, final ReaderDocument doc) {
        final StringBuilder body = new StringBuilder();
        body.append("<h1>").append(escapeXml(title)).append("</h1>\n");
        for (final ReaderPage page : doc.pages()) {
            for (final String para : page.paragraphs()) {
                if (!para.isBlank()) {
                    body.append("<p>").append(escapeXml(para)).append("</p>\n");
                }
            }
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE html>\n"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"en\">\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\"/>\n"
                + "  <title>" + escapeXml(title) + "</title>\n"
                + "  <style>body{font-family:Georgia,serif;line-height:1.6;}"
                + "p{margin:0.5em 0 1em;}</style>\n"
                + "</head>\n"
                + "<body>\n" + body
                + "</body>\n"
                + "</html>\n";
    }

    private static String escapeXml(final String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
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

    private static String deriveChapterTitle(final MirroredPage page, final int index,
            final ReaderDocument doc) {
        if (!doc.title().equals("Document") && !doc.title().isBlank()) {
            return doc.title();
        }
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
        return "Chapter " + index;
    }

    // ------------------------------------------------------------------
    // Internal record
    // ------------------------------------------------------------------

    private record EpubChapter(String id, String title, String xhtml) {}
}
