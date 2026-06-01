package codex.ir.ingestion.crawler.internal.classifier;

import codex.ir.ingestion.WebPage;
import codex.ir.ingestion.crawler.classifier.ClassifiedUrl;
import codex.ir.ingestion.crawler.classifier.PageClassification;
import codex.ir.ingestion.crawler.classifier.PageClassifier;
import codex.ir.ingestion.crawler.classifier.UrlClassifier;
import codex.ir.ingestion.crawler.classifier.UrlType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Objects;

/**
 * General-purpose Jsoup-based page classifier using weighted HTML signal scoring.
 *
 * <p>Scores product, category, and article signals independently and returns
 * the type whose score first reaches the win threshold. Falls back to the
 * URL-based type when no signal wins.</p>
 */
public final class JsoupGenericPageClassifier implements PageClassifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsoupGenericPageClassifier.class);
    private static final int WIN_THRESHOLD = 3;

    private final UrlClassifier urlClassifier;

    public JsoupGenericPageClassifier(final UrlClassifier urlClassifier) {
        this.urlClassifier = Objects.requireNonNull(urlClassifier, "urlClassifier must not be null");
    }

    @Override
    public PageClassification classify(final WebPage page) {
        final URI uri = page.url();
        final ClassifiedUrl urlClassification = urlClassifier.classify(uri);
        final UrlType urlType = urlClassification.type();

        if (HtmlSignals.PASS_THROUGH_TYPES.contains(urlType)) {
            return new PageClassification(uri, urlType, urlClassification, false, false);
        }

        if (urlType == UrlType.HOMEPAGE) {
            return new PageClassification(uri, UrlType.HOMEPAGE, urlClassification, false, false);
        }

        final String html = page.rawHtml();
        if (html == null || html.isBlank()) {
            return new PageClassification(uri, urlType, urlClassification, false, false);
        }

        final Document doc;
        try {
            doc = Jsoup.parse(html, uri.toString());
        } catch (final Exception ex) {
            LOGGER.debug("Failed to parse HTML for {}", uri, ex);
            return new PageClassification(uri, urlType, urlClassification, false, false);
        }

        final UrlType refined = selectType(doc, urlClassification);
        return new PageClassification(uri, refined, urlClassification, false, false);
    }

    private UrlType selectType(final Document doc, final ClassifiedUrl urlClassification) {
        final int product = scoreProduct(doc, urlClassification);
        final int category = scoreCategory(doc, urlClassification);
        final int article = scoreArticle(doc, urlClassification);

        if (product >= WIN_THRESHOLD || category >= WIN_THRESHOLD || article >= WIN_THRESHOLD) {
            if (product >= category && product >= article) return UrlType.PRODUCT;
            if (category >= article) return UrlType.CATEGORY;
            return UrlType.ARTICLE;
        }

        return urlClassification.type();
    }

    private int scoreProduct(final Document doc, final ClassifiedUrl url) {
        int score = 0;
        if (url.type() == UrlType.PRODUCT) score += 2;
        if (HtmlSignals.hasJsonLdType(doc, "Product")) score += 3;
        final String ogType = HtmlSignals.ogType(doc);
        if ("product".equalsIgnoreCase(ogType) || "og:product".equalsIgnoreCase(ogType)) score += 3;
        if (!doc.select("[itemprop=price], [class*=price]").isEmpty()) score += 2;
        if (hasBuyIntent(doc)) score += 2;
        return score;
    }

    private int scoreCategory(final Document doc, final ClassifiedUrl url) {
        int score = 0;
        if (url.type() == UrlType.CATEGORY) score += 2;
        final int cards = doc.select(
                "[class*=product-card], [class*=product-item], .products > li, [class*=product-grid] > *"
        ).size();
        if (cards >= 3) score += 3;
        else if (cards >= 1) score += 1;
        return score;
    }

    private int scoreArticle(final Document doc, final ClassifiedUrl url) {
        int score = 0;
        if (url.type() == UrlType.BLOG_POST) score += 2;
        if (HtmlSignals.hasJsonLdType(doc, "Article")
                || HtmlSignals.hasJsonLdType(doc, "NewsArticle")
                || HtmlSignals.hasJsonLdType(doc, "BlogPosting")) score += 3;
        final String ogType = HtmlSignals.ogType(doc);
        if ("article".equalsIgnoreCase(ogType)) score += 3;
        if (!doc.select("article").isEmpty()) score += 2;
        if (!doc.select("time[datetime]").isEmpty()) score += 1;
        return score;
    }

    private boolean hasBuyIntent(final Document doc) {
        for (final Element el : doc.select("button, a, input[type=submit]")) {
            final String text = el.text().toLowerCase();
            if (text.contains("add to cart") || text.contains("buy now")
                    || text.contains("add to bag") || text.contains("add to basket")) {
                return true;
            }
        }
        return false;
    }
}
