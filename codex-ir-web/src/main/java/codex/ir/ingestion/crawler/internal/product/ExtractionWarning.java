package codex.ir.ingestion.crawler.internal.product;

/**
 * Extraction quality warnings for diagnostic purposes.
 *
 * <p>Warnings are purely informational and do not affect extraction behavior.</p>
 */
public enum ExtractionWarning {
    PRICE_LOOKS_SUSPICIOUS,
    MISSING_SKU,
    MISSING_IMAGE,
    MISSING_DESCRIPTION,
    CARD_CLASSIFIED_AS_NAVIGATION,
    UNKNOWN_CARD_TYPE
}
