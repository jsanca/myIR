package codex.apps.siteexporter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents a single page processed during a site-mirror run.
 *
 * <p>Entries with {@link MirrorStatus#SUCCESS} have a non-null {@code localHtmlPath}.
 * Entries with other statuses carry a non-null {@code errorMessage}.</p>
 *
 * <p>{@code depth} is {@code null} when the crawl depth is unknown (the traversal
 * crawler does not currently expose it). A value of {@code 0} means the seed page.</p>
 */
@JsonDeserialize(builder = MirroredPage.Builder.class)
public record MirroredPage(
        String id,
        URI url,
        URI canonicalUrl,
        String localHtmlPath,
        String title,
        Integer depth,
        long discoveredOrder,
        URI parentUrl,
        String contentType,
        int status,
        Instant fetchedAt,
        MirrorStatus mirrorStatus,
        String errorMessage) {

    public MirroredPage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(canonicalUrl, "canonicalUrl");
        Objects.requireNonNull(mirrorStatus, "mirrorStatus");
        // depth null = unknown; localHtmlPath, title, parentUrl, contentType,
        // fetchedAt, errorMessage may be null
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Builder {

        private String id;
        private URI url;
        private URI canonicalUrl;
        private String localHtmlPath;
        private String title;
        private Integer depth;
        private long discoveredOrder;
        private URI parentUrl;
        private String contentType;
        private int status;
        private Instant fetchedAt;
        private MirrorStatus mirrorStatus;
        private String errorMessage;

        private Builder() {}

        public Builder id(final String id) { this.id = id; return this; }
        public Builder url(final URI url) { this.url = url; return this; }
        public Builder canonicalUrl(final URI canonicalUrl) { this.canonicalUrl = canonicalUrl; return this; }
        public Builder localHtmlPath(final String localHtmlPath) { this.localHtmlPath = localHtmlPath; return this; }
        public Builder title(final String title) { this.title = title; return this; }
        public Builder depth(final Integer depth) { this.depth = depth; return this; }
        public Builder discoveredOrder(final long discoveredOrder) { this.discoveredOrder = discoveredOrder; return this; }
        public Builder parentUrl(final URI parentUrl) { this.parentUrl = parentUrl; return this; }
        public Builder contentType(final String contentType) { this.contentType = contentType; return this; }
        public Builder status(final int status) { this.status = status; return this; }
        public Builder fetchedAt(final Instant fetchedAt) { this.fetchedAt = fetchedAt; return this; }
        public Builder mirrorStatus(final MirrorStatus mirrorStatus) { this.mirrorStatus = mirrorStatus; return this; }
        public Builder errorMessage(final String errorMessage) { this.errorMessage = errorMessage; return this; }

        public MirroredPage build() {
            return new MirroredPage(id, url, canonicalUrl, localHtmlPath, title, depth,
                    discoveredOrder, parentUrl, contentType, status, fetchedAt, mirrorStatus, errorMessage);
        }
    }
}
