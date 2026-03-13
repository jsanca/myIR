package codex.ir;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

        this.corpus.add(document);
        LOGGER.debug("Stored raw document {} in corpus", document.id());

        final String content = document.rawContent();
        if (content == null || content.isBlank()) {
            LOGGER.debug("Document {} has no content. Skipping indexing.", document.id());
            return;
        }

        final List<String> tokens = this.tokenizer.tokenize(content);
        LOGGER.debug("Document {} produced {} tokens", document.id(), tokens.size());

        for (final String token : tokens) {
            final Optional<String> normalized = this.normalizer.normalize(token);
            LOGGER.trace("Token '{}' normalized to '{}'", token, normalized);

            if (normalized.isEmpty() || normalized.get().isBlank()) {
                continue;
            }

            final String term = normalized.get();
            this.index.add(term, document.id());
            LOGGER.trace("Indexed term '{}' for document {}", term, document.id());
        }
    }
}
