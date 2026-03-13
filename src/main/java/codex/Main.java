package codex;

import codex.ir.*;

import java.util.List;

public class Main {

    public static void main(String[] args) {

        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.basic();
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();

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

            final List<String> tokens = tokenizer.tokenize(document.rawContent());

            for (final String token : tokens) {

                final String normalizedTerm = normalizer.normalize(token);

                if (!normalizedTerm.isBlank()) {
                    invertedIndex.add(normalizedTerm, document.id());
                }
            }
        }

        System.out.println("Search for 'java': " + invertedIndex.search("java"));
        System.out.println("Search for 'search': " + invertedIndex.search("search"));
        System.out.println("Search for 'engine': " + invertedIndex.search("engine"));
    }
}