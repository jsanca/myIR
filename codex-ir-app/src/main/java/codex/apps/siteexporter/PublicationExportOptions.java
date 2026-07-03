package codex.apps.siteexporter;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Configuration for the publication-export phase.
 *
 * <p>Specifies which format to produce and where to write the resulting artifact.</p>
 */
public record PublicationExportOptions(
        PublicationFormat format,
        Path outputPath) {

    public PublicationExportOptions {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(outputPath, "outputPath");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private PublicationFormat format;
        private Path outputPath;

        private Builder() {
        }

        public Builder format(final PublicationFormat format) {
            this.format = Objects.requireNonNull(format, "format");
            return this;
        }

        public Builder outputPath(final Path outputPath) {
            this.outputPath = Objects.requireNonNull(outputPath, "outputPath");
            return this;
        }

        public PublicationExportOptions build() {
            return new PublicationExportOptions(format, outputPath);
        }
    }
}
