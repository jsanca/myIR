package codex.ir.ingestion.crawler.product;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;

/**
 * Factory for {@link DiscoveryReportWriter} implementations.
 */
public final class DiscoveryReportWriters {

    private DiscoveryReportWriters() {
    }

    /**
     * Returns a writer that prints the report to {@link System#out} in a
     * human-readable format.
     *
     * @return a console-backed report writer
     */
    public static DiscoveryReportWriter console() {
        return new ConsoleWriter();
    }

    /**
     * Returns a writer that serializes the report as JSON to a
     * timestamped file in the given directory.
     *
     * <p>The output file is named
     * {@code product-discovery-report-}{@code yyyyMMdd-HHmmss}{@code .json}.</p>
     *
     * @param outputDir the directory to write into; must exist and be writable
     * @return a JSON-file-backed report writer
     */
    public static DiscoveryReportWriter jsonFile(final Path outputDir) {
        Objects.requireNonNull(outputDir, "outputDir must not be null");
        return new JsonFileWriter(outputDir);
    }

    /**
     * Returns a writer that delegates to all of the given writers in order.
     *
     * @param writers the writers to delegate to; must not be {@code null}
     * @return a composite report writer
     */
    public static DiscoveryReportWriter composite(final DiscoveryReportWriter... writers) {
        Objects.requireNonNull(writers, "writers must not be null");
        final DiscoveryReportWriter[] copy = writers.clone();
        return new CompositeWriter(copy);
    }

    // ------------------------------------------------------------------
    // ConsoleWriter
    // ------------------------------------------------------------------

    private static final class ConsoleWriter implements DiscoveryReportWriter {

        @Override
        public void write(final ProductDiscoveryReport report) {
            Objects.requireNonNull(report, "report must not be null");
            final String sep = "=".repeat(60);
            System.out.println(sep);
            System.out.println("  Product Discovery Report");
            System.out.println(sep);
            System.out.printf("  Pages analyzed : %d%n", report.pageResults().size());
            System.out.printf("  Product details: %d%n", report.productDetails().size());
            System.out.printf("  Product cards  : %d%n", report.productCards().size());
            System.out.println();

            System.out.println("--- Per-page classification ---");
            for (final ProductDiscoveryResult result : report.pageResults()) {
                System.out.printf("  %-50s  %s%n",
                        result.pageUri(), result.pageClassification().type());
            }

            if (!report.productDetails().isEmpty()) {
                System.out.println();
                System.out.printf("--- Product Details (%d) ---%n", report.productDetails().size());
                int idx = 1;
                for (final ProductDetail d : report.productDetails()) {
                    System.out.printf("%n  [%d] %s%n", idx++, d.name());
                    System.out.println("      URL   : " + d.url());
                    d.sku().ifPresent(v ->              System.out.println("      SKU   : " + v));
                    d.brand().ifPresent(v ->            System.out.println("      Brand : " + v));
                    d.regularPrice().ifPresent(p ->     System.out.println("      Price : " + formatPrice(p)));
                    d.availability().ifPresent(v ->     System.out.println("      Avail : " + v));
                    d.shortDescription().ifPresent(v -> System.out.println("      Desc  : " + truncate(v, 80)));
                    System.out.println("      Images: " + d.images().size());
                }
            }

            if (!report.productCards().isEmpty()) {
                System.out.println();
                System.out.printf("--- Product Cards (%d) ---%n", report.productCards().size());
                int idx = 1;
                for (final ProductCard c : report.productCards()) {
                    System.out.printf("%n  [%d] %s%n", idx++, c.name());
                    System.out.println("      URL   : " + c.url());
                    c.regularPrice().ifPresent(p -> System.out.println("      Price : " + formatPrice(p)));
                    c.thumbnail().ifPresent(img ->  System.out.println("      Image : " + img.url()));
                }
            }

            System.out.println();
            System.out.println(sep);
        }

        private static String formatPrice(final ProductPrice price) {
            final String symbol = price.currencySymbol().orElse("");
            return symbol.isEmpty()
                    ? price.amount().toPlainString()
                    : price.amount().toPlainString() + " " + symbol;
        }

        private static String truncate(final String text, final int maxLen) {
            return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
        }
    }

    // ------------------------------------------------------------------
    // JsonFileWriter
    // ------------------------------------------------------------------

    private static final class JsonFileWriter implements DiscoveryReportWriter {

        private static final ObjectMapper MAPPER = new ObjectMapper()
                .registerModule(optionalModule())
                .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
                .enable(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN);

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static SimpleModule optionalModule() {
            final SimpleModule module = new SimpleModule();
            module.addSerializer(Optional.class, new JsonSerializer<Optional>() {
                @Override
                public void serialize(final Optional value, final JsonGenerator gen,
                                      final SerializerProvider provider) throws IOException {
                    if (value.isPresent()) {
                        provider.defaultSerializeValue(value.get(), gen);
                    } else {
                        gen.writeNull();
                    }
                }
            });
            return module;
        }

        private static final DateTimeFormatter TIMESTAMP_FORMAT =
                DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

        private final Path outputDir;

        private JsonFileWriter(final Path outputDir) {
            this.outputDir = outputDir;
        }

        @Override
        public void write(final ProductDiscoveryReport report) {
            Objects.requireNonNull(report, "report must not be null");
            final String timestamp = TIMESTAMP_FORMAT.format(LocalDateTime.now());
            final Path file = outputDir.resolve("product-discovery-report-" + timestamp + ".json");
            try {
                Files.createDirectories(outputDir);
                try (OutputStream out = Files.newOutputStream(file)) {
                    MAPPER.writerWithDefaultPrettyPrinter().writeValue(out, report);
                }
                System.out.println("[OK] Report written to " + file.toAbsolutePath());
            } catch (final IOException e) {
                System.err.println("[ERROR] Failed to write report to " + file.toAbsolutePath()
                        + ": " + e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------
    // CompositeWriter
    // ------------------------------------------------------------------

    private static final class CompositeWriter implements DiscoveryReportWriter {

        private final DiscoveryReportWriter[] writers;

        private CompositeWriter(final DiscoveryReportWriter[] writers) {
            this.writers = writers;
        }

        @Override
        public void write(final ProductDiscoveryReport report) {
            Objects.requireNonNull(report, "report must not be null");
            for (final DiscoveryReportWriter writer : writers) {
                writer.write(report);
            }
        }
    }
}
