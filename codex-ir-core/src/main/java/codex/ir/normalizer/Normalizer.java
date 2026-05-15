package codex.ir.normalizer;

import java.util.Optional;

/**
 * Strategy interface responsible for normalizing tokens before indexing.
 *
 * Normalization typically includes operations such as:
 * - lowercasing
 * - punctuation trimming
 * - accent removal
 * - language-specific normalization
 *
 * Different implementations can be plugged depending on the domain
 * or language being processed.
 * @author jsanca
 */
public interface Normalizer {

    /**
     * Normalizes a token into its indexable representation.
     *
     * @param token raw token extracted from the tokenizer
     * @return normalized token suitable for indexing
     */
    Optional<String> normalize(String token);
}
