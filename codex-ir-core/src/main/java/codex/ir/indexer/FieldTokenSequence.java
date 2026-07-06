package codex.ir.indexer;

import java.util.List;
import java.util.Objects;

/**
 * Ordered normalized tokens for a single named field of a document.
 *
 * <p>A {@code FieldTokenSequence} is produced during preprocessing for each non-blank
 * field value present in a {@link codex.ir.Document}. It preserves the field name and
 * the exact token order so that downstream stages (field-aware posting insertion, field
 * boosting, score explanation) can reference terms by their source field without
 * re-parsing the whole-document {@code normalizedContent}.</p>
 *
 * <p>Blank fields are ignored at analysis time and produce no {@code FieldTokenSequence}.
 * When all fields are blank the preprocessor falls back to {@code rawContent} and
 * produces an empty field sequence list.</p>
 *
 * <p>Instances are produced inside {@link Indexers} and flow through the pipeline as
 * part of a {@link FieldAnalyzedDocument}. They are not stored in the corpus or
 * the inverted index.</p>
 */
public record FieldTokenSequence(String fieldName, List<String> tokens) {

    public FieldTokenSequence {
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        tokens = tokens == null ? List.of() : List.copyOf(tokens);
    }
}
