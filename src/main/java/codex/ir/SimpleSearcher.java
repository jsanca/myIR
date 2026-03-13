package codex.ir;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
 *  - returns either documents or richer {@link SearchResult} instances
 */
public class SimpleSearcher implements Searcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleSearcher.class);

    private final InvertedIndex invertedIndex;
    private final Corpus corpus;
    private final Tokenizer tokenizer;
    private final Normalizer normalizer;

    /**
     * Creates a new searcher backed by the given inverted index, corpus,
     * tokenizer, and normalizer.
     *
     * @param invertedIndex index used to retrieve matching document ids
     * @param corpus corpus used to resolve document ids into documents
     * @param tokenizer tokenizer used to split the incoming query
     * @param normalizer normalizer used to normalize query tokens
     */
    public SimpleSearcher(final InvertedIndex invertedIndex,
                          final Corpus corpus,
                          final Tokenizer tokenizer,
                          final Normalizer normalizer) {
        this.invertedIndex = Objects.requireNonNull(invertedIndex);
        this.corpus = Objects.requireNonNull(corpus);
        this.tokenizer = Objects.requireNonNull(tokenizer);
        this.normalizer = Objects.requireNonNull(normalizer);
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
        final Map<String, Set<String>> matchedTermsByDocumentId = new LinkedHashMap<>();

        for (final String token : queryTokens) {
            final Optional<String> normalized = this.normalizer.normalize(token);

            if (normalized.isEmpty()) {
                continue;
            }

            final String term = normalized.get();
            LOGGER.trace("Searching normalized term '{}'", term);

            final Set<String> documentIds = this.invertedIndex.search(term);
            for (final String documentId : documentIds) {
                matchedTermsByDocumentId
                        .computeIfAbsent(documentId, ignored -> new LinkedHashSet<>())
                        .add(term);
            }
        }

        LOGGER.debug("Found {} documents", matchedTermsByDocumentId.size());

        final List<SearchResult> results = new ArrayList<>();
        for (final Map.Entry<String, Set<String>> entry : matchedTermsByDocumentId.entrySet()) {
            final String documentId = entry.getKey();
            final Optional<Document> document = this.corpus.get(documentId);

            if (document.isEmpty()) {
                LOGGER.warn("Document id '{}' was found in the index but not in the corpus", documentId);
                continue;
            }

            results.add(new SearchResult(
                    documentId,
                    document.get(),
                    List.copyOf(entry.getValue())
            ));
        }

        return results;
    }
}
