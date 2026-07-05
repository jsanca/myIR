package codex.apps.siteexporter;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiteExporterCommandTest {

    @Test
    void shouldParseRequiredUrlFlag() {
        final SiteExporterCommand.ParsedArgs args = SiteExporterCommand.parseArgs(
                new String[]{"--url", "https://example.com/"});

        assertEquals(URI.create("https://example.com/"), args.seedUrl());
    }

    @Test
    void shouldUseDefaultsWhenOnlyUrlProvided() {
        final SiteExporterCommand.ParsedArgs args = SiteExporterCommand.parseArgs(
                new String[]{"--url", "https://example.com/"});

        assertEquals(Path.of("./mirror"), args.outputDir());
        assertEquals(100, args.maxPages());
        assertEquals(3, args.maxDepth());
        assertTrue(args.sameDomainOnly());
        assertEquals(PublicationFormat.PDF, args.format());
        assertEquals(Path.of("./output.pdf"), args.outputPath());
    }

    @Test
    void shouldParseAllFlags() {
        final SiteExporterCommand.ParsedArgs args = SiteExporterCommand.parseArgs(new String[]{
                "--url", "https://site.com/",
                "--out-dir", "./my-mirror",
                "--max-pages", "50",
                "--max-depth", "2",
                "--no-same-domain",
                "--format", "epub",
                "--output", "./site.epub"
        });

        assertEquals(URI.create("https://site.com/"), args.seedUrl());
        assertEquals(Path.of("./my-mirror"), args.outputDir());
        assertEquals(50, args.maxPages());
        assertEquals(2, args.maxDepth());
        assertEquals(false, args.sameDomainOnly());
        assertEquals(PublicationFormat.EPUB, args.format());
        assertEquals(Path.of("./site.epub"), args.outputPath());
    }

    @Test
    void shouldThrowWhenUrlFlagIsMissing() {
        assertThrows(IllegalArgumentException.class, () ->
                SiteExporterCommand.parseArgs(new String[]{"--out-dir", "./mirror"}));
    }

    @Test
    void sameDomainShouldDefaultToTrue() {
        final SiteExporterCommand.ParsedArgs args = SiteExporterCommand.parseArgs(
                new String[]{"--url", "https://example.com/"});

        assertTrue(args.sameDomainOnly());
    }

    @Test
    void noSameDomainFlagShouldSetSameDomainOnlyToFalse() {
        final SiteExporterCommand.ParsedArgs args = SiteExporterCommand.parseArgs(
                new String[]{"--url", "https://example.com/", "--no-same-domain"});

        assertEquals(false, args.sameDomainOnly());
    }

    // ------------------------------------------------------------------
    // --from-mirror flag
    // ------------------------------------------------------------------

    @Test
    void parseArgsShouldSetFromMirrorDir() {
        final SiteExporterCommand.ParsedArgs args = SiteExporterCommand.parseArgs(
                new String[]{"--from-mirror", "/tmp/my-mirror"});

        assertEquals(Path.of("/tmp/my-mirror"), args.fromMirrorDir());
    }

    @Test
    void parseArgsShouldNotRequireUrlWhenFromMirrorIsProvided() {
        // Must not throw — --url is optional when --from-mirror is given
        final SiteExporterCommand.ParsedArgs args = SiteExporterCommand.parseArgs(
                new String[]{"--from-mirror", "/tmp/my-mirror"});

        assertNotNull(args);
        assertNull(args.seedUrl(), "seedUrl must be null in resume mode (read from manifest later)");
    }

    @Test
    void parseArgsShouldUseFromMirrorDirAsDefaultOutputDir() {
        final SiteExporterCommand.ParsedArgs args = SiteExporterCommand.parseArgs(
                new String[]{"--from-mirror", "/tmp/my-mirror"});

        assertEquals(Path.of("/tmp/my-mirror"), args.outputDir(),
                "outputDir must default to fromMirrorDir when --out-dir is not provided");
    }

    @Test
    void parseArgsShouldRespectExplicitOutDirEvenWithFromMirror() {
        final SiteExporterCommand.ParsedArgs args = SiteExporterCommand.parseArgs(
                new String[]{"--from-mirror", "/tmp/my-mirror", "--out-dir", "/tmp/custom-out"});

        assertEquals(Path.of("/tmp/custom-out"), args.outputDir(),
                "explicit --out-dir must override fromMirrorDir as the default outputDir");
    }

    @Test
    void shouldThrowWhenNeitherUrlNorFromMirrorIsProvided() {
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SiteExporterCommand.parseArgs(new String[]{"--out-dir", "./mirror"}));

        final String msg = ex.getMessage();
        assertTrue(msg.contains("--url") || msg.contains("--from-mirror"),
                "error must mention both required flags");
    }

    @Test
    void parseArgsShouldAcceptBothUrlAndFromMirrorTogether() {
        // Both flags may be supplied simultaneously (--url is ignored for the mirror phase)
        final SiteExporterCommand.ParsedArgs args = SiteExporterCommand.parseArgs(new String[]{
                "--url", "https://example.com/",
                "--from-mirror", "/tmp/existing-mirror"
        });

        assertEquals(URI.create("https://example.com/"), args.seedUrl());
        assertEquals(Path.of("/tmp/existing-mirror"), args.fromMirrorDir());
    }

    @Test
    void parseArgsShouldLeaveFromMirrorDirNullInNormalMode() {
        final SiteExporterCommand.ParsedArgs args = SiteExporterCommand.parseArgs(
                new String[]{"--url", "https://example.com/"});

        assertNull(args.fromMirrorDir(),
                "fromMirrorDir must be null when --from-mirror is not provided");
    }

    // ------------------------------------------------------------------
    // Format-dependent default output path
    // ------------------------------------------------------------------

    @Test
    void parseArgsShouldDefaultToMarkdownOutputPathForMarkdownFormat() {
        final SiteExporterCommand.ParsedArgs args = SiteExporterCommand.parseArgs(
                new String[]{"--url", "https://example.com/", "--format", "markdown"});

        assertEquals(Path.of("./output.md"), args.outputPath(),
                "default output path must be ./output.md when --format markdown is used");
    }

    @Test
    void parseArgsShouldDefaultToEpubOutputPathForEpubFormat() {
        final SiteExporterCommand.ParsedArgs args = SiteExporterCommand.parseArgs(
                new String[]{"--url", "https://example.com/", "--format", "epub"});

        assertEquals(Path.of("./output.epub"), args.outputPath(),
                "default output path must be ./output.epub when --format epub is used");
    }

    @Test
    void parseArgsShouldUseExplicitOutputPathEvenForMarkdownFormat() {
        final SiteExporterCommand.ParsedArgs args = SiteExporterCommand.parseArgs(new String[]{
                "--url", "https://example.com/",
                "--format", "markdown",
                "--output", "./mybook.md"
        });

        assertEquals(Path.of("./mybook.md"), args.outputPath(),
                "explicit --output must override the format-derived default");
    }
}
