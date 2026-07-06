package codex.ir.indexer;

import codex.ir.Document;

import java.util.List;
import java.util.Objects;

/**
 * Analysis artifact produced after field-aware preprocessing.
 *
 * <p>A {@code FieldAnalyzedDocument} extends the {@link PreprocessedDocument} artifact
 * with per-field token sequences. The {@link #base()} carries the whole-document
 * normalized content and term frequencies, which are unchanged by field analysis.
 * The {@link #fieldSequences()} list holds one {@link FieldTokenSequence} per
 * non-blank field, preserving provenance (which terms came from which field)
 * without affecting whole-document ranking or search behavior.</p>
 *
 * <p>Field sequences are present only when the document was indexed via structured
 * fields and at least one field value was non-blank. When the preprocessor falls back
 * to {@code rawContent} (all fields absent or blank), {@link #fieldSequences()} is
 * empty.</p>
 *
 * <p>Whole-document metadata ({@code normalizedContent}, {@code termFrequencies},
 * document length) is owned by the enclosed {@link PreprocessedDocument} and is
 * not duplicated here. Per-field tokens are additional analysis data; they do not
 * alter the whole-document model.</p>
 *
 * <p>Instances flow through the internal indexing pipeline and are not intended for
 * long-term storage.</p>
 */
public record FieldAnalyzedDocument(PreprocessedDocument base, List<FieldTokenSequence> fieldSequences) {

    public FieldAnalyzedDocument {
        Objects.requireNonNull(base, "base cannot be null");
        fieldSequences = fieldSequences == null ? List.of() : List.copyOf(fieldSequences);
    }

    /** Whether per-field token sequences are available for this document. */
    public boolean hasFieldSequences() {
        return !fieldSequences.isEmpty();
    }

    /** Delegates to {@code base().id()}. */
    public String id() {
        return base.id();
    }

    /** Delegates to {@code base().document()}. */
    public Document document() {
        return base.document();
    }

    /** Delegates to {@code base().tokens()} — whole-document token list. */
    public List<String> tokens() {
        return base.tokens();
    }
}
