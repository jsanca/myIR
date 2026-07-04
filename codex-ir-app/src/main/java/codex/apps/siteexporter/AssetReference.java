package codex.apps.siteexporter;

import java.net.URI;

/** An asset URL and its type, extracted from an HTML page. Package-private pipeline type. */
record AssetReference(URI url, AssetType type) {}
