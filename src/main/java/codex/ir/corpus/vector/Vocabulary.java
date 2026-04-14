package codex.ir.corpus.vector;

import java.util.Optional;

/**
 * Global vocabulary for mapping normalized terms to stable integer identifiers.
 * @author jsanca & elo
 */
public interface Vocabulary {

    /**
     * Returns the existing term id for the supplied term, or assigns a new one if absent.
     *
     * @param term normalized term
     * @return stable integer id for the term
     */
    int getOrCreateTermId(String term);

    /**
     * Returns the term id if present.
     *
     * @param term normalized term
     * @return optional term id
     */
    Optional<Integer> getTermId(String term);

    /**
     * Resolves a term id back to the original term if present.
     *
     * @param termId term identifier
     * @return optional term string
     */
    Optional<String> getTerm(int termId);

    /**
     * Returns the number of distinct terms in the vocabulary.
     *
     * @return vocabulary size
     */
    int size();
}