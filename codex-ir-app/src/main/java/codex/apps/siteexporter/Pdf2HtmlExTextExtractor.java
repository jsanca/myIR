package codex.apps.siteexporter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Extracts readable text from a pdf2htmlEX-generated HTML document.
 *
 * <p>pdf2htmlEX lays out text using absolutely positioned {@code <span class="t">}
 * elements whose coordinates are encoded in CSS classes ({@code .xN}, {@code .yN},
 * {@code .hN}). This extractor:</p>
 * <ol>
 *   <li>Parses those coordinate classes from the document's {@code <style>} blocks.</li>
 *   <li>Visits every {@code .pf} page-frame element in order.</li>
 *   <li>Sorts the {@code .t} spans within each page by their 2-D position
 *       (top-to-bottom, then left-to-right).</li>
 *   <li>Groups co-linear spans into text lines, then lines into paragraphs using a
 *       gap threshold of 1.5× the average line height.</li>
 * </ol>
 *
 * <p>When CSS coordinate classes are absent (unusual), document order is preserved
 * as a fallback.</p>
 */
public final class Pdf2HtmlExTextExtractor {

    // Matches: .x0{left:71.0px}  .y3{bottom:700.0px}  .h1{height:12.0px}
    // Also handles spaces and the "top:" variant for newer pdf2htmlEX versions.
    private static final Pattern COORD_RULE = Pattern.compile(
            "\\.(x|y|h)(\\d+)\\s*\\{\\s*(left|bottom|top|height)\\s*:\\s*(-?[\\d.]+)px",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern Y_DIRECTION = Pattern.compile(
            "\\.y\\d+\\s*\\{\\s*(bottom|top)\\s*:",
            Pattern.CASE_INSENSITIVE
    );

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Extracts readable text from a pre-parsed pdf2htmlEX document.
     *
     * @param doc Jsoup document; must not be {@code null}
     * @return extracted {@link ReaderDocument}; never {@code null}
     */
    public ReaderDocument extract(final Document doc) {
        Objects.requireNonNull(doc, "doc");

        final String title = doc.title().isBlank() ? "Document" : doc.title();
        final CssCoordinates coords = parseCssCoordinates(doc);

        final Elements pfDivs = doc.select(".pf");
        if (pfDivs.isEmpty()) {
            return ReaderDocument.empty(title);
        }

        final List<ReaderPage> pages = new ArrayList<>(pfDivs.size());
        int autoNum = 0;
        for (final Element pf : pfDivs) {
            autoNum++;
            final String attr = pf.attr("data-page-no");
            final int num = attr.isBlank() ? autoNum : parseIntOrDefault(attr, autoNum);
            pages.add(extractPage(pf, num, coords));
        }

        return new ReaderDocument(title, pages);
    }

    /**
     * Parses {@code html} and extracts readable text.
     *
     * @param html    raw HTML string; must not be {@code null}
     * @param baseUri base URI for resolving relative references; must not be {@code null}
     * @return extracted {@link ReaderDocument}; never {@code null}
     */
    public ReaderDocument extract(final String html, final String baseUri) {
        Objects.requireNonNull(html, "html");
        Objects.requireNonNull(baseUri, "baseUri");
        return extract(Jsoup.parse(html, baseUri));
    }

    // ------------------------------------------------------------------
    // CSS coordinate parsing
    // ------------------------------------------------------------------

    private static CssCoordinates parseCssCoordinates(final Document doc) {
        final StringBuilder css = new StringBuilder();
        for (final Element style : doc.select("style")) {
            css.append(style.html()).append('\n');
        }
        final String cssText = css.toString();

        // Detect whether y values use bottom: or top: — pdf2htmlEX default is bottom:
        boolean yIsBottom = true;
        final Matcher dirMatcher = Y_DIRECTION.matcher(cssText);
        if (dirMatcher.find()) {
            yIsBottom = "bottom".equalsIgnoreCase(dirMatcher.group(1));
        }

        final Map<String, Double> x = new HashMap<>();
        final Map<String, Double> y = new HashMap<>();
        final Map<String, Double> h = new HashMap<>();

        final Matcher m = COORD_RULE.matcher(cssText);
        while (m.find()) {
            final String type = m.group(1).toLowerCase();
            final String index = m.group(2);
            final double value = Double.parseDouble(m.group(4));
            switch (type) {
                case "x" -> x.put("x" + index, value);
                case "y" -> y.put("y" + index, value);
                case "h" -> h.put("h" + index, value);
            }
        }

        return new CssCoordinates(Map.copyOf(x), Map.copyOf(y), Map.copyOf(h), yIsBottom);
    }

    // ------------------------------------------------------------------
    // Page extraction
    // ------------------------------------------------------------------

    private static ReaderPage extractPage(final Element pf, final int pageNum,
            final CssCoordinates coords) {
        final Elements spanEls = pf.select(".t");
        if (spanEls.isEmpty()) {
            return new ReaderPage(pageNum, List.of());
        }

        final boolean hasCoords = !coords.x().isEmpty() || !coords.y().isEmpty();

        final List<TextSpan> items = new ArrayList<>(spanEls.size());
        for (final Element span : spanEls) {
            final String text = span.text().trim();
            if (text.isEmpty()) {
                continue;
            }
            final Set<String> classes = span.classNames();
            final double sx = lookup(classes, "x", coords.x(), 0.0);
            final double sy = lookup(classes, "y", coords.y(), 0.0);
            final double sh = lookup(classes, "h", coords.h(), 12.0);
            items.add(new TextSpan(text, sx, sy, sh));
        }

        if (items.isEmpty()) {
            return new ReaderPage(pageNum, List.of());
        }

        if (hasCoords) {
            if (coords.yIsBottom()) {
                // Higher bottom value → closer to top of page → comes first
                items.sort(Comparator.<TextSpan>comparingDouble(TextSpan::y).reversed()
                        .thenComparingDouble(TextSpan::x));
            } else {
                // Lower top value → closer to top of page → comes first
                items.sort(Comparator.<TextSpan>comparingDouble(TextSpan::y)
                        .thenComparingDouble(TextSpan::x));
            }
        }
        // When no coordinate info is available, document order is preserved as-is.

        return new ReaderPage(pageNum, groupIntoParagraphs(items));
    }

    private static double lookup(final Set<String> classes, final String prefix,
            final Map<String, Double> map, final double defaultValue) {
        for (final String cls : classes) {
            if (cls.length() > prefix.length()
                    && cls.startsWith(prefix)
                    && Character.isDigit(cls.charAt(prefix.length()))) {
                final Double val = map.get(cls);
                if (val != null) {
                    return val;
                }
            }
        }
        return defaultValue;
    }

    // ------------------------------------------------------------------
    // Line and paragraph grouping
    // ------------------------------------------------------------------

    private static List<String> groupIntoParagraphs(final List<TextSpan> sorted) {
        // --- Group co-linear spans (same y ± 2 px) into lines ---
        final List<List<TextSpan>> lines = new ArrayList<>();
        final List<TextSpan> currentLine = new ArrayList<>();
        double currentY = sorted.get(0).y();

        for (final TextSpan span : sorted) {
            if (Math.abs(span.y() - currentY) < 2.0) {
                currentLine.add(span);
            } else {
                lines.add(List.copyOf(currentLine));
                currentLine.clear();
                currentLine.add(span);
                currentY = span.y();
            }
        }
        if (!currentLine.isEmpty()) {
            lines.add(List.copyOf(currentLine));
        }

        final double avgH = sorted.stream().mapToDouble(TextSpan::h).average().orElse(12.0);

        // --- Merge lines into paragraphs using a 1.5× line-height gap threshold ---
        final List<String> paragraphs = new ArrayList<>();
        final StringBuilder para = new StringBuilder();

        for (int i = 0; i < lines.size(); i++) {
            final List<TextSpan> line = new ArrayList<>(lines.get(i));
            line.sort(Comparator.comparingDouble(TextSpan::x));

            final String lineText = line.stream()
                    .map(TextSpan::text)
                    .filter(t -> !t.isBlank())
                    .collect(Collectors.joining(" "))
                    .strip();

            if (lineText.isBlank()) {
                continue;
            }

            if (i > 0) {
                final double gap = Math.abs(lines.get(i).get(0).y() - lines.get(i - 1).get(0).y());
                if (gap > avgH * 1.5) {
                    if (!para.isEmpty()) {
                        paragraphs.add(para.toString());
                        para.setLength(0);
                    }
                } else {
                    para.append(' ');
                }
            }

            para.append(lineText);
        }

        if (!para.isEmpty()) {
            paragraphs.add(para.toString());
        }

        return List.copyOf(paragraphs);
    }

    // ------------------------------------------------------------------
    // Internal types
    // ------------------------------------------------------------------

    private record TextSpan(String text, double x, double y, double h) {}

    private record CssCoordinates(
            Map<String, Double> x,
            Map<String, Double> y,
            Map<String, Double> h,
            boolean yIsBottom) {}

    private static int parseIntOrDefault(final String s, final int defaultValue) {
        try {
            return Integer.parseInt(s.trim());
        } catch (final NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
