package codex.apps.siteexporter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A {@link PublicationSource} backed by a completed site mirror on disk.
 *
 * <p>Use {@link #from(Path)} to construct an instance from an output directory
 * produced by {@link SiteMirrorService}. The manifest is read from
 * {@value MirrorManifest#FILE_NAME} inside that directory.</p>
 *
 * <p>Use {@link #of(Path, MirrorManifest)} when the manifest is already in
 * memory (e.g. directly after mirroring, or in tests).</p>
 */
public final class SiteMirrorSource implements PublicationSource {

    private final Path contentDir;
    private final MirrorManifest manifest;

    private SiteMirrorSource(final Path contentDir, final MirrorManifest manifest) {
        this.contentDir = Objects.requireNonNull(contentDir, "contentDir");
        this.manifest = Objects.requireNonNull(manifest, "manifest");
    }

    /**
     * Reads the mirror manifest from {@code outputDir} and constructs a source.
     *
     * @param outputDir directory produced by {@link SiteMirrorService}; must contain
     *                  {@value MirrorManifest#FILE_NAME}
     * @throws IOException if the manifest file cannot be read
     */
    public static SiteMirrorSource from(final Path outputDir) throws IOException {
        Objects.requireNonNull(outputDir, "outputDir");
        return new SiteMirrorSource(outputDir, MirrorManifest.readFrom(outputDir));
    }

    /**
     * Constructs a source from an already-loaded manifest.
     *
     * @param contentDir directory containing the mirrored HTML files and assets
     * @param manifest   manifest describing the mirror
     */
    public static SiteMirrorSource of(final Path contentDir, final MirrorManifest manifest) {
        return new SiteMirrorSource(contentDir, manifest);
    }

    @Override
    public Path contentDir() {
        return contentDir;
    }

    @Override
    public MirrorManifest manifest() {
        return manifest;
    }
}
