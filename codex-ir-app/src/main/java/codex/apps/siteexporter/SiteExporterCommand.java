package codex.apps.siteexporter;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Entry point for the site-exporter application.
 *
 * <p>Parses command-line arguments, validates required flags, then delegates to
 * {@link SiteMirrorService} for the mirror phase and (Phase 8+) the export phase.</p>
 *
 * <p>Usage:</p>
 * <pre>
 *   SiteExporterCommand --url https://example.com \
 *       [--out-dir ./mirror]     (default: ./mirror) \
 *       [--max-pages 200]        (default: 100) \
 *       [--max-depth 3]          (default: 3) \
 *       [--no-same-domain]       (default: same-domain only) \
 *       [--format pdf|epub]      (default: pdf) \
 *       [--output ./site.pdf]    (default: ./output.pdf)
 * </pre>
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

        final SiteMirrorOptions mirrorOptions = SiteMirrorOptions.builder()
                .seedUrl(parsed.seedUrl())
                .outputDir(parsed.outputDir())
                .maxPages(parsed.maxPages())
                .maxDepth(parsed.maxDepth())
                .sameDomainOnly(parsed.sameDomainOnly())
                .build();

        final PublicationExportOptions exportOptions = PublicationExportOptions.builder()
                .format(parsed.format())
                .outputPath(parsed.outputPath())
                .build();

        System.out.println("[SiteExporter] Starting mirror...");
        System.out.println("[SiteExporter] " + mirrorOptions);

        final SiteMirrorService mirrorService = new SiteMirrorService();
        try {
            mirrorService.mirror(mirrorOptions);
        } catch (final IOException e) {
            System.err.println("[ERROR] Mirror failed: " + e.getMessage());
            System.exit(1);
        }

        System.out.println("[SiteExporter] Export options : " + exportOptions);
        System.out.println("[SiteExporter] Publication export not yet implemented — see Phase 8.");
    }

    // ------------------------------------------------------------------
    // Argument parsing — no System.exit; throws IllegalArgumentException
    // ------------------------------------------------------------------

    record ParsedArgs(
            URI seedUrl,
            Path outputDir,
            int maxPages,
            int maxDepth,
            boolean sameDomainOnly,
            PublicationFormat format,
            Path outputPath) {
    }

    /**
     * Parses {@code args} into a {@link ParsedArgs} value.
     *
     * @param args command-line arguments; must not be {@code null}
     * @return parsed arguments
     * @throws IllegalArgumentException if {@code --url} is missing or a flag value is invalid
     */
    static ParsedArgs parseArgs(final String[] args) {
        Objects.requireNonNull(args, "args");

        URI seedUrl = null;
        Path outputDir = Path.of("./mirror");
        int maxPages = 100;
        int maxDepth = 3;
        boolean sameDomainOnly = true;
        PublicationFormat format = PublicationFormat.PDF;
        Path outputPath = Path.of("./output.pdf");

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
                    }
                }
                default -> System.err.println("[WARN] Unknown argument: " + args[i]);
            }
            i++;
        }

        if (seedUrl == null) {
            throw new IllegalArgumentException("--url <seed-url> is required.");
        }

        return new ParsedArgs(seedUrl, outputDir, maxPages, maxDepth, sameDomainOnly, format, outputPath);
    }
}
