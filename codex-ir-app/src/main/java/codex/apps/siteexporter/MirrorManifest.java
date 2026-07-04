package codex.apps.siteexporter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Describes what was processed during a site-mirror run.
 *
 * <p>Serialized as {@value FILE_NAME} inside the output directory via Jackson.</p>
 */
@JsonDeserialize(builder = MirrorManifest.Builder.class)
public record MirrorManifest(
        String manifestVersion,
        URI startUrl,
        Instant generatedAt,
        boolean sameDomainOnly,
        int maxPages,
        int maxDepth,
        int documentCount,
        int successfulCount,
        int failedCount,
        int skippedCount,
        List<MirroredPage> pages) {

    public static final String FILE_NAME = "mirror-manifest.json";
    public static final String MANIFEST_VERSION = "1";

    public MirrorManifest {
        Objects.requireNonNull(manifestVersion, "manifestVersion");
        Objects.requireNonNull(startUrl, "startUrl");
        Objects.requireNonNull(generatedAt, "generatedAt");
        pages = List.copyOf(Objects.requireNonNull(pages, "pages"));
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Writes this manifest as {@value FILE_NAME} into the given directory using Jackson.
     *
     * @param outputDir directory to write into; must exist
     * @throws IOException if the file cannot be written
     */
    public void writeTo(final Path outputDir) throws IOException {
        Objects.requireNonNull(outputDir, "outputDir");
        new ManifestWriter().write(this, outputDir.resolve(FILE_NAME));
    }

    /**
     * Reads a manifest from {@value FILE_NAME} inside the given directory.
     *
     * @param outputDir directory containing the manifest file
     * @throws IOException if the file cannot be read or parsed
     */
    public static MirrorManifest readFrom(final Path outputDir) throws IOException {
        Objects.requireNonNull(outputDir, "outputDir");
        return new ManifestReader().read(outputDir.resolve(FILE_NAME));
    }

    /**
     * {@code documentCount}, {@code successfulCount}, {@code failedCount}, and
     * {@code skippedCount} are derived from the {@code pages} list in {@link #build()};
     * any matching fields in JSON are silently ignored on deserialization.
     */
    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Builder {

        private String manifestVersion = MANIFEST_VERSION;
        private URI startUrl;
        private Instant generatedAt;
        private boolean sameDomainOnly;
        private int maxPages;
        private int maxDepth;
        private List<MirroredPage> pages = List.of();

        private Builder() {}

        public Builder manifestVersion(final String v) { this.manifestVersion = Objects.requireNonNull(v); return this; }
        public Builder startUrl(final URI url) { this.startUrl = Objects.requireNonNull(url); return this; }
        public Builder generatedAt(final Instant t) { this.generatedAt = Objects.requireNonNull(t); return this; }
        public Builder sameDomainOnly(final boolean v) { this.sameDomainOnly = v; return this; }
        public Builder maxPages(final int v) { this.maxPages = v; return this; }
        public Builder maxDepth(final int v) { this.maxDepth = v; return this; }
        public Builder pages(final List<MirroredPage> pages) { this.pages = Objects.requireNonNull(pages); return this; }

        public MirrorManifest build() {
            Objects.requireNonNull(startUrl, "startUrl");
            if (generatedAt == null) {
                generatedAt = Instant.now();
            }
            final int successful = (int) pages.stream()
                    .filter(p -> p.mirrorStatus() == MirrorStatus.SUCCESS).count();
            final int failed = (int) pages.stream()
                    .filter(p -> p.mirrorStatus() == MirrorStatus.FETCH_FAILED
                              || p.mirrorStatus() == MirrorStatus.WRITE_FAILED).count();
            final int skipped = (int) pages.stream()
                    .filter(p -> p.mirrorStatus() == MirrorStatus.SKIPPED).count();
            return new MirrorManifest(manifestVersion, startUrl, generatedAt, sameDomainOnly,
                    maxPages, maxDepth, pages.size(), successful, failed, skipped, pages);
        }
    }
}
