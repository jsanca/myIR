package codex.ir.ingestion.crawler;

import codex.ir.ingestion.WebCrawlingConfig;
import codex.ir.ingestion.WebPage;
import codex.ir.ingestion.crawler.fetcher.WebHttpFetcher;
import codex.ir.ingestion.crawler.fetcher.WebHttpFetchers;
import codex.ir.ingestion.crawler.fetcher.WebHttpResponse;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;


public final class WebPageFetchers {

    private static final Logger LOG = LoggerFactory.getLogger(WebPageFetchers.class);

    private WebPageFetchers() {
    }


    public static WebPageFetcher staticHtml(
            final WebCrawlingConfig.HttpClientConfig httpClientConfig
    ) {
        return staticHtml(WebHttpFetchers.jdk(httpClientConfig));
    }

    public static WebPageFetcher staticHtml(final WebHttpFetcher webHttpFetcher) {
        return new JsoupWebPageFetcher(webHttpFetcher);
    }


    public static WebPageFetcher dynamicHtml() {
        return new PlaywrightWebPageFetcher();
    }

    private static class JsoupWebPageFetcher implements WebPageFetcher, AutoCloseable {

        private final WebHttpFetcher webHttpFetcher;

        private JsoupWebPageFetcher(
                final WebHttpFetcher webHttpFetcher
        ) {
            this.webHttpFetcher = Objects.requireNonNull(webHttpFetcher, "webHttpFetcher must not be null");
        }

        @Override
        public Optional<WebPage> fetch(final URI uri, final Consumer<Set<URI>> linkSubscriber) {

            Objects.requireNonNull(uri, "uri must not be null");
            Objects.requireNonNull(linkSubscriber, "linkSubscriber must not be null");

            final WebHttpResponse webHttpResponse = this.webHttpFetcher.fetch(uri);
            if (webHttpResponse == null || !webHttpResponse.isSuccessful() || !webHttpResponse.isHtml()) {
                return Optional.empty();
            }

            final int statusCode = webHttpResponse.statusCode();
            final String contentType = webHttpResponse.contentType();
            final Instant fetchedAt = Instant.now();
            final Map<String, String> headers = toSingleValueHeaders(webHttpResponse.headers());
            final String rawHtml = webHttpResponse.body();
            final Document htmlDocParsed = Jsoup.parse(rawHtml, uri.toString());
            final String title = htmlDocParsed.title();
            final String bodyText = Optional.ofNullable(htmlDocParsed.body()).map(Element::text).orElse("");
            final Set<URI> discoveredLinks = extractLinks(htmlDocParsed, uri);
            linkSubscriber.accept(discoveredLinks);

            return Optional.of(new WebPage(
                    uri,
                    rawHtml,
                    title,
                    bodyText,
                    statusCode,
                    contentType,
                    fetchedAt,
                    headers
            ));
        }

        private Map<String, String> toSingleValueHeaders(final Map<String, List<String>> headers) {
            if (headers == null || headers.isEmpty()) {
                return Map.of();
            }

            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey() != null)
                    .collect(Collectors.toUnmodifiableMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue() == null || entry.getValue().isEmpty()
                                    ? ""
                                    : String.join(", ", entry.getValue()),
                            (left, right) -> right
                    ));
        }

        private Set<URI> extractLinks(final Document htmlDocParsed, final URI baseUri) {
            final Set<URI> discoveredLinks = new LinkedHashSet<>();

            for (final Element link : htmlDocParsed.select("a[href]")) {
                final String absoluteHref = link.absUrl("href");
                if (absoluteHref == null || absoluteHref.isBlank()) {
                    continue;
                }
                if (absoluteHref.startsWith("javascript:")
                        || absoluteHref.startsWith("mailto:")
                        || absoluteHref.startsWith("tel:")) {
                    continue;
                }

                try {
                    discoveredLinks.add(URI.create(absoluteHref).normalize());
                } catch (final IllegalArgumentException ignored) {
                    // Ignore malformed links discovered in the page.
                }
            }

            return Set.copyOf(discoveredLinks);
        }

        @Override
        public void close() throws Exception {

            if (this.webHttpFetcher != null && this.webHttpFetcher instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }

    private static class PlaywrightWebPageFetcher implements WebPageFetcher {
        @Override
        public Optional<WebPage> fetch(final URI uri, final Consumer<Set<URI>> linkSubscriber) {
            return Optional.empty();
        }
    }
}