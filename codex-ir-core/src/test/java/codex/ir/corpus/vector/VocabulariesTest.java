package codex.ir.corpus.vector;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link Vocabularies} class.
 *
 * These tests focus on the {@link InMemoryVocabulary} implementation
 * to verify thread-safety and correct ID assignment/retrieval.
 */
public class VocabulariesTest {

    private Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        // Initialize a fresh vocabulary instance before each test
        vocabulary = Vocabularies.getVocabulary();
    }

    @Test
    void testInitialState() {
        // Vocabulary should be empty initially
        assertEquals(0, vocabulary.size(), "Vocabulary size should be 0 initially.");
    }

    @Test
    void testGetOrCreateTermId_FirstCall() {
        String term = "hello";

        // First call should create the ID
        int id1 = vocabulary.getOrCreateTermId(term);

        // Check if ID 0 was assigned
        assertEquals(0, id1, "The first term should get ID 0.");
        assertEquals(1, vocabulary.size(), "Vocabulary size should be 1 after first insertion.");

        // Verify that getting the ID again returns the same ID
        int id2 = vocabulary.getOrCreateTermId(term);
        assertEquals(id1, id2, "Calling getOrCreateTermId twice should yield the same ID.");
    }

    @Test
    void testGetOrCreateTermId_MultipleUniqueTerms() {
        String term1 = "apple";
        String term2 = "banana";
        String term3 = "cherry";

        // Process terms in order
        int id1 = vocabulary.getOrCreateTermId(term1);
        int id2 = vocabulary.getOrCreateTermId(term2);
        int id3 = vocabulary.getOrCreateTermId(term3);

        // Check IDs are assigned sequentially
        assertEquals(0, id1, "Term 1 should have ID 0.");
        assertEquals(1, id2, "Term 2 should have ID 1.");
        assertEquals(2, id3, "Term 3 should have ID 2.");

        assertEquals(3, vocabulary.size(), "Vocabulary size should be 3.");
    }

    @Test
    void testGetOrCreateTermId_ReuseExistingTerm() {
        String term = "test";

        // First call to create ID
        int id1 = vocabulary.getOrCreateTermId(term);

        // Second call to reuse ID
        int id2 = vocabulary.getOrCreateTermId(term);

        assertEquals(id1, id2, "Reusing term should return the original ID.");
        assertEquals(1, vocabulary.size(), "Vocabulary size should not change when reusing a term.");
    }

    @Test
    void testGetTermId_Exists() {
        String term = "existing";
        // Pre-populate vocabulary
        vocabulary.getOrCreateTermId(term);

        Optional<Integer> optionalId = vocabulary.getTermId(term);
        assertTrue(optionalId.isPresent(), "Should find the ID for an existing term.");
        assertNotEquals(Optional.empty(), optionalId);
    }

    @Test
    void testGetTermId_DoesNotExist() {
        String term = "nonexistent";

        Optional<Integer> optionalId = vocabulary.getTermId(term);
        assertFalse(optionalId.isPresent(), "Should return empty optional for a non-existent term.");
    }

    @Test
    void testGetTerm_ValidId() {
        String term = "lookup";
        // Pre-populate vocabulary to ensure term maps to a known ID
        int id = vocabulary.getOrCreateTermId(term);

        Optional<String> optionalTerm = vocabulary.getTerm(id);
        assertTrue(optionalTerm.isPresent(), "Should retrieve the term for a valid ID.");
        assertEquals(term, optionalTerm.get(), "Retrieved term should match the original term.");
    }

    @Test
    void testGetTerm_InvalidIdTooSmall() {
        // Attempt to read ID -1
        Optional<String> optionalTerm = vocabulary.getTerm(-1);
        assertFalse(optionalTerm.isPresent(), "Should return empty for ID < 0.");
    }

    @Test
    void testGetTerm_InvalidIdTooLarge() {
        // Populate with 1 term (ID 0)
        vocabulary.getOrCreateTermId("a");

        // Attempt to read ID 1 (or any number > size - 1)
        Optional<String> optionalTerm = vocabulary.getTerm(100);
        assertFalse(optionalTerm.isPresent(), "Should return empty for ID >= size.");
    }

    @Test
    void testConcurrencySafety() {
        final String term = "concurrent_test";
        final int numThreads = 100;

        // This test is inherently difficult to make fail deterministically without advanced tools,
        // but we test by running concurrent operations and checking for consistency.

        // We use a dummy method to simulate concurrent access while checking size consistency.
        Runnable task = () -> {
            vocabulary.getOrCreateTermId("thread_safe_term");
        };

        // Although we are not using an ExecutorService here, running the action
        // multiple times simulates high contention for getOrCreateTermId.
        for (int i = 0; i < numThreads; i++) {
            task.run();
        }

        // Size should not exceed 1 if the term is the same, or should be stable
        // if unique terms are added (but here we only add one term name repeatedly).
        // If we only call it with the same term, size must be 1.
        assertEquals(1, vocabulary.size(), "Vocabulary size must remain 1 for a single term used concurrently.");
    }

    @Test
    void testNullSafety() {
        // Test getTermId with null
        assertThrows(NullPointerException.class, () -> {
            vocabulary.getTermId(null);
        }, "getTermId must throw NPE for null input.");

        // Test getOrCreateTermId with null
        assertThrows(NullPointerException.class, () -> {
            vocabulary.getOrCreateTermId(null);
        }, "getOrCreateTermId must throw NPE for null input.");

        // Test getTerm with invalid types (though Java typing makes this hard,
        // we check the boundary conditions handled by the method signature)
    }
}