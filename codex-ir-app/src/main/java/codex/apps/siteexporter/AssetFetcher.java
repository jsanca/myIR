package codex.apps.siteexporter;

import java.io.IOException;
import java.net.URI;

/**
 * Fetches a single asset by URL. Package-private functional interface that allows
 * a real HTTP client to be replaced with a stub in unit tests.
 */
@FunctionalInterface
interface AssetFetcher {

    Result fetch(URI url) throws IOException;

    record Result(int statusCode, String contentType, byte[] body) {}
}
