package codex.apps.siteexporter;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents the output artifact produced by a {@link PublicationPipeline} run.
 *
 * <p>{@code sizeBytes} is the byte length of the file written to {@code path}.
 * {@code producedAt} is the instant at which the file was written.</p>
 */
public record PublicationArtifact(
        Path path,
        PublicationFormat format,
        long sizeBytes,
        Instant producedAt) {

    public PublicationArtifact {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(producedAt, "producedAt");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must not be negative");
        }
    }
}
