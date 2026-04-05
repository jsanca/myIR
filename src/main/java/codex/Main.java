package codex;

import codex.ir.*;
import codex.ir.corpus.Corpora;
import codex.ir.corpus.Corpus;
import codex.ir.indexer.DocumentIndexer;
import codex.ir.indexer.Indexer;
import codex.ir.indexer.InvertedIndex;
import codex.ir.indexer.InvertedIndexes;
import codex.ir.ingestion.*;
import codex.ir.ingestion.crawler.CrawlerRuntime;
import codex.ir.ingestion.crawler.WebCrawlerRuntime;
import codex.ir.ingestion.crawler.WebPageFetcherRegistries;
import codex.ir.normalizer.Normalizer;
import codex.ir.normalizer.Normalizers;
import codex.ir.ranking.Ranker;
import codex.ir.ranking.Rankers;
import codex.ir.search.SearchResult;
import codex.ir.search.Searcher;
import codex.ir.search.SimpleSearcher;
import codex.ir.tokenizer.Tokenizer;
import codex.ir.tokenizer.Tokenizers;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Main {

    public static void main(String[] args) {
        //runInMemoryDemo();
         runWebCrawlingDemo();
    }

    private static void runInMemoryDemo() {

        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory();
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = new DocumentIndexer(corpus, invertedIndex, tokenizer, normalizer);
        final Ranker ranker = Rankers.tfIdf(corpus, invertedIndex);

        final String text1 = "Java is a programming language";
        final String text2 = "A search engine uses an inverted index";
        final String text3 = "Java can be used to build a search engine";

        final Document doc1 = Document.builder()
                .id("doc1.txt")
                .rawContent(text1)
                .normalizedContent(text1)
                .build();

        final Document doc2 = Document.builder()
                .id("doc2.txt")
                .rawContent(text2)
                .normalizedContent(text2)
                .build();

        final Document doc3 = Document.builder()
                .id("doc3.txt")
                .rawContent(text3)
                .normalizedContent(text3)
                .build();

        final List<Document> documents = List.of(doc1, doc2, doc3);

        for (final Document document : documents) {
            indexer.index(document);
        }

        final Searcher searcher = new SimpleSearcher(invertedIndex, corpus, tokenizer, normalizer, ranker);

        System.out.println("How many docs are in my corpus: " + corpus.size());

        printResults("java", searcher.searchDetailed("java"));
        printResults("search", searcher.searchDetailed("search"));
        printResults("engine", searcher.searchDetailed("engine"));
    }

    private static void runWebCrawlingDemo() {

        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory();
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = new DocumentIndexer(corpus, invertedIndex, tokenizer, normalizer);
        final Ranker ranker = Rankers.bm25(corpus, invertedIndex);

        final WebCrawlingConfig config = WebCrawlingConfig.builder()
                .maxDepth(1)
                .maxPages(10)
                .sameDomainOnly(true)
                .followExternalLinks(false)
                .build();

        final URI rootUri = URI.create("https://web-scraping.dev/");
        final Set<Object> managedInstances = new LinkedHashSet<>();
        final CrawlerRuntime crawlerRuntime = WebCrawlerRuntime.getInstance();

        try {

            final DocumentSource<WebPage> documentSource = crawlerRuntime.webPageSource(config, rootUri);
            final DocumentMapper<WebPage> documentMapper = Mappers.webPage();
            final DocumentIngestionService<WebPage> ingestionService = Ingestors.simple();

            managedInstances.add(corpus);
            managedInstances.add(invertedIndex);
            managedInstances.add(indexer);
            managedInstances.add(documentSource);

            ingestionService.ingest(documentSource, documentMapper, indexer);

            final Searcher searcher = new SimpleSearcher(invertedIndex, corpus, tokenizer, normalizer, ranker);
            managedInstances.add(searcher);
            managedInstances.add(crawlerRuntime);

            System.out.println("How many docs are in my crawled corpus: " + corpus.size());
            printResults("scraping", searcher.searchDetailed("scraping"));
            printResults("html", searcher.searchDetailed("html"));
            printResults("data", searcher.searchDetailed("data"));
        } catch (final Exception exception ) {

            exception.printStackTrace(System.err);
        } finally {

            closeManagedInstances(managedInstances);
        }
    }

    private static void closeManagedInstances(final Set<Object> managedInstances) {
        for (final Object instance : managedInstances) {
            if (instance instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (final Exception exception) {
                    System.err.println("Failed to close instance: " + instance.getClass().getName());
                    exception.printStackTrace(System.err);
                }
            }
        }
    }

    private static void printResults(final String query, final List<SearchResult> results) {
        System.out.println("\nResults for query: '" + query + "'");

        if (results.isEmpty()) {
            System.out.println("  No results found.");
            return;
        }

        int rank = 1;
        for (final SearchResult result : results) {
            System.out.println("  #" + rank++);
            System.out.println("    docId : " + result.documentId());
            System.out.println("    score : " + result.score());
            System.out.println("    terms : " + result.matchedTerms());
            System.out.println("    text  : " + result.document().rawContent());
            System.out.println();
        }
    }
}