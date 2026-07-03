/**
 * Composable URL filtering predicates.
 * <p>
 * {@link codex.ir.ingestion.crawler.filter.UrlFilter} decides whether a
 * classified URL should be accepted for further processing.
 * {@link codex.ir.ingestion.crawler.filter.UrlFilters} provides combinators:
 * acceptAll, rejectAll, includeTypes, excludeTypes, pathStartsWith,
 * pathMatches, hasQueryParam, not, allOf, anyOf.
 * </p>
 */
package codex.ir.ingestion.crawler.filter;
