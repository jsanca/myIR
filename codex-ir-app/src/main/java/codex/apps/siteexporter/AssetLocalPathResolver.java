package codex.apps.siteexporter;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Resolves an asset URL to a local path under {@code <outputDir>/assets/}.
 *
 * <p>Delegates path sanitization and traversal safety to {@link LocalPathResolver}.
 * The {@link #relativize(Path)} method returns the path relative to {@code outputDir}
 * (not to the {@code assets} subdirectory), e.g. {@code assets/img/logo.png}.</p>
 */
final class AssetLocalPathResolver {

    private final Path outputDir;
    private final LocalPathResolver delegate;

    AssetLocalPathResolver(final Path outputDir) {
        Objects.requireNonNull(outputDir, "outputDir");
        this.outputDir = outputDir.toAbsolutePath().normalize();
        this.delegate = new LocalPathResolver(outputDir.resolve("assets"));
    }

    /**
     * Returns the absolute local path where the asset should be stored.
     *
     * @param assetUrl the asset URL; must not be {@code null}
     * @return absolute path under {@code <outputDir>/assets/}
     * @throws SecurityException if the resolved path would escape the assets directory
     */
    Path resolve(final URI assetUrl) {
        return delegate.resolve(assetUrl);
    }

    /**
     * Returns the path of an already-resolved asset file relative to {@code outputDir}.
     *
     * @param absolutePath the absolute path returned by {@link #resolve(URI)}
     * @return forward-slash path such as {@code assets/img/logo.png}
     */
    String relativize(final Path absolutePath) {
        Objects.requireNonNull(absolutePath, "absolutePath");
        return outputDir.relativize(absolutePath.toAbsolutePath().normalize())
                        .toString()
                        .replace('\\', '/');
    }
}
