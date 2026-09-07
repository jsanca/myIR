package codex.ir.search;

import codex.ir.*;
import codex.ir.corpus.CorpusSnapshot;
import codex.ir.indexer.IndexSnapshot;
import codex.ir.indexer.Posting;
import codex.ir.normalizer.Normalizer;
import codex.ir.tokenizer.Tokenizer;
import codex.ir.ranking.Ranker;
import codex.ir.ranking.RankingContext;
import codex.ir.ranking.ScoreExplanation;
import codex.ir.ranking.TermScoring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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
public class SimpleSearcher implements ExplainableSearcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleSearcher.class);

    private final IndexSnapshot invertedIndex;
    private final CorpusSnapshot corpus;
    private final Tokenizer tokenizer;
    private final Normalizer normalizer;
    private final Ranker ranker;
    private final RankingContext rankingContext;

    /**
     * Creates a new searcher with whole-document (non-field-aware) scoring.
     *
     * @param invertedIndex frozen index snapshot used to retrieve matching document ids
     * @param corpus frozen corpus snapshot used to resolve document ids into documents
     * @param tokenizer tokenizer used to split the incoming query
     * @param normalizer normalizer used to normalize query tokens
     * @param ranker ranker used to score and order matched documents
     */
    public SimpleSearcher(final IndexSnapshot invertedIndex,
                          final CorpusSnapshot corpus,
                          final Tokenizer tokenizer,
                          final Normalizer normalizer,
                          final Ranker ranker) {
        this(invertedIndex, corpus, tokenizer, normalizer, ranker, RankingContext.neutral());
    }

    /**
     * Creates a new searcher with field-aware scoring.
     *
     * <p>When {@code rankingContext} is {@link RankingContext#neutral()} the scores are
     * identical to the no-context constructor. Pass a context with explicit
     * {@link codex.ir.ranking.FieldWeights} to boost terms that appear in high-value fields.</p>
     *
     * @param invertedIndex frozen index snapshot used to retrieve matching document ids
     * @param corpus frozen corpus snapshot used to resolve document ids into documents
     * @param tokenizer tokenizer used to split the incoming query
     * @param normalizer normalizer used to normalize query tokens
     * @param ranker ranker used to score and order matched documents
     * @param rankingContext per-request ranking configuration
     */
    public SimpleSearcher(final IndexSnapshot invertedIndex,
                          final CorpusSnapshot corpus,
                          final Tokenizer tokenizer,
                          final Normalizer normalizer,
                          final Ranker ranker,
                          final RankingContext rankingContext) {
        this.invertedIndex = Objects.requireNonNull(invertedIndex);
        this.corpus = Objects.requireNonNull(corpus);
        this.tokenizer = Objects.requireNonNull(tokenizer);
        this.normalizer = Objects.requireNonNull(normalizer);
        this.ranker = Objects.requireNonNull(ranker);
        this.rankingContext = Objects.requireNonNull(rankingContext);
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
     * Null, empty, and blank queries return an empty result list.
     *
     * @param query raw user query
     * @return detailed search results containing documents and matched terms
     */
    @Override
    public List<SearchResult> searchDetailed(final String query) {
        if (query == null || query.isBlank()) {
            LOGGER.debug("Search query is null or blank. Returning empty results.");
            return List.of();
        }

        LOGGER.debug("Search query {}", query);

        final List<String> analyzedTerms = analyzeQuery(query);
        final Map<String, Set<String>> matchedTermsByDocumentIdMap = new LinkedHashMap<>();
        final Map<String, Double> scoreByDocumentIdMap = new LinkedHashMap<>();

        for (final String normalizedTerm : analyzedTerms) {
            LOGGER.trace("Searching normalized term '{}'", normalizedTerm);

            final List<Posting> postings = this.invertedIndex.getPostings(normalizedTerm);

            for (final Posting posting : postings) {

                final String documentId = posting.documentId();
                matchedTermsByDocumentIdMap
                        .computeIfAbsent(documentId, ignored -> new LinkedHashSet<>())
                        .add(normalizedTerm);

                final double contribution = this.ranker.score(normalizedTerm, posting, this.rankingContext);
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


    /**
     * Returns a score explanation for {@code documentId} relative to the analyzed query.
     *
     * <p>Returns {@link Optional#empty()} when:
     * <ul>
     *   <li>{@code query} is {@code null} or blank;</li>
     *   <li>{@code documentId} is {@code null} or not present in the corpus snapshot;</li>
     *   <li>the analyzed query is empty (all tokens are stop words);</li>
     *   <li>no analyzed term has a posting for {@code documentId}.</li>
     * </ul>
     *
     * <p>The analysis pipeline and scoring path are identical to those used by
     * {@link #searchDetailed}, guaranteeing that score conservation holds (UC-6, T-02, T-27).
     *
     * @param query      raw user query
     * @param documentId identifier of the document to explain
     * @return score explanation, or empty
     */
    @Override
    public Optional<ScoreExplanation> explain(final String query, final String documentId) {

        final Optional<List<String>> termsOpt = this.analyzeQueryIfValid(query, documentId);

        if (termsOpt.isPresent()) {

            final List<TermScoring> contributions = new ArrayList<>();

            for (final String term : termsOpt.get()) {
                invertedIndex.getPostings(term).stream()
                        .filter(posting -> documentId.equals(posting.documentId()))
                        .findFirst()
                        .ifPresent(posting ->
                                contributions.add(
                                        ranker.evaluate(term, posting, rankingContext)
                                )
                        );
            }

            if (!contributions.isEmpty()) {

                double score = 0.0;
                for (final TermScoring termScoring : contributions) {
                    score = Double.sum(score, termScoring.contribution());
                }
                return Optional.of(new ScoreExplanation(query, documentId, score, contributions));
            }
        }

        return Optional.empty();
    }

    private Optional<List<String>> analyzeQueryIfValid(
            final String query,
            final String documentId) {

        if (query == null || query.isBlank()) {
            return Optional.empty();
        }

        if (documentId == null || corpus.get(documentId).isEmpty()) {
            return Optional.empty();
        }

        final List<String> terms = analyzeQuery(query);

        return terms.isEmpty()
                ? Optional.empty()
                : Optional.of(terms);
    }

    /**
     * Tokenizes and normalizes {@code query}, returning analyzed terms in encounter order.
     * Stop words and tokens that normalize to empty are excluded. Duplicate terms are
     * preserved — if the same word appears twice, it appears twice in the returned list.
     *
     * <p>Both {@link #searchDetailed} and {@link #explain} delegate to this method so the
     * analysis pipeline is never duplicated (UC-6, T-27).
     *
     * @param query raw user query (must not be null/blank — callers guard before calling)
     * @return ordered list of normalized terms; may be empty if all tokens are stop words
     */
    private List<String> analyzeQuery(final String query) {
        final List<String> tokens = tokenizer.tokenize(query);
        final List<String> terms = new ArrayList<>(tokens.size());
        for (final String token : tokens) {
            normalizer.normalize(token).ifPresent(terms::add);
        }
        return terms;
    }
}
