package codex.ir.indexer;

import codex.ir.Document;
import codex.ir.corpus.Corpus;
import codex.ir.normalizer.Normalizer;
import codex.ir.tokenizer.Tokenizer;
import codex.ir.vector.SparseDocumentVector;
import codex.ir.vector.SparseVectorizer;
import codex.ir.vector.store.DocumentVectorStore;
import codex.ir.weight.DocumentWeighter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Factory methods for building {@link Indexer} instances.
 * <p>
 * The public API exposes simple indexers while internally composing a
 * preprocessing pipeline and one or more indexing stages.
 */
public final class Indexers {

    private Indexers() {
    }

    /**
     * Creates an indexer for lexical indexing.
     *
     * @param corpus the corpus where preprocessed documents will be stored
     * @param index the inverted index to populate
     * @param tokenizer the tokenizer used during preprocessing
     * @param normalizer the normalizer used during preprocessing
     * @return an indexer that preprocesses and lexically indexes documents
     */
    public static Indexer lexical(final Corpus corpus,
                                  final InvertedIndex index,
                                  final Tokenizer tokenizer,
                                  final Normalizer normalizer) {

        return new PipelineIndexer(new DocumentPreprocessor(tokenizer, normalizer),
                new LexicalIndexer(corpus, index));
    }

    /**
     * Creates an indexer for vector indexing.
     *
     * @param documentWeighter the component used to compute term weights
     * @param sparseVectorizer the vectorizer used to build sparse vectors
     * @param documentVectorStore the store where vectors will be persisted
     * @param corpus the corpus used by the weighter and where preprocessed documents will be stored
     * @param tokenizer the tokenizer used during preprocessing
     * @param normalizer the normalizer used during preprocessing
     * @return an indexer that preprocesses and vector-indexes documents
     */
    public static Indexer vector(final DocumentWeighter documentWeighter,
                                 final SparseVectorizer sparseVectorizer,
                                 final DocumentVectorStore documentVectorStore,
                                 final Corpus corpus,
                                 final Tokenizer tokenizer,
                                 final Normalizer normalizer) {

        return new PipelineIndexer(new DocumentPreprocessor(tokenizer, normalizer),
                new VectorIndexer(documentWeighter, sparseVectorizer, documentVectorStore, corpus));
    }

    /**
     * Creates an indexer that performs lexical and vector indexing using a
     * shared preprocessing stage.
     *
     * @param index the inverted index to populate
     * @param tokenizer the tokenizer used during preprocessing
     * @param normalizer the normalizer used during preprocessing
     * @param documentWeighter the component used to compute term weights
     * @param sparseVectorizer the vectorizer used to build sparse vectors
     * @param documentVectorStore the store where vectors will be persisted
     * @param corpus the corpus where preprocessed documents will be stored
     * @return an indexer that preprocesses documents once and runs both stages
     */
    public static Indexer lexicalAndVector(final InvertedIndex index,
                                           final Tokenizer tokenizer,
                                           final Normalizer normalizer,
                                           final DocumentWeighter documentWeighter,
                                           final SparseVectorizer sparseVectorizer,
                                           final DocumentVectorStore documentVectorStore,
                                           final Corpus corpus) {

        return new PipelineIndexer(new DocumentPreprocessor(tokenizer, normalizer),
                new LexicalIndexer(corpus, index),
                new VectorIndexer(documentWeighter, sparseVectorizer, documentVectorStore, corpus));
    }

    /**
     * {@link Indexer} implementation that preprocesses a document once and then
     * executes multiple indexing stages in sequence.
     * <p>
     * This keeps the client-facing API simple while ensuring that all stages
     * operate on the same canonical preprocessed document.
     *
     * @author jsanca & elo
     */
    /**
     * Strategy interface for pipeline stages that need to resolve the document
     * view they want to consume before indexing.
     * <p>
     * The default pipeline behavior is to pass the preprocessed document as-is.
     * Implementations of this interface may opt into a different document view,
     * such as one retrieved or derived from the corpus.
     */
    @FunctionalInterface
    private interface PipelineDocumentResolver {

        /**
         * Resolves the document instance that should be consumed by the stage.
         *
         * @param document the preprocessed document produced by the pipeline
         * @return the document view to pass to the indexing stage
         */
        Document resolveDocument(Document document);
    }

    private static final class PipelineIndexer implements Indexer {

        private static final Logger LOGGER = LoggerFactory.getLogger(PipelineIndexer.class);

        private final DocumentPreprocessor documentPreprocessor;
        private final List<Indexer> indexers;

        public PipelineIndexer(final DocumentPreprocessor documentPreprocessor,
                               final Indexer... indexers) {
            this(documentPreprocessor,
                    Arrays.asList(Objects.requireNonNull(indexers, "indexers cannot be null")));
        }

        public PipelineIndexer(final DocumentPreprocessor documentPreprocessor,
                               final List<Indexer> indexers) {
            this.documentPreprocessor = Objects.requireNonNull(documentPreprocessor,
                    "documentPreprocessor cannot be null");
            this.indexers = List.copyOf(Objects.requireNonNull(indexers, "indexers cannot be null"));

            LOGGER.info("Creating PipelineIndexer with {} stage(s)", this.indexers.size());

            if (LOGGER.isDebugEnabled()) {
                this.indexers.forEach(indexer -> LOGGER.debug("Registered pipeline stage: {}",
                        indexer.getClass().getName()));
            }
        }

        @Override
        public void index(final Document document) {
            Objects.requireNonNull(document, "document cannot be null");

            LOGGER.info("Starting pipeline indexing for document id={}", document.id());

            final Document preprocessedDocument = this.documentPreprocessor.preprocess(document);
            LOGGER.debug("Finished preprocessing for document id={}", preprocessedDocument.id());

            for (final Indexer indexer : this.indexers) {
                final Document stageDocument = resolveStageDocument(indexer, preprocessedDocument);

                LOGGER.debug("Executing pipeline stage for document id={} with indexer {}",
                        stageDocument.id(),
                        indexer.getClass().getName());
                indexer.index(stageDocument);
                LOGGER.debug("Finished pipeline stage for document id={} with indexer {}",
                        stageDocument.id(),
                        indexer.getClass().getName());
            }

            LOGGER.info("Finished pipeline indexing for document id={}", preprocessedDocument.id());
        }

        private Document resolveStageDocument(final Indexer indexer,
                                             final Document preprocessedDocument) {
            Objects.requireNonNull(indexer, "indexer cannot be null");
            Objects.requireNonNull(preprocessedDocument, "preprocessedDocument cannot be null");

            if (indexer instanceof final PipelineDocumentResolver resolver) {
                final Document resolvedDocument = Objects.requireNonNull(
                        resolver.resolveDocument(preprocessedDocument),
                        "resolvedDocument cannot be null");
                LOGGER.debug("Resolved stage document id={} for indexer {}",
                        resolvedDocument.id(),
                        indexer.getClass().getName());
                return resolvedDocument;
            }

            return preprocessedDocument;
        }
    } // PipelineIndexer

    /**
     * Preprocesses raw documents into a canonical enriched form shared by all
     * indexing stages.
     * <p>
     * The preprocessed document contains normalized content and derived lexical
     * metadata such as document length, unique terms, and term frequencies.
     */
    private static final class DocumentPreprocessor {

        private static final Logger LOGGER = LoggerFactory.getLogger(DocumentPreprocessor.class);

        private final Tokenizer tokenizer;
        private final Normalizer normalizer;

        private DocumentPreprocessor(final Tokenizer tokenizer,
                                     final Normalizer normalizer) {
            this.tokenizer = Objects.requireNonNull(tokenizer, "tokenizer cannot be null");
            this.normalizer = Objects.requireNonNull(normalizer, "normalizer cannot be null");
        }

        public Document preprocess(final Document document) {
            Objects.requireNonNull(document, "document cannot be null");

            LOGGER.debug("Preprocessing document id={}", document.id());

            if (isAlreadyPreprocessed(document)) {
                LOGGER.debug("Document id={} already contains normalized metadata. Reusing as-is.",
                        document.id());
                return document;
            }

            final String content = document.rawContent();
            if (content == null || content.isBlank()) {
                LOGGER.debug("Document id={} has no content. Returning empty preprocessed document.",
                        document.id());
                return Document.builder(document)
                        .normalizedContent("")
                        .length(0)
                        .uniqueTerms(0)
                        .termFrequencies(Map.of())
                        .build();
            }

            final List<String> tokens = this.tokenizer.tokenize(content);
            LOGGER.debug("Document id={} produced {} raw token(s)", document.id(), tokens.size());

            final List<String> normalizedTerms = new ArrayList<>();
            final Map<String, Integer> termFrequencies = new HashMap<>();

            for (final String token : tokens) {
                final Optional<String> normalized = this.normalizer.normalize(token);
                LOGGER.trace("Token '{}' normalized to '{}'", token, normalized);

                if (normalized.isEmpty() || normalized.get().isBlank()) {
                    continue;
                }

                final String term = normalized.get();
                normalizedTerms.add(term);
                termFrequencies.merge(term, 1, Integer::sum);
            }

            final String normalizedContent = String.join(" ", normalizedTerms);
            final int documentLength = normalizedTerms.size();
            final int uniqueTermCount = termFrequencies.size();

            LOGGER.debug("Document id={} preprocessing produced length={} and uniqueTerms={}",
                    document.id(),
                    documentLength,
                    uniqueTermCount);

            return Document.builder(document)
                    .normalizedContent(normalizedContent)
                    .length(documentLength)
                    .uniqueTerms(uniqueTermCount)
                    .termFrequencies(termFrequencies)
                    .build();
        }

        private boolean isAlreadyPreprocessed(final Document document) {
            if (document.normalizedContent() == null
                    || document.metadata() == null
                    || document.metadata().termFrequencies() == null) {
                return false;
            }

            return document.metadata().length() != null
                    && document.metadata().uniqueTerms() != null;
        }
    } // DocumentPreprocessor

    /**
     * {@link Indexer} implementation responsible only for lexical indexing.
     * <p>
     * This stage assumes the incoming document has already been preprocessed,
     * stores the canonical document in the corpus, and populates the inverted
     * index using the normalized content.
     *
     * @author jsanca & elo
     */
    private static final class LexicalIndexer implements Indexer {

        private static final Logger LOGGER = LoggerFactory.getLogger(LexicalIndexer.class);

        private final Corpus corpus;
        private final InvertedIndex index;

        private LexicalIndexer(final Corpus corpus,
                               final InvertedIndex index) {
            this.corpus = Objects.requireNonNull(corpus, "corpus cannot be null");
            this.index = Objects.requireNonNull(index, "index cannot be null");
        }

        @Override
        public void index(final Document document) {
            Objects.requireNonNull(document, "document cannot be null");
            validatePreprocessedDocument(document);

            LOGGER.info("Starting lexical indexing for document id={}", document.id());

            this.corpus.add(document);
            LOGGER.debug("Stored preprocessed document id={} in corpus", document.id());

            final String normalizedContent = document.normalizedContent();
            if (normalizedContent.isBlank()) {
                LOGGER.debug("Document id={} has empty normalized content. Skipping inverted index population.",
                        document.id());
                LOGGER.info("Finished lexical indexing for document id={}", document.id());
                return;
            }

            final String[] normalizedTerms = normalizedContent.split("\\s+");
            LOGGER.debug("Document id={} produced {} normalized term(s) for lexical indexing",
                    document.id(),
                    normalizedTerms.length);

            for (int position = 0; position < normalizedTerms.length; position++) {
                final String term = normalizedTerms[position];
                this.index.add(term, document.id(), position);
                LOGGER.trace("Indexed term '{}' for document id={} at position={}",
                        term,
                        document.id(),
                        position);
            }

            LOGGER.info("Finished lexical indexing for document id={}", document.id());
        }

        private void validatePreprocessedDocument(final Document document) {
            if (document.normalizedContent() == null
                    || document.metadata() == null
                    || document.metadata().termFrequencies() == null
                    || document.metadata().length() == null
                    || document.metadata().uniqueTerms() == null) {
                throw new IllegalArgumentException(
                        "document must be preprocessed before lexical indexing");
            }
        }
    } // LexicalIndexer

    /**
     * {@link Indexer} implementation that builds and stores a sparse vector
     * representation for a preprocessed document.
     * <p>
     * This stage is intended to be executed inside a {@link PipelineIndexer}
     * so that vector weights are computed against the same canonical document
     * representation used by the other indexing stages.
     *
     * @author jsanca & elo
     */
    private static final class VectorIndexer implements Indexer, PipelineDocumentResolver {

        private static final Logger LOGGER = LoggerFactory.getLogger(VectorIndexer.class);

        private final DocumentWeighter documentWeighter;
        private final SparseVectorizer sparseVectorizer;
        private final DocumentVectorStore documentVectorStore;
        private final Corpus corpus;

        private VectorIndexer(final DocumentWeighter documentWeighter,
                              final SparseVectorizer sparseVectorizer,
                              final DocumentVectorStore documentVectorStore,
                              final Corpus corpus) {

            this.documentWeighter = Objects.requireNonNull(documentWeighter, "documentWeighter cannot be null");
            this.sparseVectorizer = Objects.requireNonNull(sparseVectorizer, "sparseVectorizer cannot be null");
            this.documentVectorStore = Objects.requireNonNull(documentVectorStore, "documentVectorStore cannot be null");
            this.corpus = Objects.requireNonNull(corpus, "corpus cannot be null");

            LOGGER.info("Creating VectorIndexer");
            LOGGER.debug("Using documentWeighter={} sparseVectorizer={} documentVectorStore={}",
                    this.documentWeighter.getClass().getName(),
                    this.sparseVectorizer.getClass().getName(),
                    this.documentVectorStore.getClass().getName());
        }

        @Override
        public void index(final Document document) {
            Objects.requireNonNull(document, "document cannot be null");

            LOGGER.info("Starting vector indexing for document id={}", document.id());

            final Map<String, Double> weights = this.documentWeighter.weigh(this.corpus, document);
            LOGGER.debug("Computed {} term weight(s) for document id={}", weights.size(), document.id());

            final SparseDocumentVector sparseDocumentVector = this.sparseVectorizer.vectorize(document.id(), weights);
            LOGGER.debug("Built sparse vector for document id={}", document.id());

            this.documentVectorStore.save(sparseDocumentVector);
            LOGGER.info("Finished vector indexing for document id={}", document.id());
        }

        @Override
        public Document resolveDocument(final Document document) {
            Objects.requireNonNull(document, "document cannot be null");
            return document;
        }
    } // VectorIndexer

} // Indexers

