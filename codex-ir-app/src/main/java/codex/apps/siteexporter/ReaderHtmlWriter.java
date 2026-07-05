package codex.apps.siteexporter;

import java.util.List;
import java.util.Objects;

/**
 * Produces a clean, print-ready HTML document from a {@link ReaderDocument}.
 *
 * <p>The generated HTML:</p>
 * <ul>
 *   <li>Contains no JavaScript.</li>
 *   <li>Uses only simple inline CSS — no {@code @font-face} declarations, no absolute
 *       positioning, no CSS transforms.</li>
 *   <li>Emits one {@code <div class="page">} per {@link ReaderPage}, with a
 *       {@code page-break-before: always} rule so that each source page maps to a
 *       PDF page when rendered by OpenHTMLToPDF.</li>
 *   <li>Wraps each paragraph in a {@code <p>} element.</li>
 *   <li>Includes an optional {@code <div class="page-num">} marker at the bottom of
 *       each page.</li>
 * </ul>
 *
 * <p>The output string is valid HTML5 that can be written to a file and passed directly
 * to {@link OpenHtmlToPdfRenderer#render}. {@code OpenHtmlToPdfRenderer} will internally
 * convert it to well-formed XHTML via {@link HtmlToXhtmlSanitizer} before rendering.</p>
 */
public final class ReaderHtmlWriter {

    private static final String STYLE = """
            body{font-family:Georgia,serif;font-size:11pt;line-height:1.6;margin:2cm 2.5cm;}
            .page{page-break-before:always;}
            .page:first-child{page-break-before:auto;}
            .page-num{text-align:center;color:#888;font-size:9pt;margin-top:2em;}
            p{margin:0.5em 0 1em;}
            """;

    /**
     * Converts {@code doc} into a self-contained HTML string.
     *
     * @param doc the reader document to render; must not be {@code null}
     * @return HTML string; never {@code null} or empty
     */
    public String write(final ReaderDocument doc) {
        Objects.requireNonNull(doc, "doc");

        final StringBuilder sb = new StringBuilder(4096);

        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"en\">\n");
        sb.append("<head>\n");
        sb.append("<meta charset=\"UTF-8\" />\n");
        sb.append("<title>").append(escape(doc.title())).append("</title>\n");
        sb.append("<style>\n").append(STYLE).append("</style>\n");
        sb.append("</head>\n");
        sb.append("<body>\n");

        final List<ReaderPage> pages = doc.pages();
        for (int i = 0; i < pages.size(); i++) {
            final ReaderPage page = pages.get(i);
            sb.append("<div class=\"page\">\n");

            for (final String para : page.paragraphs()) {
                sb.append("<p>").append(escape(para)).append("</p>\n");
            }

            sb.append("<div class=\"page-num\">&mdash;&nbsp;")
              .append(page.pageNumber())
              .append("&nbsp;&mdash;</div>\n");
            sb.append("</div>\n");
        }

        sb.append("</body>\n");
        sb.append("</html>");

        return sb.toString();
    }

    private static String escape(final String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
