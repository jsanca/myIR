package codex.ir.indexer;

import codex.ir.Document;
import codex.ir.corpus.Corpus;
import codex.ir.corpus.CorpusSnapshot;
import codex.ir.normalizer.Normalizer;
import codex.ir.tokenizer.Tokenizer;
import codex.ir.vector.SparseDocumentVector;
import codex.ir.vector.Vectorizer;
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
                                 final Vectorizer<SparseDocumentVector> sparseVectorizer,
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
     * <p>
     * This is the preferred factory when both lexical and vector indexes are
     * needed because it guarantees a single preprocessing lifecycle per
     * document:
     * <ol>
     *   <li>{@code DocumentPreprocessor} preprocesses the document once.</li>
     *   <li>{@code LexicalIndexer} stores the preprocessed document in the
     *       corpus and populates the inverted index from its
     *       {@code normalizedContent}.</li>
     *   <li>{@code VectorIndexer} receives the same preprocessed document,
     *       computes term weights from its {@code normalizedContent}, and
     *       stores the resulting sparse vector.</li>
     * </ol>
     * Both indexes are built from the same canonical preprocessed
     * representation. There is no risk of divergence between lexical and
     * vector content.
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
                                           final Vectorizer<SparseDocumentVector>sparseVectorizer,
                                           final DocumentVectorStore documentVectorStore,
                                           final Corpus corpus) {

        return new PipelineIndexer(new DocumentPreprocessor(tokenizer, normalizer),
                new LexicalIndexer(corpus, index),
                new VectorIndexer(documentWeighter, sparseVectorizer, documentVectorStore, corpus));
    }

    /**
     * Creates a batch-aware indexer that performs lexical and vector indexing with a
     * single corpus snapshot shared across all documents in a batch.
     * <p>
     * When {@link Indexer#indexAll} is called, the indexer:
     * <ol>
     *   <li>Preprocesses every document once.</li>
     *   <li>Lexically indexes all preprocessed documents (populates corpus and
     *       inverted index).</li>
     *   <li>Takes one {@link CorpusSnapshot} capturing the full batch statistics.</li>
     *   <li>Vectorizes all documents using that shared snapshot, so every document
     *       sees the same IDF values.</li>
     * </ol>
     * <p>
     * The single-document {@link Indexer#index} method uses the incremental pipeline:
     * lexical indexing is followed immediately by vectorization with a snapshot taken
     * at that moment.
     *
     * @param index the inverted index to populate
     * @param tokenizer the tokenizer used during preprocessing
     * @param normalizer the normalizer used during preprocessing
     * @param documentWeighter the component used to compute term weights
     * @param sparseVectorizer the vectorizer used to build sparse vectors
     * @param documentVectorStore the store where vectors will be persisted
     * @param corpus the corpus where preprocessed documents will be stored
     * @return a batch-aware indexer for lexical and vector indexing
     */
    public static Indexer batchLexicalAndVector(final InvertedIndex index,
                                                final Tokenizer tokenizer,
                                                final Normalizer normalizer,
                                                final DocumentWeighter documentWeighter,
                                                final Vectorizer<SparseDocumentVector> sparseVectorizer,
                                                final DocumentVectorStore documentVectorStore,
                                                final Corpus corpus) {

        final DocumentPreprocessor preprocessor = new DocumentPreprocessor(tokenizer, normalizer);
        final LexicalIndexer lexicalIndexer = new LexicalIndexer(corpus, index);
        final VectorIndexer vectorIndexer = new VectorIndexer(
                documentWeighter, sparseVectorizer, documentVectorStore, corpus);
        return new BatchPipelineIndexer(preprocessor, lexicalIndexer, vectorIndexer, corpus);
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
     * Marker for pipeline stages that require per-field token provenance.
     * Receives the full {@link FieldAnalyzedDocument} from the pipeline.
     * Takes priority over {@link PreprocessedDocumentConsumer}.
     */
    @FunctionalInterface
    private interface FieldAnalyzedDocumentConsumer {
        void index(FieldAnalyzedDocument fieldAnalyzedDocument);
    }

    /**
     * Marker for pipeline stages that can consume a {@link PreprocessedDocument}
     * directly, avoiding a redundant join→split cycle on {@code normalizedContent}.
     * Stages that do not implement this interface receive
     * {@code PreprocessedDocument.document()} instead.
     */
    @FunctionalInterface
    private interface PreprocessedDocumentConsumer {
        void index(PreprocessedDocument preprocessedDocument);
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

            final FieldAnalyzedDocument analyzed = this.documentPreprocessor.preprocess(document);
            LOGGER.debug("Finished preprocessing for document id={}", analyzed.id());

            for (final Indexer stage : this.indexers) {
                LOGGER.debug("Executing pipeline stage for document id={} with indexer {}",
                        analyzed.id(), stage.getClass().getName());

                if (stage instanceof final FieldAnalyzedDocumentConsumer consumer) {
                    consumer.index(analyzed);
                } else if (stage instanceof final PreprocessedDocumentConsumer consumer) {
                    consumer.index(analyzed.base());
                } else {
                    stage.index(analyzed.document());
                }

                LOGGER.debug("Finished pipeline stage for document id={} with indexer {}",
                        analyzed.id(), stage.getClass().getName());
            }

            LOGGER.info("Finished pipeline indexing for document id={}", analyzed.id());
        }
    } // PipelineIndexer

    /**
     * Preprocesses raw documents into a canonical enriched form shared by all
     * indexing stages.
     * <p>
     * Produces a {@link FieldAnalyzedDocument} that pairs the whole-document
     * {@link PreprocessedDocument} with per-field {@link FieldTokenSequence} entries.
     * <p>
     * <b>Whole-document content resolution (unchanged):</b>
     * <ul>
     *   <li>If the document carries structured {@code fields} with at least one
     *       non-blank value, field values are aggregated into a single
     *       whole-document content stream. {@code rawContent} is ignored.</li>
     *   <li>If fields are absent or all-blank, {@code rawContent} is used.</li>
     *   <li>The resulting {@code normalizedContent}, token list, and
     *       {@code termFrequencies} are whole-document only and are not affected
     *       by per-field analysis.</li>
     * </ul>
     * <p>
     * <b>Per-field analysis (IR-2):</b>
     * <ul>
     *   <li>Each non-blank field is tokenized and normalized independently,
     *       producing one {@link FieldTokenSequence} per field.</li>
     *   <li>Blank fields are ignored and produce no sequence.</li>
     *   <li>When rawContent fallback is used (all fields absent or blank),
     *       {@code fieldSequences} is empty.</li>
     *   <li>Per-field tokens are analysis artifacts only — they do not affect
     *       ranking, postings, or search behavior in this slice.</li>
     * </ul>
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

        public FieldAnalyzedDocument preprocess(final Document document) {
            Objects.requireNonNull(document, "document cannot be null");

            LOGGER.debug("Preprocessing document id={}", document.id());

            if (isAlreadyPreprocessed(document)) {
                LOGGER.debug("Document id={} already contains normalized metadata. Reusing as-is.",
                        document.id());
                final List<String> existingTokens = document.normalizedContent() == null
                        || document.normalizedContent().isBlank()
                        ? List.of()
                        : List.of(document.normalizedContent().split("\\s+"));
                return new FieldAnalyzedDocument(
                        new PreprocessedDocument(document, existingTokens), List.of());
            }

            final String content = resolveContent(document);
            if (content == null || content.isBlank()) {
                LOGGER.debug("Document id={} has no content. Returning empty preprocessed document.",
                        document.id());
                final Document empty = Document.builder(document)
                        .normalizedContent("")
                        .length(0)
                        .uniqueTerms(0)
                        .termFrequencies(Map.of())
                        .build();
                return new FieldAnalyzedDocument(
                        new PreprocessedDocument(empty, List.of()), List.of());
            }

            final List<String> rawTokens = this.tokenizer.tokenize(content);
            LOGGER.debug("Document id={} produced {} raw token(s)", document.id(), rawTokens.size());

            final List<String> normalizedTerms = new ArrayList<>();
            final Map<String, Integer> termFrequencies = new HashMap<>();

            for (final String token : rawTokens) {
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
                    document.id(), documentLength, uniqueTermCount);

            final Document preprocessedDoc = Document.builder(document)
                    .normalizedContent(normalizedContent)
                    .length(documentLength)
                    .uniqueTerms(uniqueTermCount)
                    .termFrequencies(termFrequencies)
                    .build();

            final PreprocessedDocument base =
                    new PreprocessedDocument(preprocessedDoc, List.copyOf(normalizedTerms));
            final List<FieldTokenSequence> fieldSequences = analyzeFields(document);

            LOGGER.debug("Document id={} produced {} field sequence(s)",
                    document.id(), fieldSequences.size());

            return new FieldAnalyzedDocument(base, fieldSequences);
        }

        /**
         * Tokenizes and normalizes each non-blank field value independently,
         * producing one {@link FieldTokenSequence} per field.
         * Returns an empty list when no fields are present or all are blank
         * (i.e. when rawContent fallback was used for whole-document content).
         */
        private List<FieldTokenSequence> analyzeFields(final Document document) {
            final Map<String, String> fields = document.fields();
            if (fields == null || fields.isEmpty()) {
                return List.of();
            }

            final boolean anyNonBlank = fields.values().stream()
                    .anyMatch(v -> v != null && !v.isBlank());
            if (!anyNonBlank) {
                return List.of();
            }

            final List<FieldTokenSequence> sequences = new ArrayList<>();
            for (final Map.Entry<String, String> entry : fields.entrySet()) {
                final String fieldValue = entry.getValue();
                if (fieldValue == null || fieldValue.isBlank()) {
                    continue;
                }
                final List<String> fieldTokens = normalizeTokens(fieldValue);
                if (!fieldTokens.isEmpty()) {
                    sequences.add(new FieldTokenSequence(entry.getKey(), fieldTokens));
                }
            }
            return List.copyOf(sequences);
        }

        private List<String> normalizeTokens(final String text) {
            final List<String> result = new ArrayList<>();
            for (final String token : this.tokenizer.tokenize(text)) {
                final Optional<String> normalized = this.normalizer.normalize(token);
                if (normalized.isPresent() && !normalized.get().isBlank()) {
                    result.add(normalized.get());
                }
            }
            return result;
        }

        private boolean isAlreadyPreprocessed(final Document document) {
            if (document.normalizedContent() == null
                    || document.normalizedContent().isBlank()
                    || document.metadata() == null
                    || document.metadata().termFrequencies() == null) {
                return false;
            }

            return document.metadata().length() != null
                    && document.metadata().uniqueTerms() != null;
        }

        /**
         * Resolves the content source for a document according to the current
         * whole-document aggregation contract.
         * <p>
         * Priority:
         * <ol>
         *   <li>If {@code fields} is non-empty and contains at least one
         *       non-blank value, all non-blank field values are joined with
         *       spaces and returned. Field names are discarded.</li>
         *   <li>If fields is empty or all values are blank, fall back to
         *       {@code rawContent}.</li>
         * </ol>
         * <p>
         * This method intentionally does not preserve per-field provenance.
         * The returned string is a whole-document content stream that feeds
         * into tokenization, normalization, and every downstream stage.
         */
        private String resolveContent(final Document document) {
            final Map<String, String> fields = document.fields();
            if (fields == null || fields.isEmpty()) {
                return document.rawContent();
            }

            final String aggregated = fields.values().stream()
                    .filter(v -> v != null && !v.isBlank())
                    .collect(java.util.stream.Collectors.joining(" "));

            if (aggregated.isBlank()) {
                LOGGER.debug("Document id={} has fields but all values are blank. Falling back to rawContent.",
                        document.id());
                return document.rawContent();
            }

            return aggregated;
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
    private static final class LexicalIndexer implements Indexer, PreprocessedDocumentConsumer,
            FieldAnalyzedDocumentConsumer {

        private static final Logger LOGGER = LoggerFactory.getLogger(LexicalIndexer.class);

        private final Corpus corpus;
        private final InvertedIndex index;

        private LexicalIndexer(final Corpus corpus,
                               final InvertedIndex index) {
            this.corpus = Objects.requireNonNull(corpus, "corpus cannot be null");
            this.index = Objects.requireNonNull(index, "index cannot be null");
        }

        /**
         * Primary path when called through the pipeline: indexes whole-document tokens
         * then records per-field frequencies for field-structured documents.
         */
        @Override
        public void index(final FieldAnalyzedDocument analyzed) {
            Objects.requireNonNull(analyzed, "analyzed cannot be null");

            index(analyzed.base());

            if (!analyzed.hasFieldSequences()) {
                return;
            }

            final String docId = analyzed.id();
            for (final FieldTokenSequence seq : analyzed.fieldSequences()) {
                for (final String term : seq.tokens()) {
                    this.index.addFieldOccurrence(term, docId, seq.fieldName());
                    LOGGER.trace("Recorded field occurrence term='{}' field='{}' docId='{}'",
                            term, seq.fieldName(), docId);
                }
            }

            LOGGER.debug("Recorded field frequencies for document id={} across {} field(s)",
                    docId, analyzed.fieldSequences().size());
        }

        /**
         * Whole-document indexing path: stores the document in the corpus and populates
         * the inverted index from its pre-built token list.
         */
        @Override
        public void index(final PreprocessedDocument preprocessed) {
            Objects.requireNonNull(preprocessed, "preprocessed cannot be null");

            final Document document = preprocessed.document();
            LOGGER.info("Starting lexical indexing for document id={}", document.id());

            this.corpus.add(document);
            LOGGER.debug("Stored preprocessed document id={} in corpus", document.id());

            final List<String> tokens = preprocessed.tokens();
            if (tokens.isEmpty()) {
                LOGGER.debug("Document id={} has no tokens. Skipping inverted index population.",
                        document.id());
                LOGGER.info("Finished lexical indexing for document id={}", document.id());
                return;
            }

            LOGGER.debug("Document id={} produced {} token(s) for lexical indexing",
                    document.id(), tokens.size());

            for (int position = 0; position < tokens.size(); position++) {
                final String term = tokens.get(position);
                this.index.add(term, document.id(), position);
                LOGGER.trace("Indexed term '{}' for document id={} at position={}",
                        term, document.id(), position);
            }

            LOGGER.info("Finished lexical indexing for document id={}", document.id());
        }

        /**
         * Fallback for callers that bypass the pipeline and pass a {@link Document}
         * directly. Splits {@code normalizedContent} on whitespace for positional indexing.
         */
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
                    document.id(), normalizedTerms.length);

            for (int position = 0; position < normalizedTerms.length; position++) {
                final String term = normalizedTerms[position];
                this.index.add(term, document.id(), position);
                LOGGER.trace("Indexed term '{}' for document id={} at position={}",
                        term, document.id(), position);
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
    private static final class VectorIndexer implements Indexer {

        private static final Logger LOGGER = LoggerFactory.getLogger(VectorIndexer.class);

        private final DocumentWeighter documentWeighter;
        private final Vectorizer<SparseDocumentVector> sparseVectorizer;
        private final DocumentVectorStore documentVectorStore;
        private final Corpus corpus;

        private VectorIndexer(final DocumentWeighter documentWeighter,
                              final Vectorizer<SparseDocumentVector> sparseVectorizer,
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

            final Map<String, Double> weights = this.documentWeighter.weigh(this.corpus.snapshot(), document);
            LOGGER.debug("Computed {} term weight(s) for document id={}", weights.size(), document.id());

            final SparseDocumentVector sparseDocumentVector = this.sparseVectorizer.vectorize(document.id(), weights);
            LOGGER.debug("Built sparse vector for document id={}", document.id());

            this.documentVectorStore.save(sparseDocumentVector);
            LOGGER.info("Finished vector indexing for document id={}", document.id());
        }

        /**
         * Vectorizes a preprocessed document using a caller-supplied snapshot.
         * Used by the batch pipeline to avoid per-document snapshot allocation.
         */
        void indexWithSnapshot(final Document document, final CorpusSnapshot corpusSnapshot) {
            Objects.requireNonNull(document, "document cannot be null");
            Objects.requireNonNull(corpusSnapshot, "corpusSnapshot cannot be null");

            LOGGER.info("Starting vector indexing for document id={}", document.id());

            final Map<String, Double> weights = this.documentWeighter.weigh(corpusSnapshot, document);
            LOGGER.debug("Computed {} term weight(s) for document id={}", weights.size(), document.id());

            final SparseDocumentVector sparseDocumentVector = this.sparseVectorizer.vectorize(document.id(), weights);
            LOGGER.debug("Built sparse vector for document id={}", document.id());

            this.documentVectorStore.save(sparseDocumentVector);
            LOGGER.info("Finished vector indexing for document id={}", document.id());
        }
    } // VectorIndexer

    /**
     * Batch-aware indexer that phases lexical and vector indexing to use a single
     * corpus snapshot for the entire batch.
     * <p>
     * The single-document {@link #index} method delegates to an incremental
     * {@link PipelineIndexer} for full compatibility with the existing API.
     */
    private static final class BatchPipelineIndexer implements Indexer {

        private static final Logger LOGGER = LoggerFactory.getLogger(BatchPipelineIndexer.class);

        private final PipelineIndexer singleDocIndexer;
        private final DocumentPreprocessor documentPreprocessor;
        private final LexicalIndexer lexicalIndexer;
        private final VectorIndexer vectorIndexer;
        private final Corpus corpus;

        private BatchPipelineIndexer(final DocumentPreprocessor documentPreprocessor,
                                     final LexicalIndexer lexicalIndexer,
                                     final VectorIndexer vectorIndexer,
                                     final Corpus corpus) {
            this.documentPreprocessor = Objects.requireNonNull(documentPreprocessor,
                    "documentPreprocessor cannot be null");
            this.lexicalIndexer = Objects.requireNonNull(lexicalIndexer,
                    "lexicalIndexer cannot be null");
            this.vectorIndexer = Objects.requireNonNull(vectorIndexer,
                    "vectorIndexer cannot be null");
            this.corpus = Objects.requireNonNull(corpus, "corpus cannot be null");
            this.singleDocIndexer = new PipelineIndexer(documentPreprocessor,
                    List.of(lexicalIndexer, vectorIndexer));
        }

        @Override
        public void index(final Document document) {
            this.singleDocIndexer.index(document);
        }

        /**
         * Batch-optimized path:
         * <ol>
         *   <li>Preprocess all documents.</li>
         *   <li>Lexically index all preprocessed documents.</li>
         *   <li>Take one {@link CorpusSnapshot} reflecting the full batch.</li>
         *   <li>Vectorize all documents using the shared snapshot.</li>
         * </ol>
         */
        @Override
        public void indexAll(final List<Document> documents) {
            Objects.requireNonNull(documents, "documents cannot be null");
            if (documents.isEmpty()) {
                return;
            }

            LOGGER.info("Starting batch indexing of {} document(s)", documents.size());

            final List<FieldAnalyzedDocument> analyzed = documents.stream()
                    .map(this.documentPreprocessor::preprocess)
                    .toList();
            LOGGER.debug("Preprocessed {} document(s)", analyzed.size());

            analyzed.forEach(this.lexicalIndexer::index);
            LOGGER.debug("Lexically indexed {} document(s)", analyzed.size());

            final CorpusSnapshot corpusSnapshot = this.corpus.snapshot();
            LOGGER.debug("Created corpus snapshot with {} document(s)", corpusSnapshot.size());

            analyzed.forEach(fa -> this.vectorIndexer.indexWithSnapshot(fa.document(), corpusSnapshot));

            LOGGER.info("Finished batch indexing of {} document(s)", analyzed.size());
        }
    } // BatchPipelineIndexer

} // Indexers

