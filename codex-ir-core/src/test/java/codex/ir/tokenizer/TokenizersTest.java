package codex.ir.tokenizer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenizersTest {

    private final Tokenizer tokenizer = Tokenizers.whitespace();

    @Test
    void shouldReturnEmptyListForNullInput() {
        final List<String> tokens = tokenizer.tokenize(null);

        assertEquals(List.of(), tokens,
                "Expected empty list for null input");
    }

    @Test
    void shouldReturnEmptyListForEmptyInput() {
        final List<String> tokens = tokenizer.tokenize("");

        assertEquals(List.of(), tokens,
                "Expected empty list for empty input");
    }

    @Test
    void shouldReturnEmptyListForBlankInput() {
        final List<String> tokens = tokenizer.tokenize("   ");

        assertEquals(List.of(), tokens,
                "Expected empty list for blank (whitespace-only) input");
    }

    @Test
    void shouldSplitOnSingleSpace() {
        final List<String> tokens = tokenizer.tokenize("java search engine");

        assertEquals(List.of("java", "search", "engine"), tokens,
                "Expected single spaces to delimit tokens");
    }

    @Test
    void shouldSplitOnMultipleSpaces() {
        final List<String> tokens = tokenizer.tokenize("java   search    engine");

        assertEquals(List.of("java", "search", "engine"), tokens,
                "Expected multiple spaces to be treated as a single delimiter");
    }

    @Test
    void shouldSplitOnTabs() {
        final List<String> tokens = tokenizer.tokenize("java\tsearch\tengine");

        assertEquals(List.of("java", "search", "engine"), tokens,
                "Expected tabs to delimit tokens");
    }

    @Test
    void shouldSplitOnNewlines() {
        final List<String> tokens = tokenizer.tokenize("java\nsearch\nengine");

        assertEquals(List.of("java", "search", "engine"), tokens,
                "Expected newlines to delimit tokens");
    }

    @Test
    void shouldHandleMixedWhitespace() {
        final List<String> tokens = tokenizer.tokenize("java \t search \n\n engine");

        assertEquals(List.of("java", "search", "engine"), tokens,
                "Expected mixed whitespace (spaces, tabs, newlines) to delimit tokens");
    }

    @Test
    void shouldStripLeadingWhitespace() {
        final List<String> tokens = tokenizer.tokenize("   java search");

        assertEquals(List.of("java", "search"), tokens,
                "Expected leading whitespace to be stripped");
    }

    @Test
    void shouldStripTrailingWhitespace() {
        final List<String> tokens = tokenizer.tokenize("java search   ");

        assertEquals(List.of("java", "search"), tokens,
                "Expected trailing whitespace to be stripped");
    }

    @Test
    void shouldStripLeadingAndTrailingWhitespace() {
        final List<String> tokens = tokenizer.tokenize("   java search   ");

        assertEquals(List.of("java", "search"), tokens,
                "Expected leading and trailing whitespace to be stripped");
    }

    @Test
    void shouldPreservePunctuationAttachedToTokens() {
        final List<String> tokens = tokenizer.tokenize("hello, world! end.");

        assertEquals(List.of("hello,", "world!", "end."), tokens,
                "Expected punctuation attached to tokens to be preserved");
    }

    @Test
    void shouldPreservePunctuationInsideTokens() {
        final List<String> tokens = tokenizer.tokenize("state-of-the-art high-level");

        assertEquals(List.of("state-of-the-art", "high-level"), tokens,
                "Expected hyphens and inner punctuation to be preserved");
    }

    @Test
    void shouldPreserveUppercase() {
        final List<String> tokens = tokenizer.tokenize("Java IS Portable");

        assertEquals(List.of("Java", "IS", "Portable"), tokens,
                "Expected case to be preserved (lowercasing belongs to Normalizer)");
    }

    @Test
    void shouldPreserveMixedCase() {
        final List<String> tokens = tokenizer.tokenize("iPhone macOS PostgreSQL");

        assertEquals(List.of("iPhone", "macOS", "PostgreSQL"), tokens,
                "Expected mixed case to be preserved (normalization belongs to Normalizer)");
    }

    @Test
    void shouldNotRemoveStopWords() {
        final List<String> tokens = tokenizer.tokenize("the search is on the engine");

        assertEquals(List.of("the", "search", "is", "on", "the", "engine"), tokens,
                "Expected stop words to be preserved (stop-word removal belongs to Normalizer)");
    }

    @Test
    void shouldNotRemoveShortWords() {
        final List<String> tokens = tokenizer.tokenize("a b c d");

        assertEquals(List.of("a", "b", "c", "d"), tokens,
                "Expected short words to be preserved (tokenizer only splits on whitespace)");
    }

    @Test
    void shouldReturnSingleTokenForUninterruptedText() {
        final List<String> tokens = tokenizer.tokenize("supercalifragilisticexpialidocious");

        assertEquals(List.of("supercalifragilisticexpialidocious"), tokens,
                "Expected uninterrupted text without whitespace to produce a single token");
    }

    @Test
    void shouldReturnSingleTokenForNumbersAndSymbols() {
        final List<String> tokens = tokenizer.tokenize("123-456 hello@world");

        assertEquals(List.of("123-456", "hello@world"), tokens,
                "Expected tokens containing numbers and symbols to be preserved unchanged");
    }

    @Test
    void shouldProduceOrderedTokens() {
        final List<String> tokens = tokenizer.tokenize("first second third fourth fifth");

        assertEquals(List.of("first", "second", "third", "fourth", "fifth"), tokens,
                "Expected token order to match input order");
    }
}
