package codex.apps.siteexporter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.net.URI;
import java.util.Objects;

/**
 * Describes a single discovered asset and the result of its download attempt.
 *
 * <p>{@code localAssetPath} is relative to the mirror output directory (e.g.
 * {@code assets/img/logo.png}).  It is {@code null} for {@code DOWNLOAD_FAILED}
 * and {@code SKIPPED} entries.</p>
 */
@JsonDeserialize(builder = AssetMetadata.Builder.class)
public record AssetMetadata(
        String id,
        URI url,
        AssetType assetType,
        String localAssetPath,
        String contentType,
        int statusCode,
        AssetStatus assetStatus,
        String errorMessage) {

    public AssetMetadata {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(assetType, "assetType");
        Objects.requireNonNull(assetStatus, "assetStatus");
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Builder {

        private String id;
        private URI url;
        private AssetType assetType;
        private String localAssetPath;
        private String contentType;
        private int statusCode;
        private AssetStatus assetStatus;
        private String errorMessage;

        private Builder() {}

        public Builder id(final String v)               { this.id = Objects.requireNonNull(v); return this; }
        public Builder url(final URI v)                 { this.url = Objects.requireNonNull(v); return this; }
        public Builder assetType(final AssetType v)     { this.assetType = Objects.requireNonNull(v); return this; }
        public Builder localAssetPath(final String v)   { this.localAssetPath = v; return this; }
        public Builder contentType(final String v)      { this.contentType = v; return this; }
        public Builder statusCode(final int v)          { this.statusCode = v; return this; }
        public Builder assetStatus(final AssetStatus v) { this.assetStatus = Objects.requireNonNull(v); return this; }
        public Builder errorMessage(final String v)     { this.errorMessage = v; return this; }

        public AssetMetadata build() {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(url, "url");
            Objects.requireNonNull(assetType, "assetType");
            Objects.requireNonNull(assetStatus, "assetStatus");
            return new AssetMetadata(id, url, assetType, localAssetPath,
                    contentType, statusCode, assetStatus, errorMessage);
        }
    }
}
