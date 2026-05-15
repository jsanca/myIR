package codex.ir.vector;

/**
 * Technical metadata associated with a sparse vector.
 *
 * <p>This record stores derived values that are useful for vector operations
 * but do not conceptually belong to the sparse dimensions themselves.</p>
 */
public record SparseVectorMetadata(
        double norm
) {

    /**
     * Creates vector metadata and validates the derived values.
     *
     * @throws IllegalArgumentException if {@code norm} is negative
     */
    public SparseVectorMetadata {

        if (norm < 0.0d) {
            throw new IllegalArgumentException("The norm must be >= 0");
        }
    }

}
