
package codex.ir.ingestion.crawler.fetcher;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Immutable representation of an HTTP response used by the web ingestion layer.
 *
 * <p>This value object intentionally keeps only the information needed by the
 * crawler and fetchers. It is transport-agnostic and can be produced by a JDK
 * {@code HttpClient}-based implementation or by any future HTTP client adapter.</p>
 * @author jsanca & elo
 */
public record WebHttpResponse(
        URI uri,
        int statusCode,
        String body,
        String contentType,
        Map<String, List<String>> headers
) {

    /**
     * Canonical constructor ensuring defensive defaults for optional values.
     */
    public WebHttpResponse {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        body = body == null ? "" : body;
        contentType = contentType == null ? "" : contentType;
    }

    /**
     * Returns whether the response is considered successful (2xx).
     *
     * @return {@code true} if the status code is between 200 and 299
     */
    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * Returns whether the response content type looks like HTML.
     *
     * @return {@code true} if the content type contains {@code text/html}
     */
    public boolean isHtml() {
        return containsContentType("text/html");
    }

    /**
     * Returns whether the response content type looks like XML.
     *
     * @return {@code true} if the content type contains {@code application/xml}
     */
    public boolean isXml() {
        return containsContentType("xml");
    }

    /**
     * Returns whether the response content type looks like JSON.
     *
     * @return {@code true} if the content type contains {@code application/json}
     */
    public boolean isJson() {
        return containsContentType("json");
    }

    private boolean containsContentType(final String value) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        return contentType.toLowerCase().contains(value);
    }

}
