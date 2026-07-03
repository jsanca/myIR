/**
 * Internal site traversal strategies.
 * <p>
 * {@code SiteTraversalStrategy} implements breadth-first traversal with
 * frontier management, depth control, visited tracking, politeness delays,
 * and virtual-thread-based concurrent fetching.
 * {@code SeededWebPageTraversal} processes seed URIs into emitted pages.
 * This package is <b>internal</b> and not JPMS-exported.
 * </p>
 */
package codex.ir.ingestion.crawler.internal.traversal;
