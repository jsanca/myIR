package codex.ir.weight;

import codex.ir.Document;
import codex.ir.corpus.Corpus;
import codex.ir.tokenizer.Tokenizer;
import codex.ir.tokenizer.Tokenizers;

import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

public final class Weighters {

    private Weighters() {}

    /**
     * Creates a document weighter based on raw term frequency.
     *
     * @return term-frequency document weighter
     */
    public static DocumentWeighter termFrequency() {

        return termFrequency(Tokenizers.whitespace());
    }


    /**
     * Creates a document weighter based on raw term frequency.
     *
     *
     * @return term-frequency document weighter
     */
    public static DocumentWeighter termFrequency(final Tokenizer tokenizer) {

        return new TermFrequencyDocumentWeighter(tokenizer);
    }

    /**
     * Computes per-term weights using plain term frequency.
     *
     * <p>This implementation assumes the document already contains normalized
     * content, so weighting can operate directly on normalized tokens without
     * repeating tokenization or normalization responsibilities.</p>
     */
    private static class TermFrequencyDocumentWeighter implements DocumentWeighter {

        private final Tokenizer tokenizer;

        public TermFrequencyDocumentWeighter(final Tokenizer tokenizer) {
            this.tokenizer = Objects.requireNonNull(tokenizer, "tokenizer must not be null");
        }

        @Override
        public Map<String, Double> weigh(final Corpus corpus, final Document document) {
            Objects.requireNonNull(corpus, "corpus must not be null");
            Objects.requireNonNull(document, "document must not be null");

            final String normalizedContent = document.normalizedContent();
            if (normalizedContent == null || normalizedContent.isBlank()) {
                return Map.of();
            }

            final Map<String, Double> termWeights = new HashMap<>();

            for (final String token : tokenizer.tokenize(normalizedContent)) {
                if (token == null || token.isBlank()) {
                    continue;
                }
                termWeights.merge(token, 1.0d, Double::sum);
            }

            return Map.copyOf(termWeights);
        }
    }
}
