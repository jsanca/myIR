/**
 * URI canonicalization pipeline.
 * <p>
 * {@link codex.ir.canonicalizer.UriCanonicalizer} normalizes URIs before
 * crawling and ingestion. {@link codex.ir.canonicalizer.UriCanonicalizers}
 * provides a default web pipeline: fragment removal, scheme lowercasing,
 * default port removal, path normalization, and query parameter sorting.
 * </p>
 */
package codex.ir.canonicalizer;
