/**
 * Concurrency utilities for the IR engine.
 * <p>
 * Provides virtual-thread execution ({@link codex.ir.concurrent.VTExecutor})
 * with configurable concurrency limits, and a {@link codex.ir.concurrent.Debouncer}
 * for coalescing repeated keyed invocations (used by corpus statistics refresh).
 * </p>
 */
package codex.ir.concurrent;
