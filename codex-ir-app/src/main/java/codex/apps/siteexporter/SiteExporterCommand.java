package codex.apps.siteexporter;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Entry point for the site-exporter application.
 *
 * <p>Parses command-line arguments, validates required flags, then delegates to
 * {@link SiteMirrorService} for the mirror phase (or {@link ExistingMirrorLoader} when
 * resuming from a previously completed mirror) and the export phase.</p>
 *
 * <p>Usage — full pipeline (crawl + render):</p>
 * <pre>
 *   SiteExporterCommand --url https://example.com \
 *       [--out-dir ./mirror]     (default: ./mirror) \
 *       [--max-pages 200]        (default: 100) \
 *       [--max-depth 3]          (default: 3) \
 *       [--no-same-domain]       (default: same-domain only) \
 *       [--format pdf|epub]      (default: pdf) \
 *       [--output ./site.pdf]    (default: ./output.pdf)
 * </pre>
 *
 * <p>Usage — resume from existing mirror (skip crawl):</p>
 * <pre>
 *   SiteExporterCommand --from-mirror ./mirror \
 *       [--format pdf|epub] \
 *       [--output ./site.pdf]
 * </pre>
 *
 * <p>When {@code --from-mirror} is provided {@code --url} is not required.
 * The seed URL and crawl settings are read from the existing manifest.</p>
 */
public final class SiteExporterCommand {

    private SiteExporterCommand() {
    }

    public static void main(final String[] args) {
        final ParsedArgs parsed;
        try {
            parsed = parseArgs(args);
        } catch (final IllegalArgumentException e) {
            System.err.println("[ERROR] " + e.getMessage());
            System.exit(1);
            return; // unreachable — satisfies compiler
        }

        final PublicationExportOptions exportOptions = PublicationExportOptions.builder()
                .format(parsed.format())
                .outputPath(parsed.outputPath())
                .build();

        // ------------------------------------------------------------------
        // Mirror phase: crawl OR load existing mirror
        // ------------------------------------------------------------------

        final SiteMirrorOptions mirrorOptions;
        final MirrorManifest mirrorManifest;

        if (parsed.fromMirrorDir() != null) {
            // Resume mode: skip crawl, load manifest from disk
            System.out.printf("[SiteExporter] Resume mode: loading mirror from %s%n",
                    parsed.fromMirrorDir().toAbsolutePath());
            try {
                mirrorManifest = new ExistingMirrorLoader().load(parsed.fromMirrorDir());
            } catch (final IOException e) {
                System.err.println("[ERROR] " + e.getMessage());
                System.exit(1);
                return;
            }
            mirrorOptions = SiteMirrorOptions.builder()
                    .seedUrl(mirrorManifest.startUrl())
                    .outputDir(parsed.outputDir())
                    .maxPages(mirrorManifest.maxPages())
                    .maxDepth(mirrorManifest.maxDepth())
                    .sameDomainOnly(mirrorManifest.sameDomainOnly())
                    .build();
            System.out.printf("[SiteExporter] Loaded manifest: %d pages (%d successful, %d failed)%n",
                    mirrorManifest.documentCount(),
                    mirrorManifest.successfulCount(),
                    mirrorManifest.failedCount());
        } else {
            // Normal mode: crawl the site
            mirrorOptions = SiteMirrorOptions.builder()
                    .seedUrl(parsed.seedUrl())
                    .outputDir(parsed.outputDir())
                    .maxPages(parsed.maxPages())
                    .maxDepth(parsed.maxDepth())
                    .sameDomainOnly(parsed.sameDomainOnly())
                    .build();

            System.out.println("[SiteExporter] Starting mirror...");
            System.out.println("[SiteExporter] " + mirrorOptions);

            final SiteMirrorService mirrorService = new SiteMirrorService();
            try {
                mirrorManifest = mirrorService.mirror(mirrorOptions);
            } catch (final IOException e) {
                System.err.println("[ERROR] Mirror failed: " + e.getMessage());
                System.exit(1);
                return;
            }
        }

        // ------------------------------------------------------------------
        // Select publication driver — format-specific construction stays here
        // ------------------------------------------------------------------

        final PublicationDriver driver;
        try {
            driver = PublicationDrivers.forFormat(exportOptions.format(),
                    mirrorOptions.outputDir());
        } catch (final IllegalArgumentException e) {
            System.err.println("[ERROR] " + e.getMessage());
            System.exit(1);
            return;
        }

        // ------------------------------------------------------------------
        // Asset download and link rewrite — only for drivers that require it
        // ------------------------------------------------------------------

        if (driver.requiresAssetProcessing()) {
            System.out.println("[SiteExporter] Downloading assets...");
            final AssetManifest assetManifest;
            try {
                assetManifest = new SiteAssetService().download(mirrorOptions, mirrorManifest);
            } catch (final IOException e) {
                System.err.println("[ERROR] Asset download failed: " + e.getMessage());
                System.exit(1);
                return;
            }

            System.out.println("[SiteExporter] Rewriting links...");
            try {
                new SiteLinkRewriteService().rewrite(mirrorOptions.outputDir(), mirrorManifest, assetManifest);
            } catch (final IOException e) {
                System.err.println("[ERROR] Link rewrite failed: " + e.getMessage());
                System.exit(1);
                return;
            }
        }

        System.out.println("[SiteExporter] Rendering and assembling publication...");
        System.out.println("[SiteExporter] Export options : " + exportOptions);
        try {
            final PublicationArtifact artifact = driver.publish(
                    SiteMirrorSource.of(mirrorOptions.outputDir(), mirrorManifest),
                    exportOptions);
            final AssemblyReport report = artifact.assemblyReport();
            System.out.printf("[SiteExporter] Done. Artifact → %s (%d bytes)%n",
                    artifact.path().toAbsolutePath(), artifact.sizeBytes());
            System.out.printf("[SiteExporter] Assembly: %d attempted, %d rendered, %d failed%n",
                    report.pagesAttempted(), report.pagesRendered(), report.pagesFailed());
            if (report.pagesFailed() > 0) {
                report.renderFailures().forEach(f ->
                        System.err.printf("[SiteExporter][WARN] Render failed: %s — %s%n",
                                f.pageUrl(), f.reason()));
            }
        } catch (final IOException e) {
            System.err.println("[ERROR] Publication export failed: " + e.getMessage());
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------
    // Argument parsing — no System.exit; throws IllegalArgumentException
    // ------------------------------------------------------------------

    /**
     * Parsed command-line arguments.
     *
     * <p>{@code seedUrl} is {@code null} when {@code fromMirrorDir} is provided
     * (the seed URL is read from the existing manifest in that case).
     * {@code fromMirrorDir} is {@code null} when running in normal crawl mode.</p>
     */
    record ParsedArgs(
            URI seedUrl,
            Path outputDir,
            int maxPages,
            int maxDepth,
            boolean sameDomainOnly,
            PublicationFormat format,
            Path outputPath,
            Path fromMirrorDir) {
    }

    /**
     * Parses {@code args} into a {@link ParsedArgs} value.
     *
     * <p>Either {@code --url} or {@code --from-mirror} is required; both may be
     * supplied together, in which case {@code --url} is ignored for the mirror phase.</p>
     *
     * @param args command-line arguments; must not be {@code null}
     * @return parsed arguments
     * @throws IllegalArgumentException if neither {@code --url} nor {@code --from-mirror}
     *                                  is provided, or if a flag value is invalid
     */
    static ParsedArgs parseArgs(final String[] args) {
        Objects.requireNonNull(args, "args");

        URI seedUrl = null;
        Path outputDir = null;          // resolved after the loop (depends on fromMirrorDir)
        boolean outputDirExplicit = false;
        int maxPages = 100;
        int maxDepth = 3;
        boolean sameDomainOnly = true;
        PublicationFormat format = PublicationFormat.PDF;
        Path outputPath = null;         // resolved after the loop (depends on format)
        boolean outputPathExplicit = false;
        Path fromMirrorDir = null;

        int i = 0;
        while (i < args.length) {
            switch (args[i]) {
                case "--url" -> {
                    if (i + 1 < args.length) {
                        seedUrl = URI.create(args[++i]);
                    }
                }
                case "--out-dir" -> {
                    if (i + 1 < args.length) {
                        outputDir = Path.of(args[++i]);
                        outputDirExplicit = true;
                    }
                }
                case "--from-mirror" -> {
                    if (i + 1 < args.length) {
                        fromMirrorDir = Path.of(args[++i]);
                    }
                }
                case "--max-pages" -> {
                    if (i + 1 < args.length) {
                        try {
                            maxPages = Integer.parseInt(args[++i]);
                        } catch (final NumberFormatException e) {
                            System.err.println("[WARN] Invalid --max-pages value; using default " + maxPages);
                        }
                    }
                }
                case "--max-depth" -> {
                    if (i + 1 < args.length) {
                        try {
                            maxDepth = Integer.parseInt(args[++i]);
                        } catch (final NumberFormatException e) {
                            System.err.println("[WARN] Invalid --max-depth value; using default " + maxDepth);
                        }
                    }
                }
                case "--no-same-domain" -> sameDomainOnly = false;
                case "--format" -> {
                    if (i + 1 < args.length) {
                        try {
                            format = PublicationFormat.valueOf(args[++i].toUpperCase());
                        } catch (final IllegalArgumentException e) {
                            System.err.println("[WARN] Unknown --format value; valid: pdf, epub. Using PDF.");
                        }
                    }
                }
                case "--output" -> {
                    if (i + 1 < args.length) {
                        outputPath = Path.of(args[++i]);
                        outputPathExplicit = true;
                    }
                }
                default -> System.err.println("[WARN] Unknown argument: " + args[i]);
            }
            i++;
        }

        // --from-mirror sets the default outputDir to the mirror directory itself
        if (outputDir == null) {
            outputDir = (fromMirrorDir != null) ? fromMirrorDir : Path.of("./mirror");
        }

        // Default output path depends on the chosen format
        if (!outputPathExplicit) {
            outputPath = (format == PublicationFormat.MARKDOWN)
                    ? Path.of("./output.md")
                    : Path.of("./output.pdf");
        }

        if (seedUrl == null && fromMirrorDir == null) {
            throw new IllegalArgumentException(
                    "--url <seed-url> is required, or use --from-mirror <dir> to resume from an existing mirror.");
        }

        return new ParsedArgs(seedUrl, outputDir, maxPages, maxDepth, sameDomainOnly,
                format, outputPath, fromMirrorDir);
    }
}
