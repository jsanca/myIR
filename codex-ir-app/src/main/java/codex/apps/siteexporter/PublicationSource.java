package codex.apps.siteexporter;

import java.nio.file.Path;

/**
 * Provides the content and manifest for a publication pipeline.
 *
 * <p>The primary implementation is {@link SiteMirrorSource}, which reads
 * from a completed site mirror. Other implementations may supply content
 * from different origins (e.g. a pre-processed document tree).</p>
 */
public interface PublicationSource {

    /** Root directory containing the mirrored HTML files and assets. */
    Path contentDir();

    /** Manifest describing every page and its local path. */
    MirrorManifest manifest();
}
