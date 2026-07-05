package codex.ir.indexer;

import codex.ir.Document;

import java.util.List;
import java.util.Objects;

/**
 * Coordinates the process of taking a Document, analyzing its text,
 * and inserting the resulting terms into an InvertedIndex.
 *
 * The Indexer itself does not necessarily implement tokenization or
 * normalization; instead, concrete implementations usually depend on
 * helpers such as Tokenizer and Normalizer.
 * @author jsanca
 */
public interface Indexer {

    /**
     * Indexes a single document into the provided inverted index.
     *
     * Implementations typically perform the following steps:
     * 1. Extract text from the document (usually rawContent)
     * 2. Tokenize the text
     * 3. Normalize each token
     * 4. Insert normalized terms into the index
     *
     * @param document the document to index
     * @param index the inverted index where terms will be stored
     */
    void index(Document document);

    /**
     * Indexes a list of documents.
     * <p>
     * The default implementation calls {@link #index(Document)} sequentially for each
     * document. Batch-aware implementations — such as those returned by
     * {@link Indexers#batchLexicalAndVector} — override this method to build all
     * lexical postings first, take a single corpus snapshot, and then vectorize every
     * document against that shared snapshot. This ensures IDF values reflect the full
     * batch rather than the corpus state at each individual document's insertion time.
     *
     * @param documents the documents to index; must not be null
     */
    default void indexAll(final List<Document> documents) {
        Objects.requireNonNull(documents, "documents cannot be null");
        documents.forEach(this::index);
    }
}
