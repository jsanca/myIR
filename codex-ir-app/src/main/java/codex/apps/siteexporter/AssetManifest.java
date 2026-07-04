package codex.apps.siteexporter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Describes every asset discovered and processed during a site-mirror run.
 *
 * <p>Serialized as {@value FILE_NAME} inside the output directory via Jackson.</p>
 *
 * <p>{@code totalCount}, {@code successfulCount}, {@code failedCount}, and
 * {@code skippedCount} are derived from {@code assets} in {@link Builder#build()};
 * any matching fields in JSON are silently ignored on deserialization.</p>
 */
@JsonDeserialize(builder = AssetManifest.Builder.class)
public record AssetManifest(
        String manifestVersion,
        Instant generatedAt,
        int totalCount,
        int successfulCount,
        int failedCount,
        int skippedCount,
        List<AssetMetadata> assets) {

    public static final String FILE_NAME = "asset-manifest.json";
    public static final String MANIFEST_VERSION = "1";

    public AssetManifest {
        Objects.requireNonNull(manifestVersion, "manifestVersion");
        Objects.requireNonNull(generatedAt, "generatedAt");
        assets = List.copyOf(Objects.requireNonNull(assets, "assets"));
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Writes this manifest as {@value FILE_NAME} into the given directory.
     *
     * @param outputDir directory to write into; must exist
     * @throws IOException if the file cannot be written
     */
    public void writeTo(final Path outputDir) throws IOException {
        Objects.requireNonNull(outputDir, "outputDir");
        new AssetManifestWriter().write(this, outputDir.resolve(FILE_NAME));
    }

    /**
     * Reads a manifest from {@value FILE_NAME} inside the given directory.
     *
     * @param outputDir directory containing the manifest file
     * @throws IOException if the file cannot be read or parsed
     */
    public static AssetManifest readFrom(final Path outputDir) throws IOException {
        Objects.requireNonNull(outputDir, "outputDir");
        return new AssetManifestReader().read(outputDir.resolve(FILE_NAME));
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Builder {

        private String manifestVersion = MANIFEST_VERSION;
        private Instant generatedAt;
        private List<AssetMetadata> assets = List.of();

        private Builder() {}

        public Builder manifestVersion(final String v)       { this.manifestVersion = Objects.requireNonNull(v); return this; }
        public Builder generatedAt(final Instant v)          { this.generatedAt = Objects.requireNonNull(v); return this; }
        public Builder assets(final List<AssetMetadata> v)   { this.assets = Objects.requireNonNull(v); return this; }

        public AssetManifest build() {
            if (generatedAt == null) {
                generatedAt = Instant.now();
            }
            final int successful = (int) assets.stream()
                    .filter(a -> a.assetStatus() == AssetStatus.SUCCESS).count();
            final int failed = (int) assets.stream()
                    .filter(a -> a.assetStatus() == AssetStatus.DOWNLOAD_FAILED).count();
            final int skipped = (int) assets.stream()
                    .filter(a -> a.assetStatus() == AssetStatus.SKIPPED).count();
            return new AssetManifest(manifestVersion, generatedAt,
                    assets.size(), successful, failed, skipped, assets);
        }
    }
}
