package codex.ir.ingestion;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

/**
 * Represents a fetched web page with both raw content and minimal fetch metadata.
 *
 * <p>This record acts as an intermediate abstraction in the ingestion pipeline:
 *
 * <pre>
 * Crawler / Fetcher -> WebPage -> DocumentMapper -> Document
 * </pre>
 *
 * It intentionally keeps only the most relevant information needed for mapping
 * into the IR {@code Document} model, while remaining extensible for future needs.
 */
public record WebPage(
        URI url,
        String rawHtml,
        String title,
        String bodyText,
        int statusCode,
        String contentType,
        Instant fetchedAt,
        Map<String, String> headers
) {

    /**
     * Canonical constructor ensuring non-null safety for optional structures.
     */
    public WebPage {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
