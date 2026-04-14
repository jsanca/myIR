package codex.ir.corpus.vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class Vocabularies {

    /**
     * In-memory {@link Vocabulary} implementation backed by a term-to-id map and
     * a positional reverse lookup list.
     *
     * <p>Term identifiers are assigned sequentially in insertion order. The
     * forward lookup uses a concurrent map for fast reads, while reverse lookup
     * is maintained in a list indexed by the assigned term id.</p>
     *
     * <p>A lock is used only when a new term must be inserted or when reverse
     * lookup needs a stable view of the backing list.</p>
     */
    private static class InMemoryVocabulary implements Vocabulary {

        private final Map<String, Integer> termToId = new ConcurrentHashMap<>();
        private final List<String> idToTerm = new ArrayList<>();
        private final ReentrantLock lock = new ReentrantLock();

        /**
         * Returns the existing identifier for the supplied term, or assigns a new
         * sequential identifier if the term is not yet present.
         *
         * @param term normalized term to resolve
         * @return existing or newly assigned term id
         */
        @Override
        public int getOrCreateTermId(final String term) {
            Objects.requireNonNull(term, "term must not be null");

            Integer existingId = termToId.get(term);
            if (existingId != null) {
                return existingId;
            }

            lock.lock();
            try {
                existingId = termToId.get(term);
                if (existingId != null) {
                    return existingId;
                }

                int newId = idToTerm.size();
                idToTerm.add(term);
                termToId.put(term, newId);
                return newId;
            } finally {
                lock.unlock();
            }
        }

        /**
         * Returns the identifier for the supplied term if it already exists in
         * the vocabulary.
         *
         * @param term normalized term to resolve
         * @return optional term id
         */
        @Override
        public Optional<Integer> getTermId(final String term) {
            Objects.requireNonNull(term, "term must not be null");
            return Optional.ofNullable(termToId.get(term));
        }

        /**
         * Resolves a term identifier back to its original term value.
         *
         * @param termId term identifier
         * @return optional term associated with the supplied id
         */
        @Override
        public Optional<String> getTerm(final int termId) {
            lock.lock();
            try {
                if (termId >= 0 && termId < idToTerm.size()) {
                    return Optional.of(idToTerm.get(termId));
                }
                return Optional.empty();
            } finally {
                lock.unlock();
            }
        }

        /**
         * Returns the number of distinct terms currently registered in the
         * vocabulary.
         *
         * @return vocabulary size
         */
        @Override
        public int size() {
            return termToId.size();
        }
    }

    public static Vocabulary getVocabulary() {
        return new InMemoryVocabulary();
    }
}