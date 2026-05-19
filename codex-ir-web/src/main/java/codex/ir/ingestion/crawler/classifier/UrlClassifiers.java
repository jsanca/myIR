package codex.ir.ingestion.crawler.classifier;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Factory for {@link UrlClassifier} implementations.
 */
public final class UrlClassifiers {

    private UrlClassifiers() {
    }

    /**
     * Returns a classifier configured with common WordPress and WooCommerce
     * URL patterns.
     *
     * <p>Classification is deterministic and based only on URL path and
     * query parameters. No HTTP fetching is performed.</p>
     */
    public static UrlClassifier wordpressWooCommerceDefaultWeb() {
        return new DefaultWebClassifier();
    }

    private static final class DefaultWebClassifier implements UrlClassifier {

        private static final List<PathRule> PATH_RULES = List.of(

                // --- transaction/ignored (check first to avoid matching product/category) ---
                new PathRule("^/cart[/]?$", UrlType.CART),
                new PathRule("^/checkout[/]?", UrlType.CHECKOUT),
                new PathRule("^/my-account[/]?", UrlType.ACCOUNT),
                new PathRule("^/account[/]?", UrlType.ACCOUNT),
                new PathRule("^/wp-admin[/]?", UrlType.ADMIN),
                new PathRule("^/wp-login\\.php$", UrlType.ADMIN),
                new PathRule("^/wp-json[/]?", UrlType.IGNORED),
                new PathRule("^/feed[/]?", UrlType.FEED),
                new PathRule("^/search[/]?", UrlType.SEARCH),
                new PathRule("^/order-tracking[/]?", UrlType.IGNORED),
                new PathRule("^/track-order[/]?", UrlType.IGNORED),
                new PathRule("^/wishlist[/]?", UrlType.IGNORED),
                new PathRule("^/compare[/]?", UrlType.IGNORED),

                // --- product detail pages ---
                new PathRule("^/product/", UrlType.PRODUCT),
                new PathRule("^/producto/", UrlType.PRODUCT),
                new PathRule("^/produit/", UrlType.PRODUCT),
                new PathRule("^/produs/", UrlType.PRODUCT),
                new PathRule("^/produkt/", UrlType.PRODUCT),
                new PathRule("^/shop/", UrlType.PRODUCT),
                new PathRule("^/tienda/", UrlType.PRODUCT),
                new PathRule("^/store/", UrlType.PRODUCT),
                new PathRule("^/loja/", UrlType.PRODUCT),
                new PathRule("^/negocio/", UrlType.PRODUCT),

                // --- category / listing pages ---
                new PathRule("^/product-category/", UrlType.CATEGORY),
                new PathRule("^/categoria-producto/", UrlType.CATEGORY),
                new PathRule("^/categorie-produit/", UrlType.CATEGORY),
                new PathRule("^/categoria-produs/", UrlType.CATEGORY),
                new PathRule("^/produkt-kategorie/", UrlType.CATEGORY),
                new PathRule("^/categoria/", UrlType.CATEGORY),

                // --- blog posts ---
                new PathRule("^/blog/\\d{4}/", UrlType.BLOG_POST),
                new PathRule("^/\\d{4}/\\d{2}/", UrlType.BLOG_POST),

                // --- homepage ---
                new PathRule("^/$", UrlType.HOMEPAGE),
                new PathRule("^$", UrlType.HOMEPAGE)
        );

        private static final Pattern QUERY_IGNORE_PATTERN = Pattern.compile(
                "^(add-to-cart|filter_|filter|orderby|min_price|max_price|paged|page|replytocom|"
                        + "utm_source|utm_medium|utm_campaign|utm_content|utm_term|"
                        + "fbclid|gclid|msclkid|ref|v|ver|wc-ajax)"
        );

        private static final Pattern ASSET_EXTENSION = Pattern.compile(
                "\\.(jpg|jpeg|png|gif|webp|svg|ico|bmp|tiff?|css|js|pdf|xml|json|woff2?|ttf|eot|mp4|webm|ogg|zip|tar\\.gz|rss)(\\?.*)?$",
                Pattern.CASE_INSENSITIVE
        );

        @Override
        public ClassifiedUrl classify(final URI uri) {
            Objects.requireNonNull(uri, "uri must not be null");

            final String path = normalizePath(uri);

            if (hasIgnoredQueryParam(uri)) {
                return new ClassifiedUrl(uri, UrlType.IGNORED);
            }

            if (ASSET_EXTENSION.matcher(path).find()) {
                return new ClassifiedUrl(uri, UrlType.ASSET);
            }

            for (final PathRule rule : PATH_RULES) {
                if (rule.matches(path)) {
                    return new ClassifiedUrl(uri, rule.type());
                }
            }

            return new ClassifiedUrl(uri, UrlType.UNKNOWN);
        }

        private static String normalizePath(final URI uri) {
            final String rawPath = uri.getRawPath();
            if (rawPath == null || rawPath.isEmpty()) {
                return "/";
            }
            return rawPath;
        }

        private static boolean hasIgnoredQueryParam(final URI uri) {
            final String query = uri.getRawQuery();
            if (query == null || query.isBlank()) {
                return false;
            }

            return java.util.Arrays.stream(query.split("&"))
                    .map(param -> param.contains("=") ? param.substring(0, param.indexOf('=')) : param)
                    .anyMatch(name -> QUERY_IGNORE_PATTERN.matcher(name).find());
        }

        private record PathRule(Pattern pattern, UrlType type) {

            PathRule(final String regex, final UrlType type) {
                this(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), type);
            }

            boolean matches(final String path) {
                return pattern.matcher(path).find();
            }
        }
    }
}
