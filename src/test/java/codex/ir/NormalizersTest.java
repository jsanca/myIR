package codex.ir;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NormalizersTest {

    @Test
    void basicShouldLowercaseAndTrimNonAlphaNumericCharacters() {
        final Optional<String> result = Normalizers.basic().normalize("...Hello123!!!");

        assertTrue(result.isPresent());
        assertEquals("hello123", result.get());
    }

    @Test
    void basicShouldReturnEmptyWhenTokenIsNull() {
        final Optional<String> result = Normalizers.basic().normalize(null);

        assertTrue(result.isEmpty());
    }

    @Test
    void basicShouldReturnEmptyWhenTokenBecomesEmptyAfterNormalization() {
        final Optional<String> result = Normalizers.basic().normalize("!!!");

        assertTrue(result.isEmpty());
    }

    @Test
    void lowercaseShouldConvertTokenToLowercase() {
        final Optional<String> result = Normalizers.lowercase().normalize("HeLLo");

        assertTrue(result.isPresent());
        assertEquals("hello", result.get());
    }

    @Test
    void lowercaseShouldPreserveWhitespaceAndInnerCharacters() {
        final Optional<String> result = Normalizers.lowercase().normalize("  HeLLo World  ");

        assertTrue(result.isPresent());
        assertEquals("  hello world  ", result.get());
    }

    @Test
    void lowercaseShouldReturnEmptyWhenTokenIsNull() {
        final Optional<String> result = Normalizers.lowercase().normalize(null);

        assertTrue(result.isEmpty());
    }

    @Test
    void trimNonAlphaNumericShouldTrimPunctuationFromBeginningAndEnd() {
        final Optional<String> result = Normalizers.trimNonAlphaNumeric().normalize("...hello123!!!");

        assertTrue(result.isPresent());
        assertEquals("hello123", result.get());
    }

    @Test
    void trimNonAlphaNumericShouldPreserveInnerSpecialCharacters() {
        final Optional<String> result = Normalizers.trimNonAlphaNumeric().normalize("hello-world");

        assertTrue(result.isPresent());
        assertEquals("hello-world", result.get());
    }

    @Test
    void trimNonAlphaNumericShouldReturnEmptyWhenTokenIsNull() {
        final Optional<String> result = Normalizers.trimNonAlphaNumeric().normalize(null);

        assertTrue(result.isEmpty());
    }

    @Test
    void trimNonAlphaNumericShouldReturnEmptyWhenTokenContainsOnlyTrimmedCharacters() {
        final Optional<String> result = Normalizers.trimNonAlphaNumeric().normalize("***");

        assertTrue(result.isEmpty());
    }

    @Test
    void accentFoldingShouldRemoveSpanishDiacritics() {
        final Optional<String> result = Normalizers.accentFolding().normalize("canción");

        assertTrue(result.isPresent());
        assertEquals("cancion", result.get());
    }

    @Test
    void accentFoldingShouldHandleUppercaseDiacriticsWithoutChangingCase() {
        final Optional<String> result = Normalizers.accentFolding().normalize("ÁRBOL");

        assertTrue(result.isPresent());
        assertEquals("ARBOL", result.get());
    }

    @Test
    void accentFoldingShouldReturnEmptyWhenTokenIsNull() {
        final Optional<String> result = Normalizers.accentFolding().normalize(null);

        assertTrue(result.isEmpty());
    }

    @Test
    void stopWordsShouldFilterKnownStopWord() {
        final Optional<String> result = Normalizers.stopWords().normalize("the");

        assertTrue(result.isEmpty());
    }

    @Test
    void stopWordsShouldKeepNonStopWord() {
        final Optional<String> result = Normalizers.stopWords().normalize("galaxy");

        assertTrue(result.isPresent());
        assertEquals("galaxy", result.get());
    }

    @Test
    void stopWordsShouldReturnEmptyWhenTokenIsNull() {
        final Optional<String> result = Normalizers.stopWords().normalize(null);

        assertTrue(result.isEmpty());
    }

    @Test
    void chainShouldApplyAllNormalizersInOrder() {
        final Normalizer chain = Normalizers.chain(
                Normalizers.lowercase(),
                Normalizers.accentFolding(),
                Normalizers.trimNonAlphaNumeric()
        );

        final Optional<String> result = chain.normalize("...Canción!!!");

        assertTrue(result.isPresent());
        assertEquals("cancion", result.get());
    }

    @Test
    void chainShouldShortCircuitWhenOneNormalizerReturnsEmpty() {
        final Normalizer chain = Normalizers.chain(
                Normalizers.lowercase(),
                Normalizers.trimNonAlphaNumeric(),
                Normalizers.stopWords()
        );

        final Optional<String> result = chain.normalize("...THE!!!");

        assertTrue(result.isEmpty());
    }

    @Test
    void chainShouldReturnEmptyWhenInitialTokenIsNull() {
        final Normalizer chain = Normalizers.chain(
                Normalizers.lowercase(),
                Normalizers.trimNonAlphaNumeric()
        );

        final Optional<String> result = chain.normalize(null);

        assertTrue(result.isEmpty());
    }

    @Test
    void minimalShouldLowercaseAndTrim() {
        final Optional<String> result = Normalizers.minimal().normalize("...HeLLo!!!");

        assertTrue(result.isPresent());
        assertEquals("hello", result.get());
    }

    @Test
    void englishShouldLowercaseTrimAndRemoveStopWords() {
        final Optional<String> result = Normalizers.english().normalize("...THE!!!");

        assertTrue(result.isEmpty());
    }

    @Test
    void englishShouldKeepUsefulTerms() {
        final Optional<String> result = Normalizers.english().normalize("...Running!!!");

        assertTrue(result.isPresent());
        assertEquals("running", result.get());
    }

    @Test
    void spanishShouldLowercaseFoldAccentsAndTrim() {
        final Optional<String> result = Normalizers.spanish().normalize("...NIÑO!!!");

        assertTrue(result.isPresent());
        assertEquals("nino", result.get());
    }

    @Test
    void spanishShouldNotDropUsefulTerms() {
        final Optional<String> result = Normalizers.spanish().normalize("acción");

        assertTrue(result.isPresent());
        assertEquals("accion", result.get());
    }

    @Test
    void defaultEnglishShouldDelegateToEnglishPipeline() {
        final Optional<String> defaultResult = Normalizers.defaultEnglish().normalize("...THE!!!");
        final Optional<String> englishResult = Normalizers.english().normalize("...THE!!!");

        assertEquals(englishResult, defaultResult);
    }

    @Test
    void chainWithoutNormalizersShouldReturnOriginalTokenWrappedInOptional() {
        final Normalizer chain = Normalizers.chain();

        final Optional<String> result = chain.normalize("RawToken");

        assertTrue(result.isPresent());
        assertEquals("RawToken", result.get());
    }

    @Test
    void chainWithoutNormalizersShouldReturnEmptyWhenTokenIsNull() {
        final Normalizer chain = Normalizers.chain();

        final Optional<String> result = chain.normalize(null);

        assertFalse(result.isPresent());
    }
}
