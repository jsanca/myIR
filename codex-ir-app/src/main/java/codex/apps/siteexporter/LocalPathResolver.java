package codex.apps.siteexporter;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Resolves a page URI to a local file path under the mirror output directory.
 *
 * <p>Mapping rules:</p>
 * <ul>
 *   <li>Root or empty path → {@code index.html}</li>
 *   <li>Trailing slash → {@code <path>index.html}</li>
 *   <li>No file extension in the last segment → {@code <path>/index.html}</li>
 *   <li>Last segment has an extension → used verbatim</li>
 * </ul>
 */
public final class LocalPathResolver {

    private final Path outputDir;

    public LocalPathResolver(final Path outputDir) {
        this.outputDir = Objects.requireNonNull(outputDir, "outputDir");
    }

    /**
     * Returns the local path for the given URI inside {@code outputDir}.
     *
     * @param uri the URI to resolve; must not be {@code null}
     * @return local path where the page should be written
     */
    public Path resolve(final URI uri) {
        Objects.requireNonNull(uri, "uri");
        String uriPath = uri.getPath();

        if (uriPath == null || uriPath.isEmpty() || "/".equals(uriPath)) {
            return outputDir.resolve("index.html");
        }

        // Strip leading slash so Path.resolve treats it as relative
        if (uriPath.startsWith("/")) {
            uriPath = uriPath.substring(1);
        }

        // Trailing slash → directory index
        if (uriPath.endsWith("/")) {
            return outputDir.resolve(uriPath + "index.html");
        }

        // No extension in last segment → treat as directory, add index.html
        final String lastSegment = uriPath.contains("/")
                ? uriPath.substring(uriPath.lastIndexOf('/') + 1)
                : uriPath;
        if (!lastSegment.contains(".")) {
            return outputDir.resolve(uriPath + "/index.html");
        }

        return outputDir.resolve(uriPath);
    }
}
