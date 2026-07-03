/**
 * URL and page classification.
 * <p>
 * {@link codex.ir.ingestion.crawler.classifier.UrlClassifier} categorizes URIs
 * by path pattern into {@link codex.ir.ingestion.crawler.classifier.UrlType}
 * (HOMEPAGE, PRODUCT, CATEGORY, etc.).
 * {@link codex.ir.ingestion.crawler.classifier.PageClassifier} refines
 * classification by inspecting HTML content, detecting WordPress/WooCommerce
 * patterns. Results are captured in {@link codex.ir.ingestion.crawler.classifier.PageClassification}
 * and {@link codex.ir.ingestion.crawler.classifier.ClassifiedUrl}.
 * </p>
 */
package codex.ir.ingestion.crawler.classifier;
