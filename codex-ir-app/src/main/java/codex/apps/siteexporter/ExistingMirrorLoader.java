package codex.apps.siteexporter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Loads and validates an existing site mirror from disk, skipping the crawl phase.
 *
 * <p>Validation rules:</p>
 * <ul>
 *   <li>The mirror directory must exist.</li>
 *   <li>{@value MirrorManifest#FILE_NAME} must be present inside it; its absence throws
 *       an {@link IOException} with an actionable message.</li>
 *   <li>For every {@link MirrorStatus#SUCCESS} page with a non-null
 *       {@link MirroredPage#localHtmlPath()}, the referenced HTML file is checked for
 *       existence. Missing files are reported as warnings on {@code stderr} but do not
 *       abort loading — render failures for those pages will surface later in
 *       {@link AssemblyReport}.</li>
 * </ul>
 */
public final class ExistingMirrorLoader {

    /**
     * Reads the mirror manifest from {@code mirrorDir} and validates it.
     *
     * @param mirrorDir existing mirror directory that contains
     *                  {@value MirrorManifest#FILE_NAME}; must not be {@code null}
     * @return the parsed {@link MirrorManifest}; never {@code null}
     * @throws IOException if {@code mirrorDir} is not a directory, if the manifest
     *                     file is absent, or if the manifest cannot be parsed
     */
    public MirrorManifest load(final Path mirrorDir) throws IOException {
        Objects.requireNonNull(mirrorDir, "mirrorDir");

        if (!Files.isDirectory(mirrorDir)) {
            throw new IOException(
                    "Mirror directory does not exist or is not a directory: " + mirrorDir.toAbsolutePath()
                    + " — run without --from-mirror to create a new mirror first.");
        }

        final Path manifestFile = mirrorDir.resolve(MirrorManifest.FILE_NAME);
        if (!Files.exists(manifestFile)) {
            throw new IOException(
                    "Mirror manifest not found: " + manifestFile.toAbsolutePath()
                    + " — run without --from-mirror to crawl and create a mirror first.");
        }

        final MirrorManifest manifest = new ManifestReader().read(manifestFile);

        validateHtmlFiles(mirrorDir, manifest);

        return manifest;
    }

    private static void validateHtmlFiles(final Path mirrorDir, final MirrorManifest manifest) {
        int missing = 0;
        for (final MirroredPage page : manifest.pages()) {
            if (page.mirrorStatus() != MirrorStatus.SUCCESS || page.localHtmlPath() == null) {
                continue;
            }
            final Path htmlFile = mirrorDir.resolve(page.localHtmlPath());
            if (!Files.exists(htmlFile)) {
                System.err.printf(
                        "[ExistingMirrorLoader][WARN] HTML file missing: %s (url: %s)%n",
                        htmlFile.toAbsolutePath(), page.url());
                missing++;
            }
        }
        if (missing > 0) {
            System.err.printf(
                    "[ExistingMirrorLoader][WARN] %d of %d SUCCESS page(s) have missing local HTML files."
                    + " Those pages will fail during rendering.%n",
                    missing, manifest.successfulCount());
        }
    }
}
