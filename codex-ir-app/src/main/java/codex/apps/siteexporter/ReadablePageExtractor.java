package codex.apps.siteexporter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Extracts readable text content from a local HTML file, returning a
 * {@link ReaderDocument} regardless of the source format.
 *
 * <p>Routing:</p>
 * <ul>
 *   <li>pdf2htmlEX pages → {@link Pdf2HtmlExTextExtractor} (CSS coordinate-based
 *       span sorting and paragraph grouping across multiple page frames).</li>
 *   <li>Normal HTML pages → Jsoup-based extraction of {@code h1–h6}, {@code p},
 *       and {@code li} elements collapsed into a single-page
 *       {@link ReaderDocument}.</li>
 * </ul>
 *
 * <p>Shared by {@link MarkdownPublicationWriter} and {@link EpubPublicationDriver}
 * so that text-extraction logic is not duplicated across format drivers.</p>
 */
public final class ReadablePageExtractor {

    private final Pdf2HtmlExDetector detector = new Pdf2HtmlExDetector();
    private final Pdf2HtmlExTextExtractor pdf2HtmlExtractor = new Pdf2HtmlExTextExtractor();

    /**
     * Reads and extracts readable content from {@code htmlFile}.
     *
     * @param htmlFile local HTML file to read; must not be {@code null}
     * @return extracted reader document; never {@code null}
     * @throws IOException if the file cannot be read
     */
    public ReaderDocument extract(final Path htmlFile) throws IOException {
        Objects.requireNonNull(htmlFile, "htmlFile");
        final String rawHtml = Files.readString(htmlFile);
        final String baseUri = htmlFile.toAbsolutePath().getParent().toUri().toString();

        if (detector.detect(rawHtml, baseUri)) {
            return pdf2HtmlExtractor.extract(rawHtml, baseUri);
        }
        return extractNormalHtml(rawHtml, baseUri);
    }

    // ------------------------------------------------------------------
    // Normal HTML extraction (package-visible for testing)
    // ------------------------------------------------------------------

    static ReaderDocument extractNormalHtml(final String rawHtml, final String baseUri) {
        final Document doc = Jsoup.parse(rawHtml, baseUri);
        final String title = doc.title().isBlank() ? "Document" : doc.title();

        final List<String> paragraphs = new ArrayList<>();
        final Element body = doc.body();
        if (body != null) {
            final Elements elements = body.select("h1, h2, h3, h4, h5, h6, p, li");
            if (elements.isEmpty()) {
                final String text = body.text().trim();
                if (!text.isBlank()) {
                    paragraphs.add(text);
                }
            } else {
                for (final Element el : elements) {
                    final String text = el.text().trim();
                    if (!text.isBlank()) {
                        paragraphs.add(text);
                    }
                }
            }
        }

        if (paragraphs.isEmpty()) {
            return ReaderDocument.empty(title);
        }
        return new ReaderDocument(title, List.of(new ReaderPage(1, paragraphs)));
    }
}
