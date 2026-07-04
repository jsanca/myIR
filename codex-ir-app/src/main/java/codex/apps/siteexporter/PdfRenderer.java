package codex.apps.siteexporter;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Renders a single local HTML file to PDF bytes.
 *
 * <p>Implementations are provided in Phase 8 (e.g. an OpenHTMLToPDF adapter).
 * Phase 7 uses fake implementations for pipeline composition tests.</p>
 */
public interface PdfRenderer {

    /**
     * Renders the given HTML file and returns the PDF content as a byte array.
     *
     * @param htmlFile absolute path to the HTML file to render
     * @return PDF bytes; never {@code null}, never empty on success
     * @throws IOException if the file cannot be read or rendering fails
     */
    byte[] render(Path htmlFile) throws IOException;
}
