package codex.apps.siteexporter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/** Serializes an {@link AssetManifest} to a JSON file using Jackson object binding. */
final class AssetManifestWriter {

    private static final ObjectMapper MAPPER = createMapper();

    private static ObjectMapper createMapper() {
        final SimpleModule module = new SimpleModule()
                .addSerializer(Instant.class, new JsonSerializer<>() {
                    @Override
                    public void serialize(final Instant value, final JsonGenerator gen,
                            final SerializerProvider p) throws IOException {
                        gen.writeString(value.toString());
                    }
                });
        return new ObjectMapper().registerModule(module);
    }

    void write(final AssetManifest manifest, final Path destination) throws IOException {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(destination, "destination");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(destination.toFile(), manifest);
    }
}
