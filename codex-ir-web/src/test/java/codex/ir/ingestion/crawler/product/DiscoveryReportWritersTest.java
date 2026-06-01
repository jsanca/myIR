package codex.ir.ingestion.crawler.product;

import codex.ir.ingestion.crawler.classifier.ClassifiedUrl;
import codex.ir.ingestion.crawler.classifier.PageClassification;
import codex.ir.ingestion.crawler.classifier.UrlType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoveryReportWritersTest {

    private static final URI PAGE_URI = URI.create("https://example.com/product/widget");
    private static final ClassifiedUrl CLASSIFIED_URL = new ClassifiedUrl(PAGE_URI, UrlType.PRODUCT);

    private static ProductDiscoveryReport sampleReport() {
        final ProductImage image = new ProductImage(
                URI.create("https://example.com/img/widget.jpg"), "A widget photo", 0);
        final ProductPrice price = new ProductPrice(new BigDecimal("19.99"), "USD");
        final ProductDetail detail = new ProductDetail(
                PAGE_URI, "Super Widget",
                Optional.of("WIDGET-001"),
                Optional.of(price),
                Optional.empty(),
                Optional.of("A high-quality widget."),
                List.of(image),
                Optional.of("Widget Corp"),
                Optional.of("In Stock"));
        final ProductDiscoveryResult result = new ProductDiscoveryResult(
                PAGE_URI,
                new PageClassification(PAGE_URI, UrlType.PRODUCT, CLASSIFIED_URL, false, false),
                Optional.of(detail),
                List.of());
        return new ProductDiscoveryReport(List.of(result), List.of(detail), List.of());
    }

    @Test
    void consoleWriterShouldProduceOutput() {
        final DiscoveryReportWriter writer = DiscoveryReportWriters.console();
        final ProductDiscoveryReport report = sampleReport();
        final ByteArrayOutputStream captured = new ByteArrayOutputStream();
        final PrintStream originalOut = System.out;
        System.setOut(new PrintStream(captured));
        try {
            writer.write(report);
        } finally {
            System.setOut(originalOut);
        }
        final String output = captured.toString();
        assertTrue(output.contains("Product Discovery Report"));
        assertTrue(output.contains("Super Widget"));
        assertTrue(output.contains("WIDGET-001"));
        assertTrue(output.contains("19.99 USD"));
        assertTrue(output.contains("Images: 1"));
    }

    @Test
    void jsonFileWriterShouldCreateFileWithContent(@TempDir final Path tempDir) throws Exception {
        final DiscoveryReportWriter writer = DiscoveryReportWriters.jsonFile(tempDir);
        final ProductDiscoveryReport report = sampleReport();
        writer.write(report);

        final List<Path> jsonFiles;
        try (final var files = Files.list(tempDir)) {
            jsonFiles = files.filter(p -> p.getFileName().toString().startsWith("product-discovery-report-"))
                    .toList();
        }
        assertEquals(1, jsonFiles.size(), "Expected exactly one JSON file");

        final String json = Files.readString(jsonFiles.get(0));
        assertTrue(json.contains("\"Super Widget\""));
        assertTrue(json.contains("\"WIDGET-001\""));
        assertTrue(json.contains("\"Widget Corp\""));
        assertTrue(json.contains("\"In Stock\""));
        assertTrue(json.contains("19.99"));
        assertTrue(json.contains("\"USD\""));
        assertTrue(json.contains("\"A high-quality widget.\""));
        assertTrue(json.contains("widget.jpg"));
    }

    @Test
    void jsonFileWriterShouldHandleEmptyReport(@TempDir final Path tempDir) {
        final DiscoveryReportWriter writer = DiscoveryReportWriters.jsonFile(tempDir);
        writer.write(ProductDiscoveryReport.empty());
        // Should not throw — file is created without error
    }

    @Test
    void compositeWriterShouldDelegateToAll() {
        final ProductDiscoveryReport report = sampleReport();
        final ByteArrayOutputStream captured = new ByteArrayOutputStream();
        final PrintStream originalOut = System.out;
        System.setOut(new PrintStream(captured));
        try {
            final DiscoveryReportWriter writer = DiscoveryReportWriters.composite(
                    DiscoveryReportWriters.console(),
                    DiscoveryReportWriters.console());
            writer.write(report);
        } finally {
            System.setOut(originalOut);
        }
        final String output = captured.toString();
        final int count = output.split("Product Discovery Report").length - 1;
        assertEquals(2, count, "CompositeWriter should delegate to both writers");
    }

    @Test
    void jsonFileFactoryShouldRejectNull() {
        assertThrows(NullPointerException.class, () -> DiscoveryReportWriters.jsonFile(null));
    }

    @Test
    void compositeFactoryShouldRejectNull() {
        assertThrows(NullPointerException.class, () -> DiscoveryReportWriters.composite((DiscoveryReportWriter[]) null));
    }

    @Test
    void consoleWriterShouldRejectNullReport() {
        final DiscoveryReportWriter writer = DiscoveryReportWriters.console();
        assertThrows(NullPointerException.class, () -> writer.write(null));
    }

    @Test
    void jsonFileWriterShouldRejectNullReport(@TempDir final Path tempDir) {
        final DiscoveryReportWriter writer = DiscoveryReportWriters.jsonFile(tempDir);
        assertThrows(NullPointerException.class, () -> writer.write(null));
    }
}
