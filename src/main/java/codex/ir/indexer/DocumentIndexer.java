package codex.ir.indexer;

import codex.ir.Document;
import codex.ir.corpus.Corpus;
import codex.ir.normalizer.Normalizer;
import codex.ir.tokenizer.Tokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of Indexer.
 *
 * Responsible for:
 *  - extracting raw text from the Document
 *  - storing the raw document in the Corpus
 *  - tokenizing the text
 *  - normalizing each token
 *  - inserting the normalized terms into the InvertedIndex
 * @author jsanca
 */
public class DocumentIndexer implements Indexer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentIndexer.class);

    private final Corpus corpus;
    private final InvertedIndex index;
    private final Tokenizer tokenizer;
    private final Normalizer normalizer;

    public DocumentIndexer(final Corpus corpus,
                           final InvertedIndex index,
                           final Tokenizer tokenizer,
                           final Normalizer normalizer) {

        this.corpus = Objects.requireNonNull(corpus);
        this.index = Objects.requireNonNull(index);
        this.tokenizer = Objects.requireNonNull(tokenizer);
        this.normalizer = Objects.requireNonNull(normalizer);
    }

    @Override
    public void index(final Document document) {

        Objects.requireNonNull(document);
        LOGGER.debug("Indexing document {}", document.id());

        final boolean alreadyEnriched = isAlreadyEnriched(document);
        if (alreadyEnriched) {
            LOGGER.debug("Document {} already contains normalized metadata. Storing as-is.", document.id());
            this.corpus.add(document);
            return;
        }

        final String content = document.rawContent();
        if (content == null || content.isBlank()) {
            final Document enrichedDocument = Document.builder(document)
                    .normalizedContent("")
                    .length(0)
                    .uniqueTerms(0)
                    .termFrequencies(Map.of())
                    .build();

            this.corpus.add(enrichedDocument);
            LOGGER.debug(
                    "Document {} has no content. Stored enriched empty document with length=0 and uniqueTerms=0.",
                    enrichedDocument.id()
            );
            return;
        }

        final List<String> tokens = this.tokenizer.tokenize(content);
        LOGGER.debug("Document {} produced {} tokens", document.id(), tokens.size());

        final List<String> normalizedTerms = new ArrayList<>();
        final Map<String, Integer> termFrequencies = new HashMap<>();

        for (int position = 0; position < tokens.size(); position++) {
            final String token = tokens.get(position);
            final Optional<String> normalized = this.normalizer.normalize(token);
            LOGGER.trace("Token '{}' normalized to '{}'", token, normalized);

            if (normalized.isEmpty() || normalized.get().isBlank()) {
                continue;
            }

            final String term = normalized.get();
            normalizedTerms.add(term);
            termFrequencies.merge(term, 1, Integer::sum);
            this.index.add(term, document.id(), position);
            LOGGER.trace("Indexed term '{}' for document {}", term, document.id());
        }

        final String normalizedContent = String.join(" ", normalizedTerms);
        final int documentLength = normalizedTerms.size();
        final int uniqueTermCount = termFrequencies.size();
        LOGGER.debug(
                "Document {} term frequencies computed for {} unique term(s)",
                document.id(),
                uniqueTermCount
        );

        final Document enrichedDocument = Document.builder(document)
                .normalizedContent(normalizedContent)
                .length(documentLength)
                .uniqueTerms(uniqueTermCount)
                .termFrequencies(termFrequencies)
                .build();

        this.corpus.add(enrichedDocument);
        LOGGER.debug(
                "Stored enriched document {} in corpus with length={} and uniqueTerms={}",
                enrichedDocument.id(),
                documentLength,
                uniqueTermCount
        );
    }

    private boolean isAlreadyEnriched(final Document document) {
        if (document.normalizedContent() == null
                || document.metadata() == null
                || document.metadata().termFrequencies() == null) {

            return false;
        }

        return document.metadata().length() != null
                && document.metadata().uniqueTerms() != null;
    }
}
