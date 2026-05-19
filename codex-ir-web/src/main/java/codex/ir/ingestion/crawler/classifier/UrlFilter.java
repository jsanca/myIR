package codex.ir.ingestion.crawler.classifier;

/**
 * Decides whether a classified URL should be accepted for further
 * processing (e.g. fetching, extraction).
 *
 * <p>While {@link UrlClassifier} determines what a URL <em>is</em>,
 * a {@code UrlFilter} decides whether it should be <em>included</em>.
 * This separation allows users to classify broadly and filter
 * selectively.</p>
 */
@FunctionalInterface
public interface UrlFilter {

    /**
     * Returns {@code true} if the classified URL should be accepted.
     *
     * @param classifiedUrl previously classified URL
     * @return true to include, false to exclude
     */
    boolean accepts(ClassifiedUrl classifiedUrl);
}
