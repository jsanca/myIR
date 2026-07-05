package codex.apps.siteexporter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExistingMirrorLoaderTest {

    private static final URI SEED = URI.create("https://book.example.com/");
    private static final Instant FETCHED_AT = Instant.parse("2026-07-04T12:00:00Z");

    private final ExistingMirrorLoader loader = new ExistingMirrorLoader();

    // ------------------------------------------------------------------
    // Null guard
    // ------------------------------------------------------------------

    @Test
    void loadShouldThrowWhenMirrorDirIsNull() {
        assertThrows(NullPointerException.class, () -> loader.load(null));
    }

    // ------------------------------------------------------------------
    // Hard-fail cases
    // ------------------------------------------------------------------

    @Test
    void loadShouldThrowWhenMirrorDirDoesNotExist(@TempDir final Path tempDir) {
        final Path missing = tempDir.resolve("nonexistent-mirror");

        final IOException ex = assertThrows(IOException.class, () -> loader.load(missing));

        final String msg = ex.getMessage();
        assertTrue(msg.contains("nonexistent-mirror"),
                "error must identify the missing directory");
        assertTrue(msg.toLowerCase().contains("not exist") || msg.toLowerCase().contains("directory"),
                "error must mention the directory problem");
    }

    @Test
    void loadShouldThrowWhenManifestFileIsMissing(@TempDir final Path tempDir) {
        // tempDir exists but contains no mirror-manifest.json
        final IOException ex = assertThrows(IOException.class, () -> loader.load(tempDir));

        final String msg = ex.getMessage();
        assertTrue(msg.contains(MirrorManifest.FILE_NAME),
                "error must name the missing manifest file");
        assertTrue(msg.contains("--from-mirror") || msg.toLowerCase().contains("mirror"),
                "error must provide guidance on next steps");
    }

    @Test
    void loadShouldThrowWhenMirrorPathIsAFile(@TempDir final Path tempDir) throws Exception {
        final Path file = tempDir.resolve("notadir.json");
        Files.writeString(file, "{}");

        final IOException ex = assertThrows(IOException.class, () -> loader.load(file));
        assertNotNull(ex.getMessage());
    }

    // ------------------------------------------------------------------
    // Happy path
    // ------------------------------------------------------------------

    @Test
    void loadShouldReturnManifestForValidMirrorDirectory(@TempDir final Path tempDir)
            throws Exception {
        final MirrorManifest written = buildManifest(List.of(
                successPage("id-1", "https://book.example.com/", "index.html")));
        written.writeTo(tempDir);
        Files.writeString(tempDir.resolve("index.html"),
                "<html><body><p>Home</p></body></html>");

        final MirrorManifest loaded = loader.load(tempDir);

        assertEquals(SEED, loaded.startUrl());
        assertEquals(1, loaded.successfulCount());
    }

    @Test
    void loadShouldPreserveAllManifestFields(@TempDir final Path tempDir) throws Exception {
        final MirrorManifest written = buildManifest(List.of(
                successPage("id-1", "https://book.example.com/ch1", "ch1.html"),
                successPage("id-2", "https://book.example.com/ch2", "ch2.html")));
        written.writeTo(tempDir);
        Files.writeString(tempDir.resolve("ch1.html"), "<html></html>");
        Files.writeString(tempDir.resolve("ch2.html"), "<html></html>");

        final MirrorManifest loaded = loader.load(tempDir);

        assertEquals(2, loaded.successfulCount());
        assertEquals(SEED, loaded.startUrl());
        assertEquals(2, loaded.pages().size());
    }

    @Test
    void loadShouldSucceedWhenAllSuccessPageHtmlFilesExist(@TempDir final Path tempDir)
            throws Exception {
        final MirrorManifest manifest = buildManifest(List.of(
                successPage("id-1", "https://book.example.com/p1", "pages/p1.html"),
                successPage("id-2", "https://book.example.com/p2", "pages/p2.html")));
        manifest.writeTo(tempDir);
        Files.createDirectories(tempDir.resolve("pages"));
        Files.writeString(tempDir.resolve("pages/p1.html"), "<html></html>");
        Files.writeString(tempDir.resolve("pages/p2.html"), "<html></html>");

        final MirrorManifest loaded = loader.load(tempDir);

        assertEquals(2, loaded.successfulCount(), "both pages loaded");
    }

    // ------------------------------------------------------------------
    // Missing file warnings (soft failure)
    // ------------------------------------------------------------------

    @Test
    void loadShouldSucceedEvenWhenSomeHtmlFilesAreMissing(@TempDir final Path tempDir)
            throws Exception {
        // Only write one of the two referenced files
        final MirrorManifest manifest = buildManifest(List.of(
                successPage("id-1", "https://book.example.com/ok", "ok.html"),
                successPage("id-2", "https://book.example.com/missing", "missing.html")));
        manifest.writeTo(tempDir);
        Files.writeString(tempDir.resolve("ok.html"), "<html></html>");
        // missing.html intentionally NOT created

        // Must succeed (missing files are warnings, not errors)
        final MirrorManifest loaded = assertDoesNotThrow(() -> loader.load(tempDir));
        assertEquals(2, loaded.successfulCount(),
                "manifest is returned even when some files are missing");
    }

    @Test
    void loadShouldIgnoreNonSuccessPagesDuringFileValidation(@TempDir final Path tempDir)
            throws Exception {
        // FETCH_FAILED page has no local file — must not cause a warning or failure
        final MirroredPage failedPage = MirroredPage.builder()
                .id("id-fail").url(URI.create("https://book.example.com/bad"))
                .canonicalUrl(URI.create("https://book.example.com/bad"))
                .depth(0).discoveredOrder(1L).status(404).fetchedAt(FETCHED_AT)
                .mirrorStatus(MirrorStatus.FETCH_FAILED).errorMessage("not found").build();

        final MirrorManifest manifest = buildManifest(List.of(
                successPage("id-1", "https://book.example.com/ok", "ok.html"),
                failedPage));
        manifest.writeTo(tempDir);
        Files.writeString(tempDir.resolve("ok.html"), "<html></html>");

        final MirrorManifest loaded = assertDoesNotThrow(() -> loader.load(tempDir));
        assertEquals(1, loaded.successfulCount());
    }

    @Test
    void loadShouldIgnoreSuccessPagesWithNullLocalHtmlPath(@TempDir final Path tempDir)
            throws Exception {
        // A SUCCESS page with a null localHtmlPath must not cause a NullPointerException
        final MirroredPage pageNoPath = MirroredPage.builder()
                .id("id-nop").url(URI.create("https://book.example.com/nop"))
                .canonicalUrl(URI.create("https://book.example.com/nop"))
                .depth(0).discoveredOrder(0L).status(200).fetchedAt(FETCHED_AT)
                .mirrorStatus(MirrorStatus.SUCCESS).build(); // localHtmlPath is null

        final MirrorManifest manifest = buildManifest(List.of(pageNoPath));
        manifest.writeTo(tempDir);

        assertDoesNotThrow(() -> loader.load(tempDir));
    }

    // ------------------------------------------------------------------
    // Integration: pipeline continues after loading
    // ------------------------------------------------------------------

    @Test
    void loadedManifestShouldSupportPublicationPipelineRun(@TempDir final Path tempDir)
            throws Exception {
        // Write two small HTML pages and a mirror manifest
        Files.writeString(tempDir.resolve("page1.html"),
                "<!DOCTYPE html><html><body><h1>Chapter 1</h1></body></html>");
        Files.writeString(tempDir.resolve("page2.html"),
                "<!DOCTYPE html><html><body><h1>Chapter 2</h1></body></html>");

        final MirrorManifest manifest = buildManifest(List.of(
                successPageOrdered("id-1", "https://book.example.com/p1", "page1.html", 0L),
                successPageOrdered("id-2", "https://book.example.com/p2", "page2.html", 1L)));
        manifest.writeTo(tempDir);

        // Load the existing mirror (skip crawl)
        final MirrorManifest loaded = loader.load(tempDir);

        // Verify SiteMirrorService was NOT needed — we have the manifest already
        assertEquals(2, loaded.successfulCount());

        // Run PublicationPipeline against the loaded manifest
        final Path outputPdf = tempDir.resolve("output.pdf");
        final PublicationArtifact artifact = PublicationPipeline.builder()
                .source(SiteMirrorSource.of(tempDir, loaded))
                .renderer(new Pdf2HtmlExAwarePdfRenderer(new OpenHtmlToPdfRenderer(),
                        tempDir.resolve("reader-pages")))
                .assemblyStrategy(new ManifestOrderPdfAssemblyStrategy())
                .output(outputPdf)
                .build()
                .run();

        // Verify downstream pipeline stages ran successfully
        assertEquals(2, artifact.assemblyReport().pagesRendered(),
                "both pages must render after loading from existing mirror");
        assertEquals(0, artifact.assemblyReport().pagesFailed());
        assertTrue(Files.exists(outputPdf), "output PDF must be created");

        final byte[] bytes = Files.readAllBytes(outputPdf);
        assertTrue(bytes.length >= 4);
        assertEquals('%', (char) bytes[0]);
        assertEquals('P', (char) bytes[1]);
        assertEquals('D', (char) bytes[2]);
        assertEquals('F', (char) bytes[3]);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static MirrorManifest buildManifest(final List<MirroredPage> pages) {
        return MirrorManifest.builder()
                .startUrl(SEED).sameDomainOnly(true).maxPages(50).maxDepth(3)
                .pages(pages).build();
    }

    private static MirroredPage successPage(final String id, final String url,
            final String localHtmlPath) {
        return successPageOrdered(id, url, localHtmlPath, 0L);
    }

    private static MirroredPage successPageOrdered(final String id, final String url,
            final String localHtmlPath, final long discoveredOrder) {
        return MirroredPage.builder()
                .id(id).url(URI.create(url)).canonicalUrl(URI.create(url))
                .localHtmlPath(localHtmlPath).depth(0).discoveredOrder(discoveredOrder)
                .status(200).fetchedAt(FETCHED_AT).mirrorStatus(MirrorStatus.SUCCESS).build();
    }
}
