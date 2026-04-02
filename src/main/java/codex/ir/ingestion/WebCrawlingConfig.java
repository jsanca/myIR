package codex.ir.ingestion;

import java.util.Set;

/**
 * Configuration for web crawling behavior used by {@link DocumentSource} implementations
 * that produce {@link WebPage} instances.
 *
 * <p>This configuration expresses <b>intent</b> rather than implementation details.
 * It defines limits, scope, and politeness rules for crawling without exposing
 * internal crawler strategies.</p>
 *
 * <p>The goal is to keep the ingestion API simple for clients while allowing
 * the underlying crawler to evolve independently.</p>
 * @author jsanca & elo
 */
public record WebCrawlingConfig(
        int maxDepth,
        boolean followExternalLinks,
        boolean sameDomainOnly,
        int maxPages,
        long delayMillisBetweenRequests,
        int maxConcurrentRequests,
        Set<String> allowedContentTypes,
        boolean skipNonHtml,
        Set<String> allowedDomains,
        Set<String> disallowedPaths,
        int connectionTimeoutMillis,
        int readTimeoutMillis
) {

    /**
     * Canonical constructor ensuring safe defaults for collections.
     */
    public WebCrawlingConfig {
        allowedContentTypes = allowedContentTypes == null ? Set.of() : Set.copyOf(allowedContentTypes);
        allowedDomains = allowedDomains == null ? Set.of() : Set.copyOf(allowedDomains);
        disallowedPaths = disallowedPaths == null ? Set.of() : Set.copyOf(disallowedPaths);
    }

    /**
     * Lightweight configuration subset for HTTP client usage.
     */
    public record HttpClientConfig(
            int connectionTimeoutMillis,
            int readTimeoutMillis
    ) {
    }

    /**
     * Extracts the subset of configuration relevant for HTTP client implementations.
     *
     * @return configuration tailored for HTTP clients
     */
    public HttpClientConfig httpClientConfig() {
        return new HttpClientConfig(
                connectionTimeoutMillis,
                readTimeoutMillis
        );
    }

    // ----------------------------------------------------------------------
    // Default configurations
    // ----------------------------------------------------------------------

    /**
     * Returns a balanced default configuration suitable for most use cases.
     */
    public static WebCrawlingConfig defaultConfig() {
        return builder()
                .maxDepth(2)
                .maxPages(100)
                .sameDomainOnly(true)
                .followExternalLinks(false)
                .delayMillisBetweenRequests(200)
                .maxConcurrentRequests(2)
                .connectionTimeoutMillis(5_000)
                .readTimeoutMillis(5_000)
                .allowedContentTypes(Set.of("text/html"))
                .skipNonHtml(true)
                .build();
    }

    /**
     * Returns a conservative configuration with stricter politeness.
     */
    public static WebCrawlingConfig polite() {
        return builder()
                .maxDepth(2)
                .maxPages(50)
                .sameDomainOnly(true)
                .followExternalLinks(false)
                .delayMillisBetweenRequests(500)
                .maxConcurrentRequests(1)
                .connectionTimeoutMillis(5_000)
                .readTimeoutMillis(5_000)
                .allowedContentTypes(Set.of("text/html"))
                .skipNonHtml(true)
                .build();
    }

    /**
     * Returns a more aggressive configuration for fast crawling scenarios.
     */
    public static WebCrawlingConfig aggressive() {
        return builder()
                .maxDepth(4)
                .maxPages(500)
                .sameDomainOnly(false)
                .followExternalLinks(true)
                .delayMillisBetweenRequests(50)
                .maxConcurrentRequests(8)
                .connectionTimeoutMillis(3_000)
                .readTimeoutMillis(3_000)
                .allowedContentTypes(Set.of("text/html"))
                .skipNonHtml(true)
                .build();
    }

    // ----------------------------------------------------------------------
    // Builder
    // ----------------------------------------------------------------------

    /**
     * Creates a new builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private int maxDepth = 2;
        private boolean followExternalLinks = false;
        private boolean sameDomainOnly = true;
        private int maxPages = 100;
        private long delayMillisBetweenRequests = 200;
        private int maxConcurrentRequests = 2;
        private Set<String> allowedContentTypes = Set.of("text/html");
        private boolean skipNonHtml = true;
        private Set<String> allowedDomains = Set.of();
        private Set<String> disallowedPaths = Set.of();
        private int connectionTimeoutMillis = 5_000;
        private int readTimeoutMillis = 5_000;

        private Builder() {
        }

        public Builder maxDepth(final int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        public Builder followExternalLinks(final boolean followExternalLinks) {
            this.followExternalLinks = followExternalLinks;
            return this;
        }

        public Builder sameDomainOnly(final boolean sameDomainOnly) {
            this.sameDomainOnly = sameDomainOnly;
            return this;
        }

        public Builder maxPages(final int maxPages) {
            this.maxPages = maxPages;
            return this;
        }

        public Builder delayMillisBetweenRequests(final long delayMillisBetweenRequests) {
            this.delayMillisBetweenRequests = delayMillisBetweenRequests;
            return this;
        }

        public Builder maxConcurrentRequests(final int maxConcurrentRequests) {
            this.maxConcurrentRequests = maxConcurrentRequests;
            return this;
        }

        public Builder allowedContentTypes(final Set<String> allowedContentTypes) {
            this.allowedContentTypes = allowedContentTypes;
            return this;
        }

        public Builder skipNonHtml(final boolean skipNonHtml) {
            this.skipNonHtml = skipNonHtml;
            return this;
        }

        public Builder allowedDomains(final Set<String> allowedDomains) {
            this.allowedDomains = allowedDomains;
            return this;
        }

        public Builder disallowedPaths(final Set<String> disallowedPaths) {
            this.disallowedPaths = disallowedPaths;
            return this;
        }

        public Builder connectionTimeoutMillis(final int connectionTimeoutMillis) {
            this.connectionTimeoutMillis = connectionTimeoutMillis;
            return this;
        }

        public Builder readTimeoutMillis(final int readTimeoutMillis) {
            this.readTimeoutMillis = readTimeoutMillis;
            return this;
        }

        /**
         * Builds the immutable configuration instance.
         */
        public WebCrawlingConfig build() {
            if (maxDepth < 0) {
                throw new IllegalArgumentException("maxDepth must be >= 0");
            }
            if (maxPages <= 0) {
                throw new IllegalArgumentException("maxPages must be > 0");
            }
            if (maxConcurrentRequests <= 0) {
                throw new IllegalArgumentException("maxConcurrentRequests must be > 0");
            }
            if (connectionTimeoutMillis <= 0 || readTimeoutMillis <= 0) {
                throw new IllegalArgumentException("timeouts must be > 0");
            }

            return new WebCrawlingConfig(
                    maxDepth,
                    followExternalLinks,
                    sameDomainOnly,
                    maxPages,
                    delayMillisBetweenRequests,
                    maxConcurrentRequests,
                    allowedContentTypes,
                    skipNonHtml,
                    allowedDomains,
                    disallowedPaths,
                    connectionTimeoutMillis,
                    readTimeoutMillis
            );
        }
    }
}
