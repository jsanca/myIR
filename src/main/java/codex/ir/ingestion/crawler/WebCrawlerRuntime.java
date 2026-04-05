package codex.ir.ingestion.crawler;

import codex.ir.ingestion.DocumentSource;
import codex.ir.ingestion.Sources;
import codex.ir.ingestion.WebCrawlingConfig;
import codex.ir.ingestion.WebPage;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WebCrawlerRuntime implements CrawlerRuntime {

    private WebCrawlerRuntime() {}

    private static final WebCrawlerRuntime INSTANCE = new WebCrawlerRuntime();

    public static WebCrawlerRuntime getInstance() {
        return INSTANCE;
    }

    private static final Map<String, WebPageFetcherRegistry> WEB_PAGE_FETCHER_REGISTRY_MAP = new ConcurrentHashMap<>();
    @Override
    public WebPageFetcherRegistry fetcherRegistry(final WebCrawlingConfig config) {
        return WEB_PAGE_FETCHER_REGISTRY_MAP.computeIfAbsent(config.configId(), ignore -> WebPageFetcherRegistries.simple(config.httpClientConfig()));
    }

    @Override
    public WebPageSourceStrategy siteTraversal(final WebCrawlingConfig config, final URI... rootUris) {
        return WebPageSourceStrategies.siteTraversal(config, fetcherRegistry(config), rootUris);
    }

    @Override
    public DocumentSource<WebPage> webPageSource(final WebCrawlingConfig config, final URI... rootUris) {
        return Sources.webPage(siteTraversal(config, rootUris));
    }

    @Override
    public void close() throws Exception {

        for (final WebPageFetcherRegistry registry : WEB_PAGE_FETCHER_REGISTRY_MAP.values()) {
            registry.close();
        }
        WEB_PAGE_FETCHER_REGISTRY_MAP.clear();
    }
}
