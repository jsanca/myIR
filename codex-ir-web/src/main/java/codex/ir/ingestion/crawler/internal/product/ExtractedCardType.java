package codex.ir.ingestion.crawler.internal.product;

/**
 * Broad classification of an extracted card based on URL and label heuristics.
 */
public enum ExtractedCardType {
    PRODUCT,
    CATEGORY,
    NAVIGATION,
    UNKNOWN
}
