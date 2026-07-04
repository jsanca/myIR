package codex.apps.siteexporter;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiscoveredOrderPublicationOrderingStrategyTest {

    private static final URI SEED = URI.create("https://example.com/");
    private static final Instant FETCHED_AT = Instant.parse("2026-07-03T12:00:00Z");

    @Test
    void orderShouldSortByDiscoveredOrderAscending() {
        final MirrorManifest manifest = manifestWithPages(
                successPage("id-3", "https://example.com/c", "c.html", 2L),
                successPage("id-1", "https://example.com/a", "a.html", 0L),
                successPage("id-2", "https://example.com/b", "b.html", 1L));

        final List<MirroredPage> ordered =
                new DiscoveredOrderPublicationOrderingStrategy().order(manifest);

        assertEquals(3, ordered.size());
        assertEquals(0L, ordered.get(0).discoveredOrder());
        assertEquals(1L, ordered.get(1).discoveredOrder());
        assertEquals(2L, ordered.get(2).discoveredOrder());
    }

    @Test
    void orderShouldExcludeWriteFailedPages() {
        final MirroredPage failed = MirroredPage.builder()
                .id("id-fail").url(URI.create("https://example.com/bad"))
                .canonicalUrl(URI.create("https://example.com/bad"))
                .depth(0).discoveredOrder(0L).status(200).fetchedAt(FETCHED_AT)
                .mirrorStatus(MirrorStatus.WRITE_FAILED).errorMessage("disk full").build();

        final MirrorManifest manifest = manifestWithPages(
                successPage("id-1", "https://example.com/a", "a.html", 1L), failed);

        final List<MirroredPage> ordered =
                new DiscoveredOrderPublicationOrderingStrategy().order(manifest);

        assertEquals(1, ordered.size());
        assertEquals("id-1", ordered.get(0).id());
    }

    @Test
    void orderShouldExcludeFetchFailedPages() {
        final MirroredPage fetchFailed = MirroredPage.builder()
                .id("id-ff").url(URI.create("https://example.com/missing"))
                .canonicalUrl(URI.create("https://example.com/missing"))
                .depth(0).discoveredOrder(0L).status(404).fetchedAt(FETCHED_AT)
                .mirrorStatus(MirrorStatus.FETCH_FAILED).build();

        final MirrorManifest manifest = manifestWithPages(
                successPage("id-1", "https://example.com/a", "a.html", 1L), fetchFailed);

        final List<MirroredPage> ordered =
                new DiscoveredOrderPublicationOrderingStrategy().order(manifest);

        assertEquals(1, ordered.size());
        assertEquals("id-1", ordered.get(0).id());
    }

    @Test
    void orderShouldExcludePagesWithNullLocalHtmlPath() {
        final MirroredPage noPath = MirroredPage.builder()
                .id("id-np").url(URI.create("https://example.com/np"))
                .canonicalUrl(URI.create("https://example.com/np"))
                .depth(0).discoveredOrder(0L).status(200).fetchedAt(FETCHED_AT)
                .mirrorStatus(MirrorStatus.SUCCESS).build();

        final MirrorManifest manifest = manifestWithPages(
                successPage("id-1", "https://example.com/a", "a.html", 1L), noPath);

        final List<MirroredPage> ordered =
                new DiscoveredOrderPublicationOrderingStrategy().order(manifest);

        assertEquals(1, ordered.size());
        assertEquals("id-1", ordered.get(0).id());
    }

    @Test
    void orderShouldReturnEmptyListForEmptyManifest() {
        final MirrorManifest manifest = MirrorManifest.builder()
                .startUrl(SEED).sameDomainOnly(true).maxPages(10).maxDepth(2)
                .pages(List.of()).build();

        final List<MirroredPage> ordered =
                new DiscoveredOrderPublicationOrderingStrategy().order(manifest);

        assertNotNull(ordered);
        assertTrue(ordered.isEmpty());
    }

    @Test
    void orderShouldReturnEmptyListWhenAllPagesAreExcluded() {
        final MirroredPage failed = MirroredPage.builder()
                .id("id-fail").url(URI.create("https://example.com/bad"))
                .canonicalUrl(URI.create("https://example.com/bad"))
                .depth(0).discoveredOrder(0L).status(500).fetchedAt(FETCHED_AT)
                .mirrorStatus(MirrorStatus.FETCH_FAILED).build();

        final MirrorManifest manifest = manifestWithPages(failed);

        final List<MirroredPage> ordered =
                new DiscoveredOrderPublicationOrderingStrategy().order(manifest);

        assertTrue(ordered.isEmpty());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static MirrorManifest manifestWithPages(final MirroredPage... pages) {
        return MirrorManifest.builder()
                .startUrl(SEED).sameDomainOnly(true).maxPages(10).maxDepth(2)
                .pages(List.of(pages)).build();
    }

    private static MirroredPage successPage(final String id, final String url,
            final String localHtmlPath, final long discoveredOrder) {
        return MirroredPage.builder()
                .id(id).url(URI.create(url)).canonicalUrl(URI.create(url))
                .localHtmlPath(localHtmlPath).depth(0).discoveredOrder(discoveredOrder)
                .status(200).fetchedAt(FETCHED_AT).mirrorStatus(MirrorStatus.SUCCESS).build();
    }
}
