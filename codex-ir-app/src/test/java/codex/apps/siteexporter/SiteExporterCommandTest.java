package codex.apps.siteexporter;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
