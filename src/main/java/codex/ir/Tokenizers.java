package codex.ir;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class Tokenizers {

    public static Tokenizer whitespace() {
        return new WhitespaceTokenizer();
    }
    /**
     * Default tokenizer implementation based on whitespace splitting.
     *
     * This implementation is intentionally simple and is suitable for the
     * first iteration of the IR engine.
     */
    static class WhitespaceTokenizer implements Tokenizer {

        @Override
        public List<String> tokenize(final String text) {
            if (text == null || text.isBlank()) {
                return List.of();
            }

            return Arrays.stream(text.split("\\s+"))
                    .filter(token -> !token.isBlank())
                    .toList();
        }
    }
}
