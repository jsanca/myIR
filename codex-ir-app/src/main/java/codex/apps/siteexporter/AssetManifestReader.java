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

/** Deserializes an {@link AssetManifest} from a JSON file using Jackson object binding. */
final class AssetManifestReader {

    private static final ObjectMapper MAPPER = createMapper();

    private static ObjectMapper createMapper() {
        final SimpleModule module = new SimpleModule()
                .addDeserializer(Instant.class, new JsonDeserializer<>() {
                    @Override
                    public Instant deserialize(final JsonParser p, final DeserializationContext ctx)
                            throws IOException {
                        return Instant.parse(p.getText());
                    }
                });
        return new ObjectMapper()
                .registerModule(module)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    AssetManifest read(final Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        return MAPPER.readValue(source.toFile(), AssetManifest.class);
    }
}
