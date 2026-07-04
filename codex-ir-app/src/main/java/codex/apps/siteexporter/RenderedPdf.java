package codex.apps.siteexporter;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The result of rendering a single HTML file to PDF.
 *
 * <p>{@code bytes} is a defensive copy — callers can mutate their copy without
 * affecting this record, and mutations to the original array passed at
 * construction do not affect the stored value.</p>
 */
public record RenderedPdf(byte[] bytes, Path sourceFile) {

    public RenderedPdf {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(sourceFile, "sourceFile");
        bytes = bytes.clone();
    }

    /** Returns the byte length of the rendered PDF. */
    public long sizeBytes() {
        return bytes.length;
    }

    /** Returns a copy of the PDF bytes. */
    public byte[] bytes() {
        return bytes.clone();
    }
}
