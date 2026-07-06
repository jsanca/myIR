package codex.ir.indexer;

import codex.ir.Document;

import java.util.List;
import java.util.Objects;

/**
 * Analysis artifact produced by the preprocessing stage of the indexing pipeline.
 *
 * <p>A {@code PreprocessedDocument} pairs the enriched {@link Document} — which carries
 * {@code normalizedContent} and term-frequency metadata — with the ordered list of
 * normalized tokens that produced it. Carrying the token list avoids downstream stages
 * having to re-split {@code normalizedContent} or re-tokenize the document, eliminating
 * the repeated join→split cycle that would otherwise occur between preprocessing,
 * lexical indexing, and vector weighting.</p>
 *
 * <p>{@code normalizedContent} on the enclosed {@link Document} is retained for
 * backward compatibility with components that consume {@link Document} directly.
 * {@code tokens} is the authoritative ordered representation; both are guaranteed to be
 * consistent ({@code String.join(" ", tokens).equals(document.normalizedContent())}).</p>
 *
 * <p>Instances are produced by the internal {@code DocumentPreprocessor} inside
 * {@link Indexers} and are consumed within the same pipeline. They are not intended
 * for long-term storage.</p>
 */
public record PreprocessedDocument(Document document, List<String> tokens) {

    public PreprocessedDocument {
        Objects.requireNonNull(document, "document cannot be null");
        tokens = tokens == null ? List.of() : List.copyOf(tokens);
    }

    /**
     * Convenience accessor — delegates to {@code document().id()}.
     */
    public String id() {
        return document.id();
    }
}
