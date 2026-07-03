package codex.apps.siteexporter;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Describes what was written during a site-mirror run.
 *
 * <p>Serialized as {@code mirror-manifest.json} inside the output directory.</p>
 */
public record MirrorManifest(
        URI seedUrl,
        Instant mirroredAt,
        List<MirroredPage> pages) {

    public MirrorManifest {
        Objects.requireNonNull(seedUrl, "seedUrl");
        Objects.requireNonNull(mirroredAt, "mirroredAt");
        pages = List.copyOf(Objects.requireNonNull(pages, "pages"));
    }

    /** Name of the manifest file written inside the output directory. */
    public static final String FILE_NAME = "mirror-manifest.json";

    /**
     * Writes this manifest as {@value FILE_NAME} into the given directory.
     *
     * @param outputDir directory to write into; must exist
     * @throws IOException if the file cannot be written
     */
    public void writeTo(final Path outputDir) throws IOException {
        Objects.requireNonNull(outputDir, "outputDir");
        Files.writeString(outputDir.resolve(FILE_NAME), toJson(), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // JSON serialization (hand-crafted; no external dependency)
    // ------------------------------------------------------------------

    private String toJson() {
        final StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"seedUrl\": \"").append(seedUrl).append("\",\n");
        sb.append("  \"mirroredAt\": \"").append(mirroredAt).append("\",\n");
        sb.append("  \"pageCount\": ").append(pages.size()).append(",\n");
        sb.append("  \"pages\": [\n");
        for (int i = 0; i < pages.size(); i++) {
            final MirroredPage page = pages.get(i);
            sb.append("    {\n");
            sb.append("      \"url\": \"").append(page.url()).append("\",\n");
            sb.append("      \"localPath\": \"").append(escape(page.localPath().toString())).append("\",\n");
            sb.append("      \"title\": \"").append(escape(page.title() != null ? page.title() : "")).append("\",\n");
            sb.append("      \"statusCode\": ").append(page.statusCode()).append(",\n");
            sb.append("      \"fetchedAt\": \"").append(page.fetchedAt()).append("\"\n");
            sb.append("    }");
            if (i < pages.size() - 1) {
                sb.append(',');
            }
            sb.append('\n');
        }
        sb.append("  ]\n");
        sb.append('}');
        return sb.toString();
    }

    private static String escape(final String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
