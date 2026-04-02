package codex.ir.ingestion;

import codex.ir.Document;

/**
 * Transforms an input element from a {@link DocumentSource} into a {@link Document}.
 *
 * <p>This abstraction represents the mapping stage of the ingestion pipeline,
 * where raw or intermediate data (such as HTML pages, files, or external records)
 * is converted into the canonical {@link Document} model used by the indexing
 * engine.</p>
 *
 * <p>Typical responsibilities of a mapper include:</p>
 * <ul>
 *     <li>extracting relevant text (e.g., title, body)</li>
 *     <li>populating structured fields</li>
 *     <li>building normalized content</li>
 *     <li>enriching metadata when applicable</li>
 * </ul>
 *
 * @param <T> the type of input consumed by the mapper
 */
@FunctionalInterface
public interface DocumentMapper<T> {
    /**
     * Maps the given input element into a {@link Document}.
     *
     * <p>The implementation defines how raw input data is interpreted and
     * transformed into the internal document representation.</p>
     *
     * @param input source element to be mapped
     * @return a fully constructed {@link Document}
     */
    Document map(T input);
}
