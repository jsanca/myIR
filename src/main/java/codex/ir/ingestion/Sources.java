package codex.ir.ingestion;

import java.net.URI;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public final class Sources {

    private Sources() {}

    public static DocumentSource<WebPage> webPage(final URI... rootUris) {
        return webPage(defaultConfig(), rootUris);
    }

    public static DocumentSource<WebPage> webPage(final WebCrawlingConfig config,
                                                  final URI... rootUris) {

        Objects.requireNonNull(rootUris, "rootUris must not be null");
        if (rootUris.length == 0) {
            throw new IllegalArgumentException("At least one root URI is required");
        }
        Objects.requireNonNull(config, "config must not be null");

        return new WebPageDocumentSource(config, Set.of(rootUris));
    }

    private static WebCrawlingConfig defaultConfig() {
        return WebCrawlingConfig.defaultConfig();
    }

    private static class WebPageDocumentSource implements DocumentSource<WebPage> {

        private final WebCrawlingConfig config;
        private final Set<URI> rootUris;

        public WebPageDocumentSource(final WebCrawlingConfig config, final Set<URI> rootUris) {
            this.config = config;
            this.rootUris = rootUris;
        }

        @Override
        public void readInto(final Consumer<WebPage> consumer) {


        }
    }
}
