package codex.apps.siteexporter;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Renders a single local HTML file to a {@link RenderedPdf}.
 *
 * <p>The primary implementation is {@link OpenHtmlToPdfRenderer}.
 * Tests use inline lambda fakes.</p>
 */
public interface PdfRenderer {

    /**
     * Renders the given HTML file to PDF using the provided options.
     *
     * @param htmlFile absolute path to the HTML file to render; must exist
     * @param options  rendering configuration (page size, base URI); must not be {@code null}
     * @return the rendered PDF; never {@code null}
     * @throws IOException if the file cannot be read or rendering fails
     */
    RenderedPdf render(Path htmlFile, PdfRenderOptions options) throws IOException;
}
