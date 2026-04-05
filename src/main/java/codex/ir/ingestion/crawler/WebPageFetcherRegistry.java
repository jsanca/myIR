package codex.ir.ingestion.crawler;

public interface WebPageFetcherRegistry extends AutoCloseable {

    WebPageFetcher staticHtml();
    WebPageFetcher dynamicHtml();
}
