package codex.ir.ingestion.crawler;

import codex.ir.ingestion.DocumentSource;
import codex.ir.ingestion.WebCrawlingConfig;
import codex.ir.ingestion.WebPage;

import java.net.URI;

public interface CrawlerRuntime extends AutoCloseable {

    WebPageFetcherRegistry fetcherRegistry(WebCrawlingConfig config);

    WebPageSourceStrategy siteTraversal(WebCrawlingConfig config, URI... rootUris);

    DocumentSource<WebPage> webPageSource(WebCrawlingConfig config, URI... rootUris);

    @Override
    void close() throws Exception;
}
