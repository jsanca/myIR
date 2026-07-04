package codex.apps.siteexporter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * Deserializes a {@link MirrorManifest} from a JSON file using Jackson object binding.
 *
 * <p>Derived manifest counts ({@code documentCount}, {@code successfulCount}, etc.) are
 * recomputed from the {@code pages} array by {@link MirrorManifest.Builder#build()};
 * the stored values in JSON are intentionally ignored.</p>
 */
public final class ManifestReader {

    private static final ObjectMapper MAPPER = createMapper();

    private static ObjectMapper createMapper() {
        final SimpleModule module = new SimpleModule()
                .addDeserializer(Instant.class, new JsonDeserializer<>() {
                    @Override
                    public Instant deserialize(final JsonParser p,
                            final DeserializationContext ctx) throws IOException {
                        return Instant.parse(p.getText());
                    }
                });
        return new ObjectMapper()
                .registerModule(module)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Reads and deserializes a manifest from {@code source}.
     *
     * @param source path to the manifest JSON file; must exist and be readable
     * @return the deserialized manifest with counts derived from the pages list
     * @throws IOException if the file cannot be read or is not valid manifest JSON
     */
    public MirrorManifest read(final Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        return MAPPER.readValue(source.toFile(), MirrorManifest.class);
    }
}
