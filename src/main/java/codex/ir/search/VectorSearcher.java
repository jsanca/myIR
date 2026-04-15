package codex.ir.search;

import codex.ir.Document;
import codex.ir.corpus.Corpus;
import codex.ir.corpus.vector.Vocabulary;
import codex.ir.normalizer.Normalizer;
import codex.ir.tokenizer.Tokenizer;
import codex.ir.vector.*;
import codex.ir.vector.store.DocumentVectorStore;
import codex.ir.weight.DocumentWeighter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class VectorSearcher implements Searcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(VectorSearcher.class);
    private final Corpus corpus;
    private final Vocabulary vocabulary;
    private final SparseVectorizer sparseVectorizer;
    private final DocumentWeighter documentWeighter;
    private final DocumentVectorStore vectorStore;
    private final Tokenizer tokenizer;
    private final Normalizer normalizer;
    private final Similarity<SparseDocumentVector> similarity;
    private final double threshold;

    public VectorSearcher(final Corpus corpus,
                          final Vocabulary vocabulary,
                          final SparseVectorizer sparseVectorizer,
                          final DocumentWeighter documentWeighter,
                          final DocumentVectorStore vectorStore,
                          final Tokenizer tokenizer,
                          final Normalizer normalizer,
                          final Similarity<SparseDocumentVector> similarity,
                          final double threshold) {

        this.corpus = Objects.requireNonNull(corpus, "Corpus should be non null");
        this.vocabulary = Objects.requireNonNull(vocabulary, "Vocabulary should be non null");
        this.sparseVectorizer = Objects.requireNonNull(sparseVectorizer, "SparseVectorizer should be non null");
        this.documentWeighter = Objects.requireNonNull(documentWeighter, "DocumentWeighter should be non null");
        this.vectorStore = Objects.requireNonNull(vectorStore, "DocumentVectorStore should be non null");
        this.tokenizer = Objects.requireNonNull(tokenizer, "Tokenizer should be non null");
        this.normalizer = Objects.requireNonNull(normalizer, "Normalizer should be non null");
        this.similarity = Objects.requireNonNull(similarity, "Similarity should be non null");
        if (threshold < 0.0d) {
            throw new IllegalArgumentException("threshold must be >= 0");
        }
        this.threshold = threshold;
    }

    @Override
    public List<Document> search(final String query) {

        Objects.requireNonNull(query, "query should be non null");
        return this.searchDetailed(query)
                .stream()
                .map(SearchResult::document)
                .collect(Collectors.toList());
    }

    @Override
    public List<SearchResult> searchDetailed(final String query) {
        Objects.requireNonNull(query, "query should be non null");

        LOGGER.info("Starting vector search for query='{}'", query);

        final Document documentQuery = preprocessQuery(query);
        final Map<String, Double> weights = this.documentWeighter.weigh(this.corpus, documentQuery);
        LOGGER.debug("Computed {} query term weight(s) for query='{}'", weights.size(), query);

        final SparseDocumentVector queryVector = this.sparseVectorizer.vectorize(documentQuery.id(), weights);
        final List<SearchResult> retrievedResults = new ArrayList<>();

        for (final SparseDocumentVector documentVector : this.vectorStore.iterateAll()) {

            final SimilarityResult similarityResult =  this.similarity.similarity(queryVector, documentVector);
            final double score = similarityResult.score();
            if (score <= this.threshold) {
                continue;
            }

            final Optional<Document> optDocument = this.corpus.get(documentVector.documentId());
            final Document document = optDocument.orElse(null);
            if (document != null) {
                final List<String> matchedTerms = Optional.ofNullable(similarityResult.matches()).orElse(List.of())
                        .stream().sorted(Comparator.comparingDouble(SimilarityMatch::contribution).reversed())
                        .map(similarityMatch -> this.vocabulary.getTerm(similarityMatch.dimension()).orElse("unknown term"))
                        .distinct().toList();
                retrievedResults.add(new SearchResult(document.id(), document, score, matchedTerms));
            } else {
                LOGGER.warn("Vector store contains document id={} but corpus does not", documentVector.documentId());
            }
        }

        LOGGER.info("Finished vector search for query='{}' with {} result(s)", query, retrievedResults.size());
        return retrievedResults.stream().sorted(Comparator.comparingDouble(SearchResult::score).reversed()).toList();
    }

    private Document preprocessQuery(final String query) {
        final List<String> normalizedTokens = new ArrayList<>();

        for (final String token : this.tokenizer.tokenize(query)) {
            if (token == null || token.isBlank()) {
                continue;
            }

            final Optional<String> normalized = this.normalizer.normalize(token);
            if (normalized.isEmpty() || normalized.get().isBlank()) {
                continue;
            }

            normalizedTokens.add(normalized.get());
        }

        final String normalizedContent = String.join(" ", normalizedTokens);
        return Document.builder()
                .id(UUID.randomUUID().toString())
                .normalizedContent(normalizedContent)
                .build();
    }
}
