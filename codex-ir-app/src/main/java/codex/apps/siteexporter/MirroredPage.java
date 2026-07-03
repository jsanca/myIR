package codex.apps.siteexporter;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents a single page that was successfully mirrored to disk.
 *
 * <p>Accumulated by {@link SiteMirrorService} and stored in {@link MirrorManifest}.</p>
 */
public record MirroredPage(
        URI url,
        Path localPath,
        String title,
        int statusCode,
        Instant fetchedAt) {

    public MirroredPage {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(localPath, "localPath");
        Objects.requireNonNull(fetchedAt, "fetchedAt");
        // title may be null for pages that have no <title> element
    }
}
