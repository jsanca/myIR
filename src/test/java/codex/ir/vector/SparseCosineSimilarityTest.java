package codex.ir.vector;

import codex.ir.corpus.vector.Vocabularies;
import codex.ir.corpus.vector.Vocabulary;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparseCosineSimilarityTest {

    @Test
    void shouldScoreUsingOnlySharedDimensions() {
        final Vocabulary vocabulary = Vocabularies.getVocabulary();
        final Vectorizer<SparseDocumentVector> vectorizer = Vectorizers.sparse(vocabulary);
        final Similarity<SparseDocumentVector> similarity = Similarities.sparseCosine();

        final SparseDocumentVector a = vectorizer.vectorize(
                Map.of("java", 2.0d, "ir", 1.0d)
        );

        final SparseDocumentVector b = vectorizer.vectorize(
                Map.of("java", 1.0d, "search", 5.0d)
        );

        final double score = similarity.score(a, b);

        assertTrue(score > 0.0d);
        assertTrue(score < 1.0d);
        assertEquals(3, vocabulary.size());
    }
}