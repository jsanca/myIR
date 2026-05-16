package codex.ir.ingestion.crawler.sitemap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Parses XML sitemaps conforming to the sitemaps.org protocol.
 *
 * <p>Supports both {@code <urlset>} (list of URLs) and {@code <sitemapindex>}
 * (index referencing nested sitemaps). The parser is stateless and produces
 * typed result records.</p>
 */
final class SitemapParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(SitemapParser.class);
    private static final DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY = createDocumentBuilderFactory();

    private static DocumentBuilderFactory createDocumentBuilderFactory() {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (final Exception e) {
            LOGGER.debug("Could not configure XML parser security features", e);
        }
        return factory;
    }

    SitemapParser() {
    }

    /**
     * Parses the given XML content and returns typed sitemap entries.
     *
     * @param xmlContent raw XML string from the sitemap response
     * @param sourceUri URI where the sitemap was fetched (for resolving relative URLs)
     * @return parsed sitemap entries, or an empty list if parsing fails
     */
    SitemapEntries parse(final String xmlContent, final URI sourceUri) {
        if (xmlContent == null || xmlContent.isBlank()) {
            return SitemapEntries.EMPTY;
        }

        try {
            final DocumentBuilder builder = DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
            final Document document = builder.parse(new InputSource(new StringReader(xmlContent)));
            final Element rootElement = document.getDocumentElement();
            final String rootTag = rootElement.getTagName();

            if ("urlset".equals(rootTag)) {
                return parseUrlSet(rootElement, sourceUri);
            }
            if ("sitemapindex".equals(rootTag)) {
                return parseSitemapIndex(rootElement, sourceUri);
            }

            LOGGER.debug("Unknown sitemap root element: {}", rootTag);
            return SitemapEntries.EMPTY;
        } catch (final Exception exception) {
            LOGGER.warn("Failed to parse sitemap from {}", sourceUri, exception);
            return SitemapEntries.EMPTY;
        }
    }

    private SitemapEntries parseUrlSet(final Element urlSet, final URI sourceUri) {
        final NodeList urlNodes = urlSet.getElementsByTagName("url");
        final List<SitemapEntry.UrlEntry> entries = new ArrayList<>();

        for (int i = 0; i < urlNodes.getLength(); i++) {
            final Element urlElement = (Element) urlNodes.item(i);
            final Optional<URI> loc = parseLoc(urlElement, sourceUri);

            if (loc.isEmpty()) {
                continue;
            }

            final Optional<Instant> lastMod = parseLastMod(urlElement);
            entries.add(new SitemapEntry.UrlEntry(loc.get(), lastMod));
        }

        LOGGER.debug("Parsed {} URL entries from urlset at {}", entries.size(), sourceUri);
        return new SitemapEntries(entries, Collections.emptyList());
    }

    private SitemapEntries parseSitemapIndex(final Element sitemapIndex, final URI sourceUri) {
        final NodeList sitemapNodes = sitemapIndex.getElementsByTagName("sitemap");
        final List<SitemapEntry.SitemapRef> refs = new ArrayList<>();

        for (int i = 0; i < sitemapNodes.getLength(); i++) {
            final Element sitemapElement = (Element) sitemapNodes.item(i);
            final Optional<URI> loc = parseLoc(sitemapElement, sourceUri);

            if (loc.isEmpty()) {
                continue;
            }

            final Optional<Instant> lastMod = parseLastMod(sitemapElement);
            refs.add(new SitemapEntry.SitemapRef(loc.get(), lastMod));
        }

        LOGGER.debug("Parsed {} sitemap references from sitemapindex at {}", refs.size(), sourceUri);
        return new SitemapEntries(Collections.emptyList(), refs);
    }

    private Optional<URI> parseLoc(final Element parentElement, final URI sourceUri) {
        final NodeList locNodes = parentElement.getElementsByTagName("loc");
        if (locNodes.getLength() == 0) {
            return Optional.empty();
        }

        final String locText = locNodes.item(0).getTextContent();
        if (locText == null || locText.isBlank()) {
            return Optional.empty();
        }

        try {
            final URI resolvedUri = sourceUri.resolve(locText.trim());
            if (resolvedUri.getScheme() == null || resolvedUri.getHost() == null) {
                LOGGER.debug("Ignoring loc with missing scheme or host: {}", locText);
                return Optional.empty();
            }
            return Optional.of(resolvedUri);
        } catch (final Exception exception) {
            LOGGER.debug("Ignoring malformed loc: {}", locText, exception);
            return Optional.empty();
        }
    }

    private Optional<Instant> parseLastMod(final Element parentElement) {
        final NodeList lastModNodes = parentElement.getElementsByTagName("lastmod");
        if (lastModNodes.getLength() == 0) {
            return Optional.empty();
        }

        final String lastModText = lastModNodes.item(0).getTextContent();
        if (lastModText == null || lastModText.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(Instant.from(DateTimeFormatter.ISO_INSTANT.parse(lastModText.trim())));
        } catch (final Exception exception) {
            try {
                return Optional.of(Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(lastModText.trim())));
            } catch (final Exception exception2) {
                LOGGER.debug("Could not parse lastmod: {}", lastModText, exception2);
                return Optional.empty();
            }
        }
    }
}
