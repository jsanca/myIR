package codex.apps.siteexporter;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Configuration for the site-mirroring phase.
 *
 * <p>Drives the traversal crawler: which site to crawl, how deep to go,
 * how many pages to fetch, and where to write the mirrored HTML.</p>
 */
public record SiteMirrorOptions(
        URI seedUrl,
        Path outputDir,
        int maxPages,
        int maxDepth,
        boolean sameDomainOnly) {

    public SiteMirrorOptions {
        Objects.requireNonNull(seedUrl, "seedUrl");
        Objects.requireNonNull(outputDir, "outputDir");
        if (maxPages < 1) {
            throw new IllegalArgumentException("maxPages must be >= 1, got: " + maxPages);
        }
        if (maxDepth < 0) {
            throw new IllegalArgumentException("maxDepth must be >= 0, got: " + maxDepth);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private URI seedUrl;
        private Path outputDir = Path.of("./mirror");
        private int maxPages = 100;
        private int maxDepth = 3;
        private boolean sameDomainOnly = true;

        private Builder() {
        }

        public Builder seedUrl(final URI seedUrl) {
            this.seedUrl = Objects.requireNonNull(seedUrl, "seedUrl");
            return this;
        }

        public Builder outputDir(final Path outputDir) {
            this.outputDir = Objects.requireNonNull(outputDir, "outputDir");
            return this;
        }

        public Builder maxPages(final int maxPages) {
            this.maxPages = maxPages;
            return this;
        }

        public Builder maxDepth(final int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        public Builder sameDomainOnly(final boolean sameDomainOnly) {
            this.sameDomainOnly = sameDomainOnly;
            return this;
        }

        public SiteMirrorOptions build() {
            return new SiteMirrorOptions(seedUrl, outputDir, maxPages, maxDepth, sameDomainOnly);
        }
    }
}
