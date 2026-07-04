package codex.apps.siteexporter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Downloads assets (images, stylesheets, scripts) referenced by mirrored HTML pages.
 *
 * <p>Flow:</p>
 * <ol>
 *   <li>Read each mirrored HTML file listed in {@code mirrorManifest}.</li>
 *   <li>Extract asset references with {@link AssetReferenceExtractor}.</li>
 *   <li>Deduplicate across all pages by canonical URL.</li>
 *   <li>Apply same-domain filter when {@code options.sameDomainOnly()} is true.</li>
 *   <li>Download each unique asset and write it under {@code outputDir/assets/}.</li>
 *   <li>Serialize {@value AssetManifest#FILE_NAME} into the output directory.</li>
 * </ol>
 */
public final class SiteAssetService {

    /**
     * Downloads assets using a real HTTP client.
     *
     * @param options        crawl configuration; must not be {@code null}
     * @param mirrorManifest manifest produced by {@link SiteMirrorService}; must not be {@code null}
     * @return asset manifest describing every discovered and processed asset
     * @throws IOException if a critical I/O error occurs
     */
    public AssetManifest download(final SiteMirrorOptions options,
            final MirrorManifest mirrorManifest) throws IOException {
        return download(options, mirrorManifest, httpFetcher());
    }

    /**
     * Downloads assets using the given {@code fetcher}. Package-private to allow
     * a stub fetcher to be injected in unit tests.
     */
    AssetManifest download(final SiteMirrorOptions options,
            final MirrorManifest mirrorManifest, final AssetFetcher fetcher) throws IOException {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(mirrorManifest, "mirrorManifest");
        Objects.requireNonNull(fetcher, "fetcher");

        final Path outputDir = options.outputDir();
        final Path assetBaseDir = outputDir.resolve("assets");
        Files.createDirectories(assetBaseDir);

        final LinkedHashMap<URI, AssetType> unique = collectUniqueReferences(options, mirrorManifest);
        final AssetLocalPathResolver resolver = new AssetLocalPathResolver(outputDir);

        final List<AssetMetadata> results = new ArrayList<>(unique.size());
        for (final var entry : unique.entrySet()) {
            final URI url = entry.getKey();
            final AssetType type = entry.getValue();
            results.add(processAsset(url, type, options, resolver, fetcher));
        }

        final AssetManifest manifest = AssetManifest.builder()
                .generatedAt(Instant.now())
                .assets(results)
                .build();

        manifest.writeTo(outputDir);

        System.out.printf("[Assets] Done. %d asset(s) downloaded, %d failed, %d skipped. Manifest → %s%n",
                manifest.successfulCount(), manifest.failedCount(), manifest.skippedCount(),
                outputDir.resolve(AssetManifest.FILE_NAME));

        return manifest;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static LinkedHashMap<URI, AssetType> collectUniqueReferences(
            final SiteMirrorOptions options, final MirrorManifest mirrorManifest) {
        final AssetReferenceExtractor extractor = new AssetReferenceExtractor();
        final LinkedHashMap<URI, AssetType> unique = new LinkedHashMap<>();

        for (final MirroredPage page : mirrorManifest.pages()) {
            if (page.mirrorStatus() != MirrorStatus.SUCCESS || page.localHtmlPath() == null) {
                continue;
            }
            final Path htmlFile = options.outputDir().resolve(page.localHtmlPath());
            if (!Files.exists(htmlFile)) {
                continue;
            }
            final String html;
            try {
                html = Files.readString(htmlFile);
            } catch (final IOException e) {
                System.err.printf("[Assets][WARN] Could not read %s: %s%n", htmlFile, e.getMessage());
                continue;
            }
            for (final AssetReference ref : extractor.extract(html, page.url())) {
                unique.putIfAbsent(ref.url(), ref.type());
            }
        }
        return unique;
    }

    private static AssetMetadata processAsset(final URI url, final AssetType type,
            final SiteMirrorOptions options, final AssetLocalPathResolver resolver,
            final AssetFetcher fetcher) {
        final String id = UUID.randomUUID().toString();

        if (options.sameDomainOnly() && !isSameDomain(url, options.seedUrl())) {
            return AssetMetadata.builder()
                    .id(id)
                    .url(url)
                    .assetType(type)
                    .assetStatus(AssetStatus.SKIPPED)
                    .build();
        }

        try {
            final AssetFetcher.Result result = fetcher.fetch(url);
            if (result.statusCode() >= 200 && result.statusCode() < 300) {
                final Path localPath = resolver.resolve(url);
                Files.createDirectories(localPath.getParent());
                Files.write(localPath, result.body());
                final String relativePath = resolver.relativize(localPath);

                System.out.printf("[Assets] %s → %s%n", url, localPath);
                return AssetMetadata.builder()
                        .id(id)
                        .url(url)
                        .assetType(type)
                        .localAssetPath(relativePath)
                        .contentType(result.contentType())
                        .statusCode(result.statusCode())
                        .assetStatus(AssetStatus.SUCCESS)
                        .build();
            } else {
                return AssetMetadata.builder()
                        .id(id)
                        .url(url)
                        .assetType(type)
                        .statusCode(result.statusCode())
                        .assetStatus(AssetStatus.DOWNLOAD_FAILED)
                        .errorMessage("HTTP " + result.statusCode())
                        .build();
            }
        } catch (final IOException e) {
            System.err.printf("[Assets][ERROR] Failed to fetch %s: %s%n", url, e.getMessage());
            return AssetMetadata.builder()
                    .id(id)
                    .url(url)
                    .assetType(type)
                    .assetStatus(AssetStatus.DOWNLOAD_FAILED)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    private static boolean isSameDomain(final URI assetUrl, final URI seedUrl) {
        final String assetHost = assetUrl.getHost();
        final String seedHost = seedUrl.getHost();
        if (assetHost == null || seedHost == null) {
            return false;
        }
        return assetHost.equalsIgnoreCase(seedHost);
    }

    private static AssetFetcher httpFetcher() {
        final HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        return url -> {
            try {
                final HttpRequest request = HttpRequest.newBuilder(url).GET().build();
                final HttpResponse<byte[]> response =
                        client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                final String contentType =
                        response.headers().firstValue("content-type").orElse(null);
                return new AssetFetcher.Result(response.statusCode(), contentType, response.body());
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while fetching: " + url, e);
            }
        };
    }
}
