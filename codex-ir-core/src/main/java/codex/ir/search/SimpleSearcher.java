package codex.ir.search;

import codex.ir.*;
import codex.ir.corpus.Corpus;
import codex.ir.indexer.InvertedIndex;
import codex.ir.indexer.Posting;
import codex.ir.normalizer.Normalizer;
import codex.ir.tokenizer.Tokenizer;
import codex.ir.ranking.Ranker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Simple in-memory implementation of {@link Searcher}.
 *
 * This searcher applies the same query-time analysis pipeline used during
 * indexing: tokenization followed by normalization.
 *
 * Current behavior:
 *  - tokenizes the incoming query
 *  - normalizes each query token
 *  - performs lookup in the inverted index for each normalized term
 *  - merges matches using union semantics
 *  - scores results using the configured {@link Ranker}
 *  - returns either documents or richer {@link SearchResult} instances
 */
public class SimpleSearcher implements Searcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleSearcher.class);

    private final InvertedIndex invertedIndex;
    private final Corpus corpus;
    private final Tokenizer tokenizer;
    private final Normalizer normalizer;
    private final Ranker ranker;

    /**
     * Creates a new searcher backed by the given inverted index, corpus,
     * tokenizer, and normalizer.
     *
     * @param invertedIndex index used to retrieve matching document ids
     * @param corpus corpus used to resolve document ids into documents
     * @param tokenizer tokenizer used to split the incoming query
     * @param normalizer normalizer used to normalize query tokens
     * @param ranker ranker used to score and order matched documents
     */
    public SimpleSearcher(final InvertedIndex invertedIndex,
                          final Corpus corpus,
                          final Tokenizer tokenizer,
                          final Normalizer normalizer,
                          final Ranker ranker) {
        this.invertedIndex = Objects.requireNonNull(invertedIndex);
        this.corpus = Objects.requireNonNull(corpus);
        this.tokenizer = Objects.requireNonNull(tokenizer);
        this.normalizer = Objects.requireNonNull(normalizer);
        this.ranker = Objects.requireNonNull(ranker);
    }

    /**
     * Executes a search query and returns only the matched documents.
     *
     * This method delegates to {@link #searchDetailed(String)} and projects
     * the detailed results back to plain documents.
     *
     * @param query raw user query
     * @return list of matching documents
     */
    @Override
    public List<Document> search(final String query) {
        return this.searchDetailed(query).stream()
                .map(SearchResult::document)
                .toList();
    }

    /**
     * Executes a search query and returns rich result objects.
     *
     * The incoming query is tokenized and normalized using the same analysis
     * pipeline used for document indexing. Matching document ids from all
     * normalized query terms are merged using union semantics, while keeping
     * track of which normalized terms matched each document.
     *
     * @param query raw user query
     * @return detailed search results containing documents and matched terms
     */
    @Override
    public List<SearchResult> searchDetailed(final String query) {
        Objects.requireNonNull(query);
        LOGGER.debug("Search query {}", query);

        final List<String> queryTokens = this.tokenizer.tokenize(query);
        final Map<String, Set<String>> matchedTermsByDocumentIdMap = new LinkedHashMap<>();
        final Map<String, Double> scoreByDocumentIdMap = new LinkedHashMap<>();

        for (final String token : queryTokens) {
            final Optional<String> normalizedTokenOpt = this.normalizer.normalize(token);

            if (normalizedTokenOpt.isEmpty()) {
                continue;
            }

            final String normalizedTerm = normalizedTokenOpt.get();
            LOGGER.trace("Searching normalized term '{}'", normalizedTerm);

            final List<Posting> postings = this.invertedIndex.getPostings(normalizedTerm);

            for (final Posting posting : postings) {

                final String documentId = posting.documentId();
                matchedTermsByDocumentIdMap
                        .computeIfAbsent(documentId, ignored -> new LinkedHashSet<>())
                        .add(normalizedTerm);

                final double contribution = this.ranker.score(normalizedTerm, posting);
                scoreByDocumentIdMap.merge(documentId, contribution, Double::sum);
            }
        }

        LOGGER.debug("Found {} documents", matchedTermsByDocumentIdMap.size());

        final List<SearchResult> results = new ArrayList<>();
        for (final Map.Entry<String, Set<String>> entry : matchedTermsByDocumentIdMap.entrySet()) {
            final String documentId = entry.getKey();
            final Optional<Document> document = this.corpus.get(documentId);

            if (document.isEmpty()) {
                LOGGER.warn("Document id '{}' was found in the index but not in the corpus", documentId);
                continue;
            }

            final Set<String> matchingTerms = entry.getValue();
            final double score = scoreByDocumentIdMap.getOrDefault(documentId, 0.0d);
            results.add(new SearchResult(
                    documentId,
                    document.get(),
                    score,
                    List.copyOf(matchingTerms)
            ));
        }

        results.sort(Comparator.comparingDouble(SearchResult::score).reversed());

        LOGGER.trace("Ordered results by score: {}", results.stream()
                .map(result -> result.documentId() + "=" + result.score())
                .toList());

        return results;
    }
}
