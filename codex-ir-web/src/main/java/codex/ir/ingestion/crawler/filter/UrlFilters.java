package codex.ir.ingestion.crawler.filter;

import codex.ir.ingestion.crawler.classifier.UrlType;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Factory and composite helpers for {@link UrlFilter} instances.
 */
public final class UrlFilters {

    private UrlFilters() {
    }

    /** Accepts every classified URL. */
    public static UrlFilter acceptAll() {
        return url -> true;
    }

    /** Rejects every classified URL. */
    public static UrlFilter rejectAll() {
        return url -> false;
    }

    /**
     * Accepts only URLs whose type is one of the given types.
     * Returns {@link #rejectAll()} when no types are specified.
     */
    public static UrlFilter includeTypes(final UrlType... types) {
        if (types.length == 0) {
            return rejectAll();
        }
        final Set<UrlType> allowed = EnumSet.copyOf(Arrays.asList(types));
        return url -> allowed.contains(url.type());
    }

    /**
     * Rejects URLs whose type is one of the given types.
     * Returns {@link #acceptAll()} when no types are specified.
     */
    public static UrlFilter excludeTypes(final UrlType... types) {
        if (types.length == 0) {
            return acceptAll();
        }
        final Set<UrlType> excluded = EnumSet.copyOf(Arrays.asList(types));
        return url -> !excluded.contains(url.type());
    }

    /** Accepts URLs whose path starts with the given prefix. */
    public static UrlFilter pathStartsWith(final String prefix) {
        Objects.requireNonNull(prefix, "prefix must not be null");
        return url -> {
            final String path = url.uri().getRawPath();
            return path != null && path.startsWith(prefix);
        };
    }

    /** Accepts URLs whose path matches the given regex. */
    public static UrlFilter pathMatches(final Pattern pattern) {
        Objects.requireNonNull(pattern, "pattern must not be null");
        return url -> {
            final String path = url.uri().getRawPath();
            return path != null && pattern.matcher(path).find();
        };
    }

    /** Accepts URLs that have a query parameter with the given name (exact match). */
    public static UrlFilter hasQueryParam(final String name) {
        Objects.requireNonNull(name, "name must not be null");
        return url -> {
            final String query = url.uri().getRawQuery();
            if (query == null) {
                return false;
            }
            return Arrays.stream(query.split("&"))
                    .map(p -> p.contains("=") ? p.substring(0, p.indexOf('=')) : p)
                    .anyMatch(name::equals);
        };
    }

    /** Accepts URLs that have a query parameter starting with the given prefix. */
    public static UrlFilter hasQueryParamPrefix(final String prefix) {
        Objects.requireNonNull(prefix, "prefix must not be null");
        return url -> {
            final String query = url.uri().getRawQuery();
            if (query == null) {
                return false;
            }
            return Arrays.stream(query.split("&"))
                    .map(p -> p.contains("=") ? p.substring(0, p.indexOf('=')) : p)
                    .anyMatch(param -> param.startsWith(prefix));
        };
    }

    /** Returns a filter that negates the given filter. */
    public static UrlFilter not(final UrlFilter filter) {
        Objects.requireNonNull(filter, "filter must not be null");
        return url -> !filter.accepts(url);
    }

    /** Returns a filter that requires all given filters to accept. */
    public static UrlFilter allOf(final UrlFilter... filters) {
        Objects.requireNonNull(filters, "filters must not be null");
        for (final UrlFilter filter : filters) {
            Objects.requireNonNull(filter, "each filter must not be null");
        }
        return url -> {
            for (final UrlFilter filter : filters) {
                if (!filter.accepts(url)) {
                    return false;
                }
            }
            return true;
        };
    }

    /** Returns a filter that accepts if any given filter accepts. */
    public static UrlFilter anyOf(final UrlFilter... filters) {
        Objects.requireNonNull(filters, "filters must not be null");
        for (final UrlFilter filter : filters) {
            Objects.requireNonNull(filter, "each filter must not be null");
        }
        return url -> {
            for (final UrlFilter filter : filters) {
                if (filter.accepts(url)) {
                    return true;
                }
            }
            return false;
        };
    }
}
