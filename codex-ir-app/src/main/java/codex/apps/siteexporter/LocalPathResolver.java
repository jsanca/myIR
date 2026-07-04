package codex.apps.siteexporter;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Resolves a page URI to a local file path under the mirror output directory.
 *
 * <p>Mapping rules:</p>
 * <ul>
 *   <li>Root or empty path → {@code index.html}</li>
 *   <li>Trailing slash → {@code <path>/index.html}</li>
 *   <li>No file extension in last segment → {@code <path>/index.html}</li>
 *   <li>Last segment has an extension → used verbatim</li>
 * </ul>
 *
 * <p>Every path segment is sanitized: {@code ..} and {@code .} are stripped,
 * filesystem-unsafe characters are replaced with {@code _}, and the resolved
 * path is verified to remain within {@code outputDir}.</p>
 */
public final class LocalPathResolver {

    private final Path outputDir;
    private final Path normalizedBase;

    public LocalPathResolver(final Path outputDir) {
        this.outputDir = Objects.requireNonNull(outputDir, "outputDir");
        this.normalizedBase = outputDir.toAbsolutePath().normalize();
    }

    /**
     * Returns the local path for the given URI inside {@code outputDir}.
     *
     * @param uri the URI to resolve; must not be {@code null}
     * @return local path where the page should be written
     * @throws SecurityException if the resolved path would escape {@code outputDir}
     */
    public Path resolve(final URI uri) {
        Objects.requireNonNull(uri, "uri");
        String uriPath = uri.getPath();

        if (uriPath == null || uriPath.isEmpty() || "/".equals(uriPath)) {
            return outputDir.resolve("index.html");
        }

        if (uriPath.startsWith("/")) {
            uriPath = uriPath.substring(1);
        }

        final boolean trailingSlash = uriPath.endsWith("/");
        final String sanitized = sanitizePathSegments(uriPath);

        if (sanitized.isEmpty()) {
            return outputDir.resolve("index.html");
        }

        final String filePath;
        if (trailingSlash) {
            filePath = sanitized + "/index.html";
        } else {
            final String lastSegment = sanitized.contains("/")
                    ? sanitized.substring(sanitized.lastIndexOf('/') + 1)
                    : sanitized;
            filePath = lastSegment.contains(".") ? sanitized : sanitized + "/index.html";
        }

        final Path resolved = outputDir.resolve(filePath).normalize();
        enforceWithinOutputDir(resolved, uri);
        return resolved;
    }

    private void enforceWithinOutputDir(final Path resolved, final URI sourceUri) {
        if (!resolved.toAbsolutePath().normalize().startsWith(normalizedBase)) {
            throw new SecurityException(
                    "Resolved path escapes output directory for URI: " + sourceUri);
        }
    }

    /**
     * Strips {@code ..}, {@code .}, and empty segments; replaces filesystem-unsafe
     * characters in each remaining segment with {@code _}.
     */
    private static String sanitizePathSegments(final String path) {
        final List<String> cleaned = new ArrayList<>();
        for (final String segment : path.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                continue;
            }
            final String safe = segment.replaceAll("[\\x00\\\\:*?\"<>|]", "_").strip();
            if (!safe.isEmpty()) {
                cleaned.add(safe);
            }
        }
        return String.join("/", cleaned);
    }
}
