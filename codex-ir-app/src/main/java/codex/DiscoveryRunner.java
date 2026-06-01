package codex;

import codex.ir.ingestion.WebCrawlingConfig;
import codex.ir.ingestion.WebPage;
import codex.ir.ingestion.crawler.WebPageFetcher;
import codex.ir.ingestion.crawler.WebPageFetchers;
import codex.ir.ingestion.crawler.product.DiscoveryReportWriter;
import codex.ir.ingestion.crawler.product.DiscoveryReportWriters;
import codex.ir.ingestion.crawler.product.ProductDiscoveryCollectors;
import codex.ir.ingestion.crawler.product.ProductDiscoveryReport;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * Dev/validation runner: fetches pages and writes a product-discovery
 * report without crawling or exporting.
 *
 * <p>Usage:</p>
 * <pre>
 *   # Explicit URLs — console output (default)
 *   SyjDiscoveryRunner https://example.com/product/a https://example.com/shop/
 *
 *   # From one or more sitemaps
 *   SyjDiscoveryRunner --sitemap https://example.com/product-sitemap.xml --sitemap https://example.com/product_cat-sitemap.xml --limit 50
 *
 *   # JSON-only output
 *   SyjDiscoveryRunner --sitemap https://example.com/sitemap.xml --output json
 *
 *   # Both console + JSON to custom directory
 *   SyjDiscoveryRunner --sitemap https://example.com/sitemap.xml --output both --out-dir ./reports
 * </pre>
 */
public final class DiscoveryRunner {

    private static final int DEFAULT_LIMIT = 100;
    private static final OutputMode DEFAULT_OUTPUT = OutputMode.CONSOLE;

    /** Fallback URLs when no arguments are provided. */
    private static final List<String> DEFAULT_URLS = List.of(
            "https://web-scraping.dev/product/1",
            "https://web-scraping.dev/products"
    );


    private DiscoveryRunner() {
    }

    public static void main(final String[] args) {
        final RunnerArgs parsed = parseArgs(args);
        final List<URI> urls = resolveUrls(parsed);

        System.out.println("Product Discovery Runner");
        System.out.println("URLs to fetch: " + urls.size());
        System.out.println();

        final WebPageFetcher fetcher =
                WebPageFetchers.staticHtml(WebCrawlingConfig.defaultConfig().httpClientConfig());
        final List<WebPage> pages = fetchPages(urls, fetcher);

        if (pages.isEmpty()) {
            System.out.println("No pages could be fetched. Exiting.");
            return;
        }

        System.out.println("Running product discovery on " + pages.size() + " page(s)...\n");
        final ProductDiscoveryReport report =
                ProductDiscoveryCollectors.jsoupDefault().collect(pages);

        final DiscoveryReportWriter writer = createWriter(parsed);
        writer.write(report);
    }

    // ------------------------------------------------------------------
    // Argument parsing
    // ------------------------------------------------------------------

    private record RunnerArgs(List<String> explicitUrls, List<String> sitemapUrls, int limit,
                              OutputMode outputMode, Path outDir) {
    }

    private static RunnerArgs parseArgs(final String[] args) {
        final List<String> urls = new ArrayList<>();
        final List<String> sitemapUrls = new ArrayList<>();
        int limit = DEFAULT_LIMIT;
        OutputMode outputMode = DEFAULT_OUTPUT;
        Path outDir = Path.of("");

        int i = 0;
        while (i < args.length) {
            if ("--sitemap".equals(args[i]) && i + 1 < args.length) {
                sitemapUrls.add(args[++i]);
            } else if ("--limit".equals(args[i]) && i + 1 < args.length) {
                try {
                    limit = Integer.parseInt(args[++i]);
                } catch (final NumberFormatException e) {
                    System.out.println("[WARN] Invalid --limit value; using default " + DEFAULT_LIMIT);
                }
            } else if ("--output".equals(args[i]) && i + 1 < args.length) {
                final String raw = args[++i];
                try {
                    outputMode = OutputMode.valueOf(raw.toUpperCase());
                } catch (final IllegalArgumentException e) {
                    System.out.println("[WARN] Invalid --output value '" + raw
                            + "'; valid: console, json, both. Using " + DEFAULT_OUTPUT.name().toLowerCase() + ".");
                    outputMode = DEFAULT_OUTPUT;
                }
            } else if ("--out-dir".equals(args[i]) && i + 1 < args.length) {
                outDir = Path.of(args[++i]);
            } else {
                urls.add(args[i]);
            }
            i++;
        }

        return new RunnerArgs(urls, sitemapUrls, limit, outputMode, outDir);
    }

    private static List<URI> resolveUrls(final RunnerArgs args) {
        if (!args.sitemapUrls().isEmpty()) {
            final LinkedHashSet<URI> merged = new LinkedHashSet<>();
            for (final String raw : args.sitemapUrls()) {
                System.out.println("Loading URLs from sitemap: " + raw);
                final URI sitemapUri;
                try {
                    sitemapUri = URI.create(raw);
                } catch (final IllegalArgumentException e) {
                    System.out.println("[ERROR] Invalid sitemap URL: " + raw);
                    continue;
                }
                final List<URI> found = SitemapUrlExtractor.http().extract(sitemapUri, args.limit());
                System.out.println("  Found " + found.size() + " URL(s) (limit=" + args.limit() + ")");
                merged.addAll(found);
            }
            final List<URI> result = List.copyOf(merged);
            System.out.println("  Total unique URLs: " + result.size());
            System.out.println();
            return result;
        }

        if (!args.explicitUrls().isEmpty()) {
            return args.explicitUrls().stream()
                    .flatMap(raw -> {
                        try {
                            return java.util.stream.Stream.of(URI.create(raw));
                        } catch (final IllegalArgumentException e) {
                            System.out.println("[SKIP] Invalid URL: " + raw);
                            return java.util.stream.Stream.empty();
                        }
                    })
                    .toList();
        }

        return DEFAULT_URLS.stream().map(URI::create).toList();
    }

    // ------------------------------------------------------------------
    // Fetching
    // ------------------------------------------------------------------

    private static List<WebPage> fetchPages(
            final List<URI> uris, final WebPageFetcher fetcher) {
        final List<WebPage> pages = new ArrayList<>(uris.size());
        for (final URI uri : uris) {
            System.out.print("  Fetching " + uri + " ... ");
            try {
                final Optional<WebPage> page = fetcher.fetch(uri, ignored -> {
                });
                if (page.isPresent()) {
                    pages.add(page.get());
                    System.out.println("OK");
                } else {
                    System.out.println("EMPTY (no content returned)");
                }
            } catch (final Exception e) {
                System.out.println("ERROR: " + e.getMessage());
            }
        }
        System.out.println();
        return pages;
    }

    // ------------------------------------------------------------------
    // Writer resolution
    // ------------------------------------------------------------------

    private static DiscoveryReportWriter createWriter(final RunnerArgs args) {
        final OutputMode mode = args.outputMode();
        final Path outDir = args.outDir();

        return switch (mode) {
            case JSON -> {
                validateOutDir(outDir);
                yield DiscoveryReportWriters.jsonFile(outDir);
            }
            case BOTH -> {
                validateOutDir(outDir);
                yield DiscoveryReportWriters.composite(
                        DiscoveryReportWriters.console(),
                        DiscoveryReportWriters.jsonFile(outDir));
            }
            default -> DiscoveryReportWriters.console();
        };
    }

    private static void validateOutDir(final Path outDir) {
        if (!Files.isDirectory(outDir)) {
            System.err.println("[ERROR] Output directory does not exist or is not a directory: "
                    + outDir.toAbsolutePath());
            System.exit(1);
        }
        if (!Files.isWritable(outDir)) {
            System.err.println("[ERROR] Output directory is not writable: "
                    + outDir.toAbsolutePath());
            System.exit(1);
        }
    }
}
