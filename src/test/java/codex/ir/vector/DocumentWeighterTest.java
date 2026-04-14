package codex.ir.vector;

import codex.ir.Document;
import codex.ir.Document.DocumentMetadata;
import codex.ir.corpus.Corpora;
import codex.ir.corpus.Corpus;
import codex.ir.tokenizer.Tokenizer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentWeighterTest {

    @Test
    void shouldComputeRawTermFrequencyFromNormalizedContent() {
        final Corpus corpus = Corpora.inMemory();
        final Document document = new Document(
                "doc-1",
                "Java Java IR Search",
                "java java ir search",
                Map.of(),
                DocumentMetadata.empty()
        );

        final DocumentWeighter weighter = Weighters.termFrequency();
        final Map<String, Double> weights = weighter.weigh(corpus, document);

        assertEquals(3, weights.size());
        assertEquals(2.0d, weights.get("java"));
        assertEquals(1.0d, weights.get("ir"));
        assertEquals(1.0d, weights.get("search"));
    }

    @Test
    void shouldReturnEmptyWeightsForBlankNormalizedContent() {
        final Corpus corpus = Corpora.inMemory();
        final Document document = new Document(
                "doc-blank",
                "   ",
                "   ",
                Map.of(),
                DocumentMetadata.empty()
        );

        final DocumentWeighter weighter = Weighters.termFrequency();
        final Map<String, Double> weights = weighter.weigh(corpus, document);

        assertTrue(weights.isEmpty());
    }

    @Test
    void shouldUseInjectedTokenizer() {
        final Corpus corpus = Corpora.inMemory();
        final Document document = new Document(
                "doc-2",
                "ignored",
                "alpha|beta|alpha",
                Map.of(),
                DocumentMetadata.empty()
        );

        final Tokenizer pipeTokenizer = content -> java.util.List.of(content.split("\\|"));
        final DocumentWeighter weighter = Weighters.termFrequency(pipeTokenizer);
        final Map<String, Double> weights = weighter.weigh(corpus, document);

        assertEquals(2.0d, weights.get("alpha"));
        assertEquals(1.0d, weights.get("beta"));
        assertEquals(2, weights.size());
    }

    @Test
    void shouldRejectNullCorpus() {
        final Document document = new Document(
                "doc-3",
                "Java",
                "java",
                Map.of(),
                DocumentMetadata.empty()
        );

        final DocumentWeighter weighter = Weighters.termFrequency();

        assertThrows(NullPointerException.class, () -> weighter.weigh(null, document));
    }

    @Test
    void shouldRejectNullDocument() {
        final Corpus corpus = Corpora.inMemory();
        final DocumentWeighter weighter = Weighters.termFrequency();

        assertThrows(NullPointerException.class, () -> weighter.weigh(corpus, null));
    }
}
