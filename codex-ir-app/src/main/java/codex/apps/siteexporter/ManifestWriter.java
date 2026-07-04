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

/** Serializes a {@link MirrorManifest} to a JSON file using Jackson object binding. */
public final class ManifestWriter {

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

    /**
     * Writes {@code manifest} as pretty-printed JSON to {@code destination}.
     *
     * @param manifest    the manifest to serialize; must not be {@code null}
     * @param destination the file to write; parent directory must exist
     * @throws IOException if the file cannot be written
     */
    public void write(final MirrorManifest manifest, final Path destination) throws IOException {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(destination, "destination");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(destination.toFile(), manifest);
    }
}
