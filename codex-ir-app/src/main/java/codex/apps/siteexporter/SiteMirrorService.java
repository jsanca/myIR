package codex.apps.siteexporter;

import codex.ir.canonicalizer.UriCanonicalizers;
import codex.ir.ingestion.DocumentSource;
import codex.ir.ingestion.WebCrawlingConfig;
import codex.ir.ingestion.WebPage;
import codex.ir.ingestion.crawler.WebCrawlerRuntime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mirrors a website to local disk using the traversal crawler.
 *
 * <p>For each crawled {@link WebPage}:</p>
 * <ol>
 *   <li>Resolves a local file path via {@link LocalPathResolver}.</li>
 *   <li>Writes the raw HTML via {@link HtmlPageWriter}.</li>
 *   <li>Accumulates a {@link MirroredPage} entry (including failures).</li>
 * </ol>
 * <p>After all pages are processed, serializes a {@link MirrorManifest} as
 * {@value MirrorManifest#FILE_NAME} inside the output directory.</p>
 */
public final class SiteMirrorService {

    /**
     * Mirrors the site described by {@code options} using the real traversal crawler.
     *
     * @param options crawl and output configuration; must not be {@code null}
     * @return manifest describing what was processed
     * @throws IOException              if a critical IO error occurs
     * @throws IllegalArgumentException if the output directory exists but is not writable
     */
    public MirrorManifest mirror(final SiteMirrorOptions options) throws IOException {
        Objects.requireNonNull(options, "options");
        validateOutputDir(options);
        Files.createDirectories(options.outputDir());

        final WebCrawlingConfig config = buildCrawlingConfig(options);
        final WebCrawlerRuntime runtime = WebCrawlerRuntime.getInstance();
        final DocumentSource<WebPage> source = runtime.webPageSource(
                config, UriCanonicalizers.defaultWeb(), options.seedUrl());

        try {
            return mirror(options, source);
        } finally {
            try {
                runtime.close();
            } catch (final Exception e) {
                System.err.println("[Mirror][WARN] Failed to close crawler runtime: " + e.getMessage());
            }
        }
    }

    /**
     * Mirrors pages provided by {@code source} using the given options.
     *
     * <p>Package-private to allow injection of a fake source in tests.</p>
     *
     * @param options crawl and output configuration; must not be {@code null}
     * @param source  page source to consume; must not be {@code null}
     * @return manifest describing what was processed
     * @throws IOException if the output directory cannot be created
     */
    MirrorManifest mirror(final SiteMirrorOptions options, final DocumentSource<WebPage> source)
            throws IOException {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(source, "source");

        validateOutputDir(options);
        Files.createDirectories(options.outputDir());

        final LocalPathResolver resolver = new LocalPathResolver(options.outputDir());
        final HtmlPageWriter writer = new HtmlPageWriter(resolver);
        final List<MirroredPage> pages = new ArrayList<>();
        final AtomicInteger order = new AtomicInteger(0);

        source.readInto(page -> {
            final long discoveredOrder = order.getAndIncrement();
            final String id = UUID.randomUUID().toString();
            final Instant fetchedAt = page.fetchedAt() != null ? page.fetchedAt() : Instant.now();

            try {
                final Path localPath = writer.write(page);
                final String relativePath = toRelativePath(options.outputDir(), localPath);

                pages.add(MirroredPage.builder()
                        .id(id)
                        .url(page.url())
                        .canonicalUrl(page.url())
                        .localHtmlPath(relativePath)
                        .title(page.title())
                        .depth(null)   // traversal depth not exposed by crawler
                        .discoveredOrder(discoveredOrder)
                        .contentType(page.contentType())
                        .status(page.statusCode())
                        .fetchedAt(fetchedAt)
                        .mirrorStatus(MirrorStatus.SUCCESS)
                        .build());

                System.out.printf("[Mirror] %s → %s%n", page.url(), localPath);
            } catch (final IOException e) {
                pages.add(MirroredPage.builder()
                        .id(id)
                        .url(page.url())
                        .canonicalUrl(page.url())
                        .title(page.title())
                        .depth(null)   // traversal depth not exposed by crawler
                        .discoveredOrder(discoveredOrder)
                        .contentType(page.contentType())
                        .status(page.statusCode())
                        .fetchedAt(fetchedAt)
                        .mirrorStatus(MirrorStatus.WRITE_FAILED)
                        .errorMessage(e.getMessage())
                        .build());

                System.err.printf("[Mirror][ERROR] Failed to write %s: %s%n", page.url(), e.getMessage());
            }
        });

        final MirrorManifest manifest = MirrorManifest.builder()
                .startUrl(options.seedUrl())
                .generatedAt(Instant.now())
                .sameDomainOnly(options.sameDomainOnly())
                .maxPages(options.maxPages())
                .maxDepth(options.maxDepth())
                .pages(pages)
                .build();

        manifest.writeTo(options.outputDir());

        System.out.printf("[Mirror] Done. %d page(s) written, %d failed. Manifest → %s%n",
                manifest.successfulCount(), manifest.failedCount(),
                options.outputDir().resolve(MirrorManifest.FILE_NAME));

        return manifest;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String toRelativePath(final Path outputDir, final Path absolute) {
        return outputDir.relativize(absolute).toString().replace('\\', '/');
    }

    private static WebCrawlingConfig buildCrawlingConfig(final SiteMirrorOptions options) {
        return WebCrawlingConfig.builder()
                .maxDepth(options.maxDepth())
                .maxPages(options.maxPages())
                .sameDomainOnly(options.sameDomainOnly())
                .followExternalLinks(false)
                .skipNonHtml(true)
                .build();
    }

    private static void validateOutputDir(final SiteMirrorOptions options) {
        final Path dir = options.outputDir();
        if (Files.exists(dir) && !Files.isDirectory(dir)) {
            throw new IllegalArgumentException(
                    "outputDir exists but is not a directory: " + dir.toAbsolutePath());
        }
        if (Files.isDirectory(dir) && !Files.isWritable(dir)) {
            throw new IllegalArgumentException(
                    "outputDir is not writable: " + dir.toAbsolutePath());
        }
    }
}
