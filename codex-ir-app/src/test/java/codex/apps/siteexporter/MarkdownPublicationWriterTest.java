package codex.apps.siteexporter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownPublicationWriterTest {

    private static final URI SEED = URI.create("https://book.example.com/");
    private static final Instant FETCHED_AT = Instant.parse("2026-07-04T12:00:00Z");

    // ------------------------------------------------------------------
    // Null guards
    // ------------------------------------------------------------------

    @Test
    void builderShouldThrowWhenSourceIsNull() {
        assertThrows(NullPointerException.class, () ->
                MarkdownPublicationWriter.builder().build());
    }

    @Test
    void writeShouldThrowWhenOutputIsNull(@TempDir final Path tempDir) {
        final MarkdownPublicationWriter writer = writerWithManifest(tempDir, List.of());
        assertThrows(NullPointerException.class, () -> writer.write(null));
    }

    // ------------------------------------------------------------------
    // Output file creation
    // ------------------------------------------------------------------

    @Test
    void writeShouldCreateOutputFile(@TempDir final Path tempDir) throws Exception {
        final Path output = tempDir.resolve("output.md");
        writerWithManifest(tempDir, List.of()).write(output);

        assertTrue(Files.exists(output), "output file must be created");
    }

    @Test
    void writeShouldCreateOutputParentDirectoriesIfAbsent(@TempDir final Path tempDir)
            throws Exception {
        final Path output = tempDir.resolve("nested/dir/output.md");
        writerWithManifest(tempDir, List.of()).write(output);

        assertTrue(Files.exists(output));
    }

    // ------------------------------------------------------------------
    // Document header format
    // ------------------------------------------------------------------

    @Test
    void writeShouldStartWithLevelOneHeadingContainingSeedHost(@TempDir final Path tempDir)
            throws Exception {
        final Path output = tempDir.resolve("output.md");
        writerWithManifest(tempDir, List.of()).write(output);

        final String md = Files.readString(output);
        assertTrue(md.startsWith("# book.example.com"),
                "Markdown must start with a level-1 heading containing the seed host");
    }

    @Test
    void writeShouldIncludeDocumentLevelSourceComment(@TempDir final Path tempDir)
            throws Exception {
        final Path output = tempDir.resolve("output.md");
        writerWithManifest(tempDir, List.of()).write(output);

        final String md = Files.readString(output);
        assertTrue(md.contains("<!-- source: " + SEED + " -->"),
                "document-level source comment must contain the seed URL");
    }

    // ------------------------------------------------------------------
    // Section headings and per-page source comments
    // ------------------------------------------------------------------

    @Test
    void writeShouldIncludeSectionHorizontalRuleForEachPage(@TempDir final Path tempDir)
            throws Exception {
        Files.writeString(tempDir.resolve("page.html"),
                "<html><body><p>Hello</p></body></html>");
        final Path output = tempDir.resolve("output.md");
        writerWithManifest(tempDir, List.of(
                page("id-1", "https://book.example.com/hello", "page.html", 0L)
        )).write(output);

        assertTrue(Files.readString(output).contains("---"),
                "each page section must be preceded by a horizontal rule");
    }

    @Test
    void writeShouldDeriveSectionHeadingFromPageUrlPath(@TempDir final Path tempDir)
            throws Exception {
        Files.writeString(tempDir.resolve("chapter.html"),
                "<html><body><p>Content</p></body></html>");
        final Path output = tempDir.resolve("output.md");
        writerWithManifest(tempDir, List.of(
                page("id-1", "https://book.example.com/chapter", "chapter.html", 0L)
        )).write(output);

        assertTrue(Files.readString(output).contains("## chapter"),
                "section heading must use the last URL path segment");
    }

    @Test
    void writeShouldIncludePerPageSourceComment(@TempDir final Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("intro.html"),
                "<html><body><p>Intro</p></body></html>");
        final Path output = tempDir.resolve("output.md");
        writerWithManifest(tempDir, List.of(
                page("id-1", "https://book.example.com/intro", "intro.html", 0L)
        )).write(output);

        final String md = Files.readString(output);
        assertTrue(md.contains("<!-- source: https://book.example.com/intro -->"),
                "per-page source comment must contain the page URL");
    }

    // ------------------------------------------------------------------
    // Content extraction — normal HTML
    // ------------------------------------------------------------------

    @Test
    void writeShouldExtractParagraphTextFromNormalHtml(@TempDir final Path tempDir)
            throws Exception {
        Files.writeString(tempDir.resolve("page.html"),
                "<html><body><p>Hello world</p><p>Second paragraph</p></body></html>");
        final Path output = tempDir.resolve("output.md");
        writerWithManifest(tempDir, List.of(
                page("id-1", "https://book.example.com/page", "page.html", 0L)
        )).write(output);

        final String md = Files.readString(output);
        assertTrue(md.contains("Hello world"), "first paragraph must appear in output");
        assertTrue(md.contains("Second paragraph"), "second paragraph must appear in output");
    }

    @Test
    void writeShouldExtractHeadingTextFromNormalHtml(@TempDir final Path tempDir)
            throws Exception {
        Files.writeString(tempDir.resolve("page.html"),
                "<html><body><h1>Chapter Title</h1><p>Body text.</p></body></html>");
        final Path output = tempDir.resolve("output.md");
        writerWithManifest(tempDir, List.of(
                page("id-1", "https://book.example.com/chapter", "page.html", 0L)
        )).write(output);

        final String md = Files.readString(output);
        assertTrue(md.contains("Chapter Title"), "heading text must appear in output");
        assertTrue(md.contains("Body text."), "paragraph text must appear in output");
    }

    @Test
    void writeShouldExtractListItemTextFromNormalHtml(@TempDir final Path tempDir)
            throws Exception {
        Files.writeString(tempDir.resolve("page.html"),
                "<html><body><ul><li>First item</li><li>Second item</li></ul></body></html>");
        final Path output = tempDir.resolve("output.md");
        writerWithManifest(tempDir, List.of(
                page("id-1", "https://book.example.com/list", "page.html", 0L)
        )).write(output);

        final String md = Files.readString(output);
        assertTrue(md.contains("First item"), "list items must appear in output");
        assertTrue(md.contains("Second item"));
    }

    // ------------------------------------------------------------------
    // Content extraction — pdf2htmlEX
    // ------------------------------------------------------------------

    @Test
    void writeShouldExtractPdf2HtmlExContentContainingExpectedStrings(
            @TempDir final Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("chapter.html"),
                Pdf2HtmlExDetectorTest.loadFixture());
        final Path output = tempDir.resolve("output.md");
        writerWithManifest(tempDir, List.of(
                page("id-1", "https://book.example.com/chapter", "chapter.html", 0L)
        )).write(output);

        final String md = Files.readString(output);
        assertTrue(md.contains("Part I"),
                "output must contain 'Part I' extracted from pdf2htmlEX fixture");
        assertTrue(md.contains("Applied Math and Machine Learning Basics"),
                "output must contain subtitle text");
        assertTrue(md.contains("This part of the book introduces"),
                "output must contain body paragraph text");
    }

    @Test
    void writeShouldNotContainCssOrScriptFromPdf2HtmlExPage(@TempDir final Path tempDir)
            throws Exception {
        Files.writeString(tempDir.resolve("chapter.html"),
                Pdf2HtmlExDetectorTest.loadFixture());
        final Path output = tempDir.resolve("output.md");
        writerWithManifest(tempDir, List.of(
                page("id-1", "https://book.example.com/chapter", "chapter.html", 0L)
        )).write(output);

        final String md = Files.readString(output);
        assertFalse(md.contains("@font-face"),
                "Markdown output must not contain @font-face declarations");
        assertFalse(md.contains("<script"),
                "Markdown output must not contain script tags");
        assertFalse(md.contains("position:absolute"),
                "Markdown output must not contain positioning CSS");
    }

    // ------------------------------------------------------------------
    // Multi-page ordering
    // ------------------------------------------------------------------

    @Test
    void writeShouldPreservePublicationOrder(@TempDir final Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("first.html"),
                "<html><body><p>First page content</p></body></html>");
        Files.writeString(tempDir.resolve("second.html"),
                "<html><body><p>Second page content</p></body></html>");

        final Path output = tempDir.resolve("output.md");
        writerWithManifest(tempDir, List.of(
                page("id-1", "https://book.example.com/first", "first.html", 0L),
                page("id-2", "https://book.example.com/second", "second.html", 1L)
        )).write(output);

        final String md = Files.readString(output);
        final int firstIdx = md.indexOf("First page content");
        final int secondIdx = md.indexOf("Second page content");
        assertTrue(firstIdx < secondIdx,
                "pages must appear in discoveredOrder (first before second)");
    }

    @Test
    void writeShouldReturnCorrectPageCount(@TempDir final Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("p1.html"),
                "<html><body><p>Page one</p></body></html>");
        Files.writeString(tempDir.resolve("p2.html"),
                "<html><body><p>Page two</p></body></html>");

        final PublicationArtifact artifact = writerWithManifest(tempDir, List.of(
                page("id-1", "https://book.example.com/p1", "p1.html", 0L),
                page("id-2", "https://book.example.com/p2", "p2.html", 1L)
        )).write(tempDir.resolve("output.md"));

        assertEquals(2, artifact.assemblyReport().pagesAttempted());
        assertEquals(2, artifact.assemblyReport().pagesRendered());
        assertEquals(0, artifact.assemblyReport().pagesFailed());
    }

    // ------------------------------------------------------------------
    // Artifact metadata
    // ------------------------------------------------------------------

    @Test
    void writeShouldReturnMarkdownFormat(@TempDir final Path tempDir) throws Exception {
        final PublicationArtifact artifact =
                writerWithManifest(tempDir, List.of()).write(tempDir.resolve("output.md"));

        assertEquals(PublicationFormat.MARKDOWN, artifact.format());
    }

    @Test
    void writeShouldReturnCorrectOutputPath(@TempDir final Path tempDir) throws Exception {
        final Path output = tempDir.resolve("output.md");
        final PublicationArtifact artifact = writerWithManifest(tempDir, List.of()).write(output);

        assertEquals(output, artifact.path());
    }

    @Test
    void writeShouldReturnPositiveSizeBytes(@TempDir final Path tempDir) throws Exception {
        final PublicationArtifact artifact =
                writerWithManifest(tempDir, List.of()).write(tempDir.resolve("output.md"));

        assertTrue(artifact.sizeBytes() > 0, "artifact size must be positive (header text)");
    }

    // ------------------------------------------------------------------
    // Per-page Markdown files
    // ------------------------------------------------------------------

    @Test
    void writeShouldCreatePerPageMarkdownFilesWhenDirConfigured(@TempDir final Path tempDir)
            throws Exception {
        Files.writeString(tempDir.resolve("page.html"),
                "<html><body><p>Content</p></body></html>");

        final Path markdownPagesDir = tempDir.resolve("markdown-pages");
        MarkdownPublicationWriter.builder()
                .source(SiteMirrorSource.of(tempDir, manifest(List.of(
                        page("id-1", "https://book.example.com/page", "page.html", 0L)))))
                .markdownPagesDir(markdownPagesDir)
                .build()
                .write(tempDir.resolve("output.md"));

        assertTrue(Files.exists(markdownPagesDir.resolve("page.html.md")),
                "per-page .md file must be created under markdownPagesDir");
    }

    @Test
    void writeShouldNotCreateMarkdownPagesDirWhenNotConfigured(@TempDir final Path tempDir)
            throws Exception {
        Files.writeString(tempDir.resolve("page.html"),
                "<html><body><p>Content</p></body></html>");

        final Path markdownPagesDir = tempDir.resolve("markdown-pages");
        MarkdownPublicationWriter.builder()
                .source(SiteMirrorSource.of(tempDir, manifest(List.of(
                        page("id-1", "https://book.example.com/page", "page.html", 0L)))))
                // no markdownPagesDir
                .build()
                .write(tempDir.resolve("output.md"));

        assertFalse(Files.exists(markdownPagesDir),
                "markdown-pages directory must not be created when not configured");
    }

    @Test
    void perPageMarkdownFileShouldContainSameContentAsSection(@TempDir final Path tempDir)
            throws Exception {
        Files.writeString(tempDir.resolve("intro.html"),
                "<html><body><p>Introduction text</p></body></html>");

        final Path markdownPagesDir = tempDir.resolve("markdown-pages");
        final Path output = tempDir.resolve("output.md");
        MarkdownPublicationWriter.builder()
                .source(SiteMirrorSource.of(tempDir, manifest(List.of(
                        page("id-1", "https://book.example.com/intro", "intro.html", 0L)))))
                .markdownPagesDir(markdownPagesDir)
                .build()
                .write(output);

        final String pageFile = Files.readString(markdownPagesDir.resolve("intro.html.md"));
        assertTrue(pageFile.contains("Introduction text"),
                "per-page .md file must contain the extracted paragraph text");
    }

    // ------------------------------------------------------------------
    // Markdown escaping
    // ------------------------------------------------------------------

    @Test
    void writeShouldEscapeBackslashesInExtractedText(@TempDir final Path tempDir)
            throws Exception {
        Files.writeString(tempDir.resolve("page.html"),
                "<html><body><p>Path: C:\\Users\\foo</p></body></html>");
        final Path output = tempDir.resolve("output.md");
        writerWithManifest(tempDir, List.of(
                page("id-1", "https://book.example.com/page", "page.html", 0L)
        )).write(output);

        assertTrue(Files.readString(output).contains("C:\\\\Users\\\\foo"),
                "backslashes must be escaped as double-backslashes");
    }

    @Test
    void writeShouldEscapeBracketsInExtractedText(@TempDir final Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("page.html"),
                "<html><body><p>See [RFC 1234]</p></body></html>");
        final Path output = tempDir.resolve("output.md");
        writerWithManifest(tempDir, List.of(
                page("id-1", "https://book.example.com/page", "page.html", 0L)
        )).write(output);

        final String md = Files.readString(output);
        assertTrue(md.contains("\\[RFC 1234\\]"),
                "square brackets must be escaped to prevent unintended link syntax");
    }

    @Test
    void writeShouldEscapeLeadingHashesInExtractedText(@TempDir final Path tempDir)
            throws Exception {
        Files.writeString(tempDir.resolve("page.html"),
                "<html><body><p>## Not a heading</p></body></html>");
        final Path output = tempDir.resolve("output.md");
        writerWithManifest(tempDir, List.of(
                page("id-1", "https://book.example.com/page", "page.html", 0L)
        )).write(output);

        final String md = Files.readString(output);
        assertTrue(md.contains("\\## Not a heading"),
                "leading ## in extracted text must be escaped to prevent heading creation");
    }

    // ------------------------------------------------------------------
    // Failure handling
    // ------------------------------------------------------------------

    @Test
    void writeShouldRecordFailureWhenHtmlFileIsMissing(@TempDir final Path tempDir)
            throws Exception {
        // Do NOT create the referenced HTML file
        final Path output = tempDir.resolve("output.md");
        final PublicationArtifact artifact = writerWithManifest(tempDir, List.of(
                page("id-1", "https://book.example.com/missing", "missing.html", 0L)
        )).write(output);

        assertEquals(1, artifact.assemblyReport().pagesFailed(),
                "missing HTML file must be recorded as a render failure");
        assertEquals(0, artifact.assemblyReport().pagesRendered());
    }

    @Test
    void writeShouldSucceedForSuccessfulPagesEvenIfSomePagesMissing(
            @TempDir final Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("ok.html"),
                "<html><body><p>OK page</p></body></html>");
        // "missing.html" intentionally not created

        final Path output = tempDir.resolve("output.md");
        final PublicationArtifact artifact = writerWithManifest(tempDir, List.of(
                page("id-1", "https://book.example.com/ok", "ok.html", 0L),
                page("id-2", "https://book.example.com/missing", "missing.html", 1L)
        )).write(output);

        assertEquals(1, artifact.assemblyReport().pagesRendered(),
                "ok page must be counted as rendered even when another page fails");
        assertEquals(1, artifact.assemblyReport().pagesFailed());
        assertTrue(Files.readString(output).contains("OK page"));
    }

    // ------------------------------------------------------------------
    // Empty manifest
    // ------------------------------------------------------------------

    @Test
    void writeShouldHandleEmptyManifestGracefully(@TempDir final Path tempDir) throws Exception {
        final Path output = tempDir.resolve("output.md");
        final PublicationArtifact artifact = writerWithManifest(tempDir, List.of()).write(output);

        assertTrue(Files.exists(output), "output file must be created even for empty manifest");
        assertEquals(0, artifact.assemblyReport().pagesAttempted());
        assertEquals(0, artifact.assemblyReport().pagesRendered());
        final String md = Files.readString(output);
        assertTrue(md.contains("# book.example.com"),
                "header must still be written for empty manifest");
    }

    // ------------------------------------------------------------------
    // escapeMarkdown unit tests
    // ------------------------------------------------------------------

    @Test
    void escapeMarkdownShouldReturnEmptyForNullOrEmpty() {
        assertEquals("", MarkdownPublicationWriter.escapeMarkdown(null));
        assertEquals("", MarkdownPublicationWriter.escapeMarkdown(""));
    }

    @Test
    void escapeMarkdownShouldEscapeBackslash() {
        assertEquals("a\\\\b", MarkdownPublicationWriter.escapeMarkdown("a\\b"));
    }

    @Test
    void escapeMarkdownShouldEscapeBrackets() {
        assertEquals("\\[link\\]", MarkdownPublicationWriter.escapeMarkdown("[link]"));
    }

    @Test
    void escapeMarkdownShouldEscapeLeadingHashAtLineStart() {
        assertEquals("\\## Section", MarkdownPublicationWriter.escapeMarkdown("## Section"));
    }

    @Test
    void escapeMarkdownShouldNotEscapeHashInMiddleOfLine() {
        assertEquals("foo ## bar",
                MarkdownPublicationWriter.escapeMarkdown("foo ## bar"),
                "# in the middle of a line must NOT be escaped");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static MarkdownPublicationWriter writerWithManifest(final Path contentDir,
            final List<MirroredPage> pages) {
        return MarkdownPublicationWriter.builder()
                .source(SiteMirrorSource.of(contentDir, manifest(pages)))
                .build();
    }

    private static MirrorManifest manifest(final List<MirroredPage> pages) {
        return MirrorManifest.builder()
                .startUrl(SEED).sameDomainOnly(true).maxPages(50).maxDepth(3)
                .pages(pages).build();
    }

    private static MirroredPage page(final String id, final String url,
            final String localHtmlPath, final long discoveredOrder) {
        return MirroredPage.builder()
                .id(id).url(URI.create(url)).canonicalUrl(URI.create(url))
                .localHtmlPath(localHtmlPath).depth(0).discoveredOrder(discoveredOrder)
                .status(200).fetchedAt(FETCHED_AT).mirrorStatus(MirrorStatus.SUCCESS).build();
    }
}
