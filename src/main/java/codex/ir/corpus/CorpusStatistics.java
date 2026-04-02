package codex.ir.corpus;

import codex.ir.Document;

import java.util.Objects;

/**
 * Immutable value object representing aggregated statistics of a corpus.
 *
 * <p>Includes additional aggregated values such as total document length and the number of
 * documents contributing to length-based calculations.</p>
 *
 * <p>This record is a lightweight projection derived from the current state of a {@link Corpus},
 * intended to be used by ranking algorithms (e.g., BM25) that require global corpus-level
 * metrics such as total number of documents and average document length.</p>
 *
 * <p>It is intentionally small and inexpensive to compute compared to a full snapshot of the corpus
 * or index structures. Instances are immutable and therefore safe to share across threads.</p>
 *
 * <p>Typical usage:</p>
 * <pre>
 *     CorpusStatistics stats = CorpusStatistics.from(corpus);
 *     double avgdl = stats.averageDocumentLength();
 * </pre>
 */
public record CorpusStatistics(
        int documentCount,
        long totalDocumentLength,
        int documentsWithLength,
        double averageDocumentLength
) {

    /**
     * Canonical constructor with validation.
     *
     * @param documentCount total number of documents in the corpus (must be >= 0)
     * @param totalDocumentLength total length of all documents with length info (must be >= 0)
     * @param documentsWithLength number of documents contributing to length calculations (must be >= 0)
     * @param averageDocumentLength average number of terms per document (must be >= 0)
     * @throws IllegalArgumentException if any value is negative
     */
    public CorpusStatistics {
        if (documentCount < 0) {
            throw new IllegalArgumentException("documentCount must be >= 0");
        }
        if (totalDocumentLength < 0) {
            throw new IllegalArgumentException("totalDocumentLength must be >= 0");
        }
        if (documentsWithLength < 0) {
            throw new IllegalArgumentException("documentsWithLength must be >= 0");
        }
        if (averageDocumentLength < 0) {
            throw new IllegalArgumentException("averageDocumentLength must be >= 0");
        }
    }

    /**
     * Creates a {@link CorpusStatistics} instance from the given {@link Corpus}.
     *
     * <p>This method iterates over all documents in the corpus and computes the
     * average document length based on normalized term counts stored in each document's metadata.</p>
     *
     * <p>Documents without metadata or length information are ignored in the average calculation.</p>
     *
     * @param corpus the corpus from which to derive statistics (must not be null)
     * @return a new {@link CorpusStatistics} instance representing the current corpus state
     * @throws NullPointerException if {@code corpus} is null
     */
    public static CorpusStatistics from(final Corpus corpus) {
        Objects.requireNonNull(corpus, "corpus must not be null");

        long totalLength = 0;
        int countedDocuments = 0;

        for (final Document document : corpus.documents()) {
            if (document == null || document.metadata() == null || document.metadata().length() == null) {
                continue;
            }

            totalLength += document.metadata().length();
            countedDocuments++;
        }

        final double avgdl = countedDocuments == 0
                ? 0.0
                : (double) totalLength / countedDocuments;

        return new CorpusStatistics(
                corpus.size(),
                totalLength,
                countedDocuments,
                avgdl
        );
    }
}
