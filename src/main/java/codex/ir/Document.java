package codex.ir;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a document inside the Information Retrieval engine.
 *
 * A document contains:
 * - a unique identifier
 * - the raw content (original text)
 * - the normalized content (clean tokens used for indexing)
 * - metadata describing the document
 *
 * The design intentionally separates raw and normalized text so that
 * the indexing pipeline can operate only on cleaned tokens while still
 * preserving the original text for debugging, highlighting, or re‑processing.
 * @author jsanca
 */
public record Document(String id,
                       String rawContent,
                       String normalizedContent,
                       DocumentMetadata metadata) {

    /**
     * Canonical constructor ensuring metadata is never null.
     */
    public Document {
        metadata = metadata == null ? DocumentMetadata.empty() : metadata;
    }

    /**
     * Convenience factory when no metadata is required.
     */
    public static Document of(final String id,
                              final String rawContent,
                              final String normalizedContent) {
        return new Document(id, rawContent, normalizedContent, DocumentMetadata.empty());
    }

    /**
     * Entry point for the Builder pattern.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Metadata associated with a document.
     *
     * The structure intentionally mixes:
     * - strongly typed core fields
     * - an extensible attribute map
     *
     * This mirrors patterns used in systems like LangChain where metadata
     * must remain flexible and domain‑specific.
     */
    public record DocumentMetadata(
            String title,
            String source,
            Integer length,
            Integer uniqueTerms,
            Map<String, Object> attributes
    ) {

        /**
         * Ensures the attribute map is never null and is immutable.
         */
        public DocumentMetadata {
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }

        /**
         * Creates an empty metadata instance.
         */
        public static DocumentMetadata empty() {
            return new DocumentMetadata(null, null, null, null, Map.of());
        }
    }

    /**
     * Builder used to construct Document instances with optional metadata.
     *
     * This makes the API easier to use when many optional metadata fields
     * are involved.
     */
    public static class Builder {

        private String id;
        private String rawContent;
        private String normalizedContent;

        private String title;
        private String source;
        private Integer length;
        private Integer uniqueTerms;

        private final Map<String, Object> attributes = new HashMap<>();

        public Builder id(final String id) {
            this.id = id;
            return this;
        }

        public Builder rawContent(final String rawContent) {
            this.rawContent = rawContent;
            return this;
        }

        public Builder normalizedContent(final String normalizedContent) {
            this.normalizedContent = normalizedContent;
            return this;
        }

        public Builder title(final String title) {
            this.title = title;
            return this;
        }

        public Builder source(final String source) {
            this.source = source;
            return this;
        }

        public Builder length(final Integer length) {
            this.length = length;
            return this;
        }

        public Builder uniqueTerms(final Integer uniqueTerms) {
            this.uniqueTerms = uniqueTerms;
            return this;
        }

        /**
         * Adds a single metadata attribute.
         */
        public Builder attribute(final String key, final Object value) {
            this.attributes.put(key, value);
            return this;
        }

        /**
         * Adds a full map of attributes.
         */
        public Builder attributes(final Map<String, Object> attributes) {
            if (attributes != null) {
                this.attributes.putAll(attributes);
            }
            return this;
        }

        /**
         * Builds the final Document instance.
         */
        public Document build() {

            DocumentMetadata metadata = new DocumentMetadata(
                    title,
                    source,
                    length,
                    uniqueTerms,
                    attributes
            );

            return new Document(
                    id,
                    rawContent,
                    normalizedContent,
                    metadata
            );
        }
    }
}