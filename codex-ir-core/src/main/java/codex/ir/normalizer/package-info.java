/**
 * Token normalization strategies.
 * <p>
 * {@link codex.ir.normalizer.Normalizer} defines the contract for transforming
 * tokens into their canonical form. {@link codex.ir.normalizer.Normalizers}
 * provides implementations: lowercasing, accent folding, stop-word removal
 * (English and Spanish), and composite chains via {@code chain()}.
 * </p>
 */
package codex.ir.normalizer;
