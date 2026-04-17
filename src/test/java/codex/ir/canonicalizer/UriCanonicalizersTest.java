package codex.ir.canonicalizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class UriCanonicalizersTest {

    @ParameterizedTest(name = "Obvious: {0} -> {1}")
    @MethodSource("provideObviousCases")
    @DisplayName("Default web canonicalizer should handle obvious cases")
    void testDefaultWebCanonicalizerObviousCases(String input, String expected) throws Exception {
        final UriCanonicalizer canonicalizer = UriCanonicalizers.defaultWeb();
        assertEquals(new URI(expected), canonicalizer.canonicalize(new URI(input)));
    }

    private static Stream<Arguments> provideObviousCases() {
        return Stream.of(
                Arguments.of("https://example.com/page#section1", "https://example.com/page"),
                Arguments.of("HTTPS://EXAMPLE.COM/path", "https://example.com/path"),
                Arguments.of("http://example.com:80/index.html", "http://example.com/index.html"),
                Arguments.of("https://example.com:443/index.html", "https://example.com/index.html"),
                Arguments.of("https://example.com/a/b/../c", "https://example.com/a/c")
        );
    }

    @ParameterizedTest(name = "Less obvious: {0} -> {1}")
    @MethodSource("provideComplexCases")
    @DisplayName("Default web canonicalizer should handle complex and edge cases")
    void testDefaultWebCanonicalizerComplexCases(String input, String expected) throws Exception {
        final UriCanonicalizer canonicalizer = UriCanonicalizers.defaultWeb();
        assertEquals(new URI(expected), canonicalizer.canonicalize(new URI(input)));
    }

    private static Stream<Arguments> provideComplexCases() {
        return Stream.of(
                Arguments.of("https://example.com/?z=last&a=first&m=middle", "https://example.com/?a=first&m=middle&z=last"),
                Arguments.of("https://example.com/?b=2&a=1&c=3", "https://example.com/?a=1&b=2&c=3"),
                Arguments.of("https://example.com/path//subpath/./", "https://example.com/path/subpath/"),
                Arguments.of("HTTP://MyHost.com:8080/path", "http://myhost.com:8080/path"),
                Arguments.of("https://example.com/?&a=1&&b=2&", "https://example.com/?a=1&b=2")
        );
    }

    @Test
    @DisplayName("Custom canonicalizer should apply the custom rule at the end of the pipeline")
    void testCustomCanonicalizer() throws Exception {
        final UriCanonicalizer customStep = uri -> {
            final String newPath = uri.getPath() + "/custom";
            try {
                return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(),
                        uri.getPort(), newPath, uri.getQuery(), uri.getFragment());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        final UriCanonicalizer pipeline = UriCanonicalizers.defaultWeb(customStep);

        final String input = "https://example.com/start";
        final String expected = "https://example.com/start/custom";

        assertEquals(new URI(expected), pipeline.canonicalize(new URI(input)));
    }

    @Test
    @DisplayName("Custom canonicalizer should propagate failures from custom steps")
    void testCustomCanonicalizerFailure() {
        final UriCanonicalizer brokenStep = uri -> {
            throw new IllegalArgumentException("Boom!");
        };

        final UriCanonicalizer pipeline = UriCanonicalizers.defaultWeb(brokenStep);

        assertThrows(IllegalArgumentException.class, () -> {
            pipeline.canonicalize(new URI("https://example.com"));
        });
    }
}