package codex.ir;

import java.util.Arrays;
import java.util.List;

/**
 * Strategy interface for tokenizing text into indexable terms.
 *
 * Different implementations may apply different tokenization approaches,
 * such as whitespace splitting, regex-based tokenization, language-aware
 * tokenization, or domain-specific parsing.
 * @author jsanca
 */
public interface Tokenizer {

    /**
     * Splits the input text into tokens.
     *
     * @param text source text to tokenize
     * @return ordered list of extracted tokens
     */
    List<String> tokenize(String text);
}

