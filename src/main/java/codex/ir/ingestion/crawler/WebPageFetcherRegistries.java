package codex.ir.ingestion.crawler;

import codex.ir.ingestion.WebCrawlingConfig;

import java.util.Objects;

public final class WebPageFetcherRegistries {

    private WebPageFetcherRegistries() {
    }

    public static WebPageFetcherRegistry simple(final WebCrawlingConfig.HttpClientConfig httpClientConfig) {

        return new SimpleWebPageFetcherRegistry(httpClientConfig);
    }

    private static final class SimpleWebPageFetcherRegistry implements WebPageFetcherRegistry {

        private final WebCrawlingConfig.HttpClientConfig httpClientConfig;
        private volatile WebPageFetcher staticHtmlFetcher;
        private volatile WebPageFetcher dynamicHtmlFetcher;

        SimpleWebPageFetcherRegistry(final WebCrawlingConfig.HttpClientConfig httpClientConfig) {
            this.httpClientConfig = Objects.requireNonNull(httpClientConfig, "httpClientConfig must not be null");
        }

        @Override
        public WebPageFetcher staticHtml() {
            WebPageFetcher current = this.staticHtmlFetcher;
            if (current != null) {
                return current;
            }

            synchronized (this) {
                if (this.staticHtmlFetcher == null) {
                    this.staticHtmlFetcher = WebPageFetchers.staticHtml(this.httpClientConfig);
                }
                return this.staticHtmlFetcher;
            }
        }

        @Override
        public WebPageFetcher dynamicHtml() {
            WebPageFetcher current = this.dynamicHtmlFetcher;
            if (current != null) {
                return current;
            }

            synchronized (this) {
                if (this.dynamicHtmlFetcher == null) {
                    this.dynamicHtmlFetcher = WebPageFetchers.dynamicHtml();
                }
                return this.dynamicHtmlFetcher;
            }
        }

        @Override
        public void close() throws Exception {
            closeIfPossible(this.staticHtmlFetcher);
            closeIfPossible(this.dynamicHtmlFetcher);
            this.staticHtmlFetcher = null;
            this.dynamicHtmlFetcher = null;
        }

        private void closeIfPossible(final WebPageFetcher fetcher) throws Exception {
            if (fetcher instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }
}
