package codex.ir.ingestion.crawler.internal.text;

import org.jsoup.parser.Parser;

import java.util.Objects;

/**
 * Decodes HTML entities, including double-encoded values.
 *
 * <p>Jsoup's {@code Element.text()} and {@code Element.attr()} decode
 * entities once, so {@code S&amp;amp;J} in source HTML becomes
 * {@code S&amp;J} after extraction. This helper applies repeated
 * unescaping until the text stabilizes, converting double-encoded
 * values like {@code &amp;amp;} into their final form {@code &amp;}.</p>
 *
 * <p>Trims surrounding whitespace after decoding.</p>
 */
public final class HtmlTextDecoder {

    private HtmlTextDecoder() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * Decodes HTML entities in the given text, handling double-encoding.
     *
     * <p>Returns the input unchanged if null or blank. Otherwise, applies
     * {@link Parser#unescapeEntities(String, boolean)} repeatedly until
     * the result stabilizes, then trims whitespace.</p>
     *
     * @param text the text to decode, may be null
     * @return the decoded and trimmed text, or the original value if null
     */
    public static String decode(final String text) {
        if (text == null) {
            return null;
        }
        final String trimmed = text.trim();
        if (trimmed.isBlank()) {
            return trimmed;
        }
        return unescapeRepeatedly(trimmed);
    }

    private static String unescapeRepeatedly(final String input) {
        String previous;
        String current = input;
        do {
            previous = current;
            current = Parser.unescapeEntities(previous, false);
        } while (!current.equals(previous));
        return current.trim();
    }
}
