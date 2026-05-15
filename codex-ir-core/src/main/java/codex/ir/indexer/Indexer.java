package codex.ir.indexer;

import codex.ir.Document;

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
}
