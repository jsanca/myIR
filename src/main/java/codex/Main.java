package codex;

import codex.ir.*;

import java.util.List;
import java.util.Optional;

public class Main {

    public static void main(String[] args) {

        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory();
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = new DocumentIndexer(corpus, invertedIndex, tokenizer, normalizer);

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

        final Searcher searcher = new SimpleSearcher(invertedIndex, corpus, tokenizer, normalizer);

        System.out.println("Search for 'java': " + searcher.searchDetailed("java"));
        System.out.println("Search for 'search': " + searcher.searchDetailed("search"));
        System.out.println("Search for 'engine': " + searcher.searchDetailed("engine"));
    }
}