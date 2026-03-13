package codex.ir;

import java.util.List;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utility factory class for creating common Normalizer implementations.
 *
 * This class centralizes the creation of reusable normalization strategies,
 * including simple single normalizers and composite normalization pipelines.
 */
public final class Normalizers {

    private Normalizers() {
    }

    public static Normalizer basic() {
        return new BasicNormalizer();
    }

    public static Normalizer lowercase() {
        return new LowercaseNormalizer();
    }

    public static Normalizer trimNonAlphaNumeric() {
        return new TrimNonAlphaNumericNormalizer();
    }

    public static Normalizer accentFolding() {
        return new AccentFoldingNormalizer();
    }

    public static Normalizer stopWords() {
        return new StopWordNormalizer("classpath:/stopwords_en.txt");
    }

    public static Normalizer stopWords(final String explicitPath) {
        return new StopWordNormalizer(explicitPath);
    }

    public static Normalizer defaultEnglish() {
        return english();
    }

    public static Normalizer minimal() {
        return chain(
                lowercase(),
                trimNonAlphaNumeric()
        );
    }

    public static Normalizer english() {
        return chain(
                lowercase(),
                trimNonAlphaNumeric(),
                stopWords("classpath:/stopwords_en.txt")
        );
    }

    public static Normalizer spanish() {
        return chain(
                lowercase(),
                accentFolding(),
                trimNonAlphaNumeric(),
                stopWords("classpath:/stopwords_es.txt")
        );
    }
    /**
     * Normalizer that removes diacritics (accent marks) from characters.
     *
     * Example:
     * canción -> cancion
     * niño -> nino
     * acción -> accion
     */
    static class AccentFoldingNormalizer implements Normalizer {

        private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

        @Override
        public Optional<String> normalize(final String token) {

            if (token == null) {
                return Optional.empty();
            }

            final String normalized = DIACRITICS.matcher(
                    java.text.Normalizer.normalize(token, java.text.Normalizer.Form.NFD)
            ).replaceAll("");

            if (normalized.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(normalized);
        }
    }


    /**
     * Creates a composite normalizer that applies the provided normalizers
     * sequentially in the given order.
     *
     * @param normalizers the normalizers to apply in sequence
     * @return a composite normalizer chain
     */
    public static Normalizer chain(final List<Normalizer> normalizers) {
        return new NormalizerChain(normalizers);
    }

    /**
     * Creates a composite normalizer that applies the provided normalizers
     * sequentially in the given order.
     *
     * @param normalizers the normalizers to apply in sequence
     * @return a composite normalizer chain
     */
    public static Normalizer chain(final Normalizer... normalizers) {
        return new NormalizerChain(List.of(normalizers));
    }

    /**
     * Default normalizer implementation.
     *
     * Performs very simple normalization suitable for the first iteration
     * of the IR engine:
     * - converts tokens to lowercase
     * - removes non-alphanumeric characters from the beginning and end
     */
    static class BasicNormalizer implements Normalizer {

        @Override
        public Optional<String> normalize(final String token) {
            if (token == null) {
                return Optional.empty();
            }

            final String normalized = token
                    .toLowerCase(java.util.Locale.ROOT)
                    .replaceAll("^[^a-z0-9]+|[^a-z0-9]+$", "");

            if (normalized.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(normalized);
        }
    }

    /**
     * Normalizer that converts tokens to lowercase using Locale.ROOT.
     */
    static class LowercaseNormalizer implements Normalizer {

        @Override
        public Optional<String> normalize(final String token) {
            if (token == null) {
                return Optional.empty();
            }
            return Optional.of(token.toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Normalizer that removes non‑alphanumeric characters from the
     * beginning and end of tokens.
     */
    static class TrimNonAlphaNumericNormalizer implements Normalizer {

        @Override
        public Optional<String> normalize(final String token) {
            if (token == null) {
                return Optional.empty();
            }

            final String normalized = token.replaceAll("^[^a-z0-9]+|[^a-z0-9]+$", "");
            if (normalized.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(normalized);
        }
    }

    /**
     * Normalizer that filters stop words.
     *
     * Stop words are loaded using the following strategy:
     * 1. Environment variable IR_STOPWORDS_PATH
     * 2. File path provided explicitly in constructor
     * 3. Fallback to classpath resource /stopwords.txt
     */
    static class StopWordNormalizer implements Normalizer {

        private final Set<String> stopWords;

        public StopWordNormalizer() {
            this(loadStopWords(null));
        }

        public StopWordNormalizer(final String explicitPath) {
            this(loadStopWords(explicitPath));
        }

        private StopWordNormalizer(final Set<String> stopWords) {
            this.stopWords = stopWords;
        }

        @Override
        public Optional<String> normalize(final String token) {
            if (token == null) {
                return Optional.empty();
            }

            if (stopWords.contains(token)) {
                return Optional.empty();
            }

            return Optional.of(token);
        }

        private static Set<String> loadStopWords(final String explicitPath) {

            final Set<String> words = new HashSet<>();

            try {

                String path = explicitPath;

                if (path == null) {
                    path = System.getenv("IR_STOPWORDS_PATH");
                }

                if (path != null) {

                    if (path.startsWith("classpath:")) {
                        final String resourcePath = path.substring("classpath:".length());
                        try (InputStream is = StopWordNormalizer.class.getResourceAsStream(resourcePath)) {

                            if (is != null) {
                                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                                    reader.lines().map(String::trim).forEach(words::add);
                                    return words;
                                }
                            }
                        }
                    } else if (path.startsWith("file:")) {
                        final String filePath = path.substring("file:".length());
                        try (BufferedReader reader = Files.newBufferedReader(Path.of(filePath))) {
                            reader.lines().map(String::trim).forEach(words::add);
                            return words;
                        }
                    } else {
                        try (InputStream is = StopWordNormalizer.class.getResourceAsStream("/" + path)) {

                            if (is != null) {
                                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                                    reader.lines().map(String::trim).forEach(words::add);
                                    return words;
                                }
                            }
                        }
                    }
                }

                try (InputStream is = StopWordNormalizer.class
                        .getResourceAsStream("/stopwords.txt")) {

                    if (is == null) {
                        return words;
                    }

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                        reader.lines().map(String::trim).forEach(words::add);
                    }
                }

            } catch (Exception ignored) {
                // Fail silently for now; empty stop-word list.
            }

            return words;
        }
    }

    /**
     * Composite Normalizer implementation that applies multiple normalizers
     * in sequence.
     *
     * Each normalizer receives the output of the previous one, allowing the
     * construction of normalization pipelines such as lowercase -> stop word
     * filtering -> stemming.
     */
    static class NormalizerChain implements Normalizer {

        private final List<Normalizer> normalizers;

        public NormalizerChain(final List<Normalizer> normalizers) {
            this.normalizers = List.copyOf(normalizers);
        }

        @Override
        public Optional<String> normalize(final String token) {

            Optional<String> current = Optional.ofNullable(token);

            for (final Normalizer normalizer : this.normalizers) {
                if (current.isEmpty()) {
                    return Optional.empty();
                }
                current = normalizer.normalize(current.get());
            }
            return current;
        }
    }
}
