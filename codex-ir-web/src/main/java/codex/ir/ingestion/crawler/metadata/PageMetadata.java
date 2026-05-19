package codex.ir.ingestion.crawler.metadata;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Metadata extracted from an HTML page head.
 *
 * <p>All components are non-null. Map and List components are immutable copies.
 * Use {@link #empty()} for an all-absent instance, {@link #builder()} to construct one,
 * and {@link #toBuilder()} to derive a modified copy.
 */
public record PageMetadata(
        Optional<String> title,
        Optional<String> description,
        Optional<String> canonicalUrl,
        Map<String, String> openGraph,
        Map<String, String> twitterCard,
        List<String> robotsDirectives,
        List<String> h1Headings,
        List<String> h2Headings,
        Optional<String> language,
        List<String> jsonLdBlocks
) {

    public PageMetadata {
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(canonicalUrl, "canonicalUrl must not be null");
        Objects.requireNonNull(openGraph, "openGraph must not be null");
        Objects.requireNonNull(twitterCard, "twitterCard must not be null");
        Objects.requireNonNull(robotsDirectives, "robotsDirectives must not be null");
        Objects.requireNonNull(h1Headings, "h1Headings must not be null");
        Objects.requireNonNull(h2Headings, "h2Headings must not be null");
        Objects.requireNonNull(language, "language must not be null");
        Objects.requireNonNull(jsonLdBlocks, "jsonLdBlocks must not be null");
        openGraph = Map.copyOf(openGraph);
        twitterCard = Map.copyOf(twitterCard);
        robotsDirectives = List.copyOf(robotsDirectives);
        h1Headings = List.copyOf(h1Headings);
        h2Headings = List.copyOf(h2Headings);
        jsonLdBlocks = List.copyOf(jsonLdBlocks);
    }

    /** Returns a {@code PageMetadata} with all fields absent or empty. */
    public static PageMetadata empty() {
        return new PageMetadata(
                Optional.empty(), Optional.empty(), Optional.empty(),
                Map.of(), Map.of(), List.of(), List.of(), List.of(),
                Optional.empty(), List.of()
        );
    }

    /** Returns a new {@link Builder}. */
    public static Builder builder() {
        return new Builder();
    }

    /** Returns a {@link Builder} pre-populated with this instance's values. */
    public Builder toBuilder() {
        final Builder b = new Builder();
        title.ifPresent(b::title);
        description.ifPresent(b::description);
        canonicalUrl.ifPresent(b::canonicalUrl);
        b.openGraph(openGraph);
        b.twitterCard(twitterCard);
        b.robotsDirectives(robotsDirectives);
        b.h1Headings(h1Headings);
        b.h2Headings(h2Headings);
        language.ifPresent(b::language);
        b.jsonLdBlocks(jsonLdBlocks);
        return b;
    }

    /** Builder for {@link PageMetadata}. */
    public static final class Builder {

        private String title;
        private String description;
        private String canonicalUrl;
        private Map<String, String> openGraph = Map.of();
        private Map<String, String> twitterCard = Map.of();
        private List<String> robotsDirectives = List.of();
        private List<String> h1Headings = List.of();
        private List<String> h2Headings = List.of();
        private String language;
        private List<String> jsonLdBlocks = List.of();

        private Builder() {
        }

        public Builder title(final String title) {
            this.title = Objects.requireNonNull(title);
            return this;
        }

        public Builder description(final String description) {
            this.description = Objects.requireNonNull(description);
            return this;
        }

        public Builder canonicalUrl(final String canonicalUrl) {
            this.canonicalUrl = Objects.requireNonNull(canonicalUrl);
            return this;
        }

        public Builder openGraph(final Map<String, String> openGraph) {
            this.openGraph = Objects.requireNonNull(openGraph);
            return this;
        }

        public Builder twitterCard(final Map<String, String> twitterCard) {
            this.twitterCard = Objects.requireNonNull(twitterCard);
            return this;
        }

        public Builder robotsDirectives(final List<String> robotsDirectives) {
            this.robotsDirectives = Objects.requireNonNull(robotsDirectives);
            return this;
        }

        public Builder h1Headings(final List<String> h1Headings) {
            this.h1Headings = Objects.requireNonNull(h1Headings);
            return this;
        }

        public Builder h2Headings(final List<String> h2Headings) {
            this.h2Headings = Objects.requireNonNull(h2Headings);
            return this;
        }

        public Builder language(final String language) {
            this.language = Objects.requireNonNull(language);
            return this;
        }

        public Builder jsonLdBlocks(final List<String> jsonLdBlocks) {
            this.jsonLdBlocks = Objects.requireNonNull(jsonLdBlocks);
            return this;
        }

        public PageMetadata build() {
            return new PageMetadata(
                    Optional.ofNullable(title),
                    Optional.ofNullable(description),
                    Optional.ofNullable(canonicalUrl),
                    openGraph,
                    twitterCard,
                    robotsDirectives,
                    h1Headings,
                    h2Headings,
                    Optional.ofNullable(language),
                    jsonLdBlocks
            );
        }
    }
}
