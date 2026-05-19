package codex.ir.ingestion.crawler.metadata;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageMetadataTest {

    @Test
    void emptyShouldHaveAllAbsentFields() {
        final PageMetadata meta = PageMetadata.empty();

        assertTrue(meta.title().isEmpty());
        assertTrue(meta.description().isEmpty());
        assertTrue(meta.canonicalUrl().isEmpty());
        assertTrue(meta.openGraph().isEmpty());
        assertTrue(meta.twitterCard().isEmpty());
        assertTrue(meta.robotsDirectives().isEmpty());
        assertTrue(meta.h1Headings().isEmpty());
        assertTrue(meta.h2Headings().isEmpty());
        assertTrue(meta.language().isEmpty());
        assertTrue(meta.jsonLdBlocks().isEmpty());
    }

    @Test
    void builderShouldProduceCorrectValues() {
        final PageMetadata meta = PageMetadata.builder()
                .title("My Title")
                .description("My Desc")
                .canonicalUrl("https://example.com/")
                .language("en")
                .openGraph(Map.of("og:type", "article"))
                .twitterCard(Map.of("twitter:card", "summary"))
                .robotsDirectives(List.of("noindex"))
                .h1Headings(List.of("Main"))
                .h2Headings(List.of("Sub"))
                .jsonLdBlocks(List.of("{\"@type\":\"WebPage\"}"))
                .build();

        assertEquals("My Title", meta.title().orElse(null));
        assertEquals("My Desc", meta.description().orElse(null));
        assertEquals("https://example.com/", meta.canonicalUrl().orElse(null));
        assertEquals("en", meta.language().orElse(null));
        assertEquals("article", meta.openGraph().get("og:type"));
        assertEquals("summary", meta.twitterCard().get("twitter:card"));
        assertEquals(List.of("noindex"), meta.robotsDirectives());
        assertEquals(List.of("Main"), meta.h1Headings());
        assertEquals(List.of("Sub"), meta.h2Headings());
        assertEquals(1, meta.jsonLdBlocks().size());
    }

    @Test
    void builderWithNoOptionalFieldsShouldProduceEmptyOptionals() {
        final PageMetadata meta = PageMetadata.builder().build();

        assertTrue(meta.title().isEmpty());
        assertTrue(meta.language().isEmpty());
    }

    @Test
    void toBuilderShouldProduceEqualInstance() {
        final PageMetadata original = PageMetadata.builder()
                .title("Original")
                .language("en")
                .h1Headings(List.of("Heading"))
                .build();

        final PageMetadata copy = original.toBuilder().build();

        assertEquals(original, copy);
    }

    @Test
    void toBuilderShouldAllowOverridingField() {
        final PageMetadata original = PageMetadata.builder()
                .title("Old Title")
                .language("en")
                .build();

        final PageMetadata modified = original.toBuilder()
                .title("New Title")
                .build();

        assertEquals("New Title", modified.title().orElse(null));
        assertEquals("en", modified.language().orElse(null));
        assertEquals("Old Title", original.title().orElse(null));
    }

    @Test
    void toBuilderShouldNotMutateOriginal() {
        final PageMetadata original = PageMetadata.builder()
                .title("Stable")
                .build();

        original.toBuilder().title("Changed").build();

        assertEquals("Stable", original.title().orElse(null));
    }

    @Test
    void compactConstructorShouldDefensivelyCopyMutableMap() {
        final Map<String, String> mutable = new HashMap<>();
        mutable.put("og:title", "Before");
        final PageMetadata meta = PageMetadata.builder().openGraph(mutable).build();

        mutable.put("og:title", "After");

        assertEquals("Before", meta.openGraph().get("og:title"));
    }

    @Test
    void compactConstructorShouldDefensivelyCopyMutableList() {
        final List<String> mutable = new ArrayList<>();
        mutable.add("noindex");
        final PageMetadata meta = PageMetadata.builder().robotsDirectives(mutable).build();

        mutable.add("nofollow");

        assertEquals(1, meta.robotsDirectives().size());
    }

    @Test
    void openGraphShouldBeImmutable() {
        final PageMetadata meta = PageMetadata.builder()
                .openGraph(Map.of("og:type", "article"))
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> meta.openGraph().put("og:extra", "value"));
    }

    @Test
    void listFieldShouldBeImmutable() {
        final PageMetadata meta = PageMetadata.builder()
                .h1Headings(List.of("Heading"))
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> meta.h1Headings().add("Extra"));
    }

    @Test
    void compactConstructorShouldRejectNullTitle() {
        assertThrows(NullPointerException.class, () ->
                new PageMetadata(null, java.util.Optional.empty(), java.util.Optional.empty(),
                        Map.of(), Map.of(), List.of(), List.of(), List.of(),
                        java.util.Optional.empty(), List.of()));
    }

    @Test
    void compactConstructorShouldRejectNullOpenGraph() {
        assertThrows(NullPointerException.class, () ->
                new PageMetadata(java.util.Optional.empty(), java.util.Optional.empty(),
                        java.util.Optional.empty(), null, Map.of(), List.of(), List.of(),
                        List.of(), java.util.Optional.empty(), List.of()));
    }

    @Test
    void twoBuiltInstancesWithSameValuesShouldBeEqual() {
        final PageMetadata a = PageMetadata.builder().title("X").build();
        final PageMetadata b = PageMetadata.builder().title("X").build();

        assertEquals(a, b);
        assertNotSame(a, b);
    }
}
