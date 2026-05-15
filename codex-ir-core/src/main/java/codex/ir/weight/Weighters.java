package codex.ir.weight;

import codex.ir.Document;
import codex.ir.corpus.Corpus;
import codex.ir.indexer.InvertedIndex;
import codex.ir.tokenizer.Tokenizer;
import codex.ir.tokenizer.Tokenizers;

import codex.ir.util.TermWeightingUtils;

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
     * Creates a document weighter based on TF-IDF.
     *
     * @param index inverted index used to obtain document frequency
     * @return TF-IDF document weighter
     */
    public static DocumentWeighter tfIdf(final InvertedIndex index) {

        return tfIdf(Tokenizers.whitespace(), index);
    }

    /**
     * Creates a document weighter based on TF-IDF.
     *
     * @param tokenizer tokenizer used to split normalized content
     * @param index inverted index used to obtain document frequency
     * @return TF-IDF document weighter
     */
    public static DocumentWeighter tfIdf(final Tokenizer tokenizer, final InvertedIndex index) {

        return new TfIdfDocumentWeighter(tokenizer, index);
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
    /**
     * Computes per-term weights using TF-IDF.
     *
     * <p>This implementation assumes the document already contains normalized
     * content, so weighting can operate directly on normalized tokens without
     * repeating tokenization or normalization responsibilities.</p>
     */
    private static class TfIdfDocumentWeighter implements DocumentWeighter {

        private final Tokenizer tokenizer;
        private final InvertedIndex index;

        private TfIdfDocumentWeighter(final Tokenizer tokenizer, final InvertedIndex index) {
            this.tokenizer = Objects.requireNonNull(tokenizer, "tokenizer must not be null");
            this.index = Objects.requireNonNull(index, "index must not be null");
        }

        @Override
        public Map<String, Double> weigh(final Corpus corpus, final Document document) {
            Objects.requireNonNull(corpus, "corpus must not be null");
            Objects.requireNonNull(document, "document must not be null");

            final String normalizedContent = document.normalizedContent();
            if (normalizedContent == null || normalizedContent.isBlank()) {
                return Map.of();
            }

            final Map<String, Integer> termFrequencies = new HashMap<>();

            for (final String token : tokenizer.tokenize(normalizedContent)) {
                if (token == null || token.isBlank()) {
                    continue;
                }
                termFrequencies.merge(token, 1, Integer::sum);
            }

            if (termFrequencies.isEmpty()) {
                return Map.of();
            }

            final int corpusSize = corpus.statistics().documentCount();
            if (corpusSize <= 0) {
                return Map.of();
            }

            final Map<String, Double> termWeights = new HashMap<>();

            for (final Map.Entry<String, Integer> entry : termFrequencies.entrySet()) {
                final String term = entry.getKey();
                final int tf = entry.getValue();
                if (tf <= 0) {
                    continue;
                }

                final int documentFrequency = this.index.getPostings(term).size();
                if (documentFrequency <= 0) {
                    continue;
                }

                final double sublinearTf = TermWeightingUtils.sublinearTf(tf);
                final double idf = TermWeightingUtils.classicIdf(corpusSize, documentFrequency);
                termWeights.put(term, sublinearTf * idf);
            }

            return Map.copyOf(termWeights);
        }
    }
}
