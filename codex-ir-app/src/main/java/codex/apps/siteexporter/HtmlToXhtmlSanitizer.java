package codex.apps.siteexporter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Converts raw, potentially malformed HTML into well-formed XHTML suitable
 * for OpenHTMLToPDF, which requires XML-compliant input.
 *
 * <p>Handles the common failure modes found in mirrored real-world pages:</p>
 * <ul>
 *   <li>Void elements without self-closing slash ({@code <br>}, {@code <meta>},
 *       {@code <img>}, {@code <link>})</li>
 *   <li>Unclosed block elements ({@code <p>}, {@code <li>}, {@code <td>})</li>
 *   <li>Leading whitespace, BOM, or comments before the doctype</li>
 *   <li>Missing {@code <html>}/{@code <head>}/{@code <body>} structure</li>
 * </ul>
 *
 * <p>Jsoup parses the input as HTML5, then serializes it as XML (XHTML). The
 * {@code baseUri} is passed to Jsoup so that relative URLs in the document are
 * resolved correctly during parsing; it does not appear in the output string.</p>
 */
public class HtmlToXhtmlSanitizer {

    /**
     * Matches a single {@code @font-face} block whose body contains a {@code data:}
     * URI (i.e. an inlined base64 font). {@code @font-face} rules never contain
     * nested braces, so {@code [^}]*} is safe here.
     */
    private static final Pattern FONT_FACE_DATA_URI = Pattern.compile(
            "@font-face\\s*\\{[^}]*data:[^}]*\\}",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    /**
     * Parses {@code html} and returns a well-formed XHTML string.
     *
     * @param html    raw HTML content; must not be {@code null}
     * @param baseUri base URI for resolving relative links during parsing;
     *                must not be {@code null}
     * @return well-formed XHTML string; never {@code null}
     */
    public String sanitize(final String html, final String baseUri) {
        Objects.requireNonNull(html, "html");
        Objects.requireNonNull(baseUri, "baseUri");
        return toXhtml(Jsoup.parse(html, baseUri));
    }

    /**
     * Parses {@code html}, removes scripts and embedded base64 font declarations,
     * and returns well-formed XHTML suitable for PDF rendering.
     *
     * <p>Specifically:</p>
     * <ul>
     *   <li>All {@code <script>} elements are removed.</li>
     *   <li>{@code @font-face} blocks whose {@code src} uses a {@code data:} URI
     *       are stripped from every {@code <style>} element. If a style block becomes
     *       empty after stripping, the element is removed entirely.</li>
     * </ul>
     * <p>Semantic content — headings, paragraphs, links, images, tables, and
     * code blocks — is preserved.</p>
     *
     * @param html    raw HTML content; must not be {@code null}
     * @param baseUri base URI for resolving relative links during parsing;
     *                must not be {@code null}
     * @return well-formed print-optimized XHTML string; never {@code null}
     */
    public String sanitizeForPrint(final String html, final String baseUri) {
        Objects.requireNonNull(html, "html");
        Objects.requireNonNull(baseUri, "baseUri");

        final Document doc = Jsoup.parse(html, baseUri);

        doc.select("script").remove();

        for (final Element style : doc.select("style")) {
            final String stripped = FONT_FACE_DATA_URI.matcher(style.html()).replaceAll("");
            if (stripped.isBlank()) {
                style.remove();
            } else {
                style.html(stripped);
            }
        }

        return toXhtml(doc);
    }

    private static String toXhtml(final Document doc) {
        doc.outputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .charset(StandardCharsets.UTF_8)
                .escapeMode(Entities.EscapeMode.xhtml);
        return doc.html();
    }
}
