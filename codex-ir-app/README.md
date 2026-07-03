# codex-ir-app

Application entry point for myIR. Contains demo runners, product discovery workflows, and sitemap extraction tools. Depends on both `codex-ir-core` and `codex-ir-web`.

## JPMS Module

`codex.ir.app` — requires `codex.ir.core`, `codex.ir.web`, and `java.xml`. No exports (entry point only).

## Entry Points

| Class | Description |
|-------|-------------|
| `codex.Main` | Primary entry point. Runs demos for in-memory indexing, web crawling with lexical search, and web crawling with vector search. ~300 lines. |
| `codex.DiscoveryRunner` | Dev/validation runner that fetches pages from explicit URLs or sitemaps, runs product discovery, and writes reports to console or JSON files. |
| `codex.QuickDiscoveryRunner` | Convenience wrapper that invokes `DiscoveryRunner` with baked-in arguments for IDE launch. |
| `codex.SitemapUrlExtractor` | Extracts page URLs from XML sitemaps (supports `<urlset>` and one level of `<sitemapindex>`), filtered to HTTP/HTTPS same-host URLs. |
| `codex.OutputMode` | Enum: `CONSOLE`, `JSON`, `BOTH` — controls discovery report output destination. |

## Running

```shell
# Main demo
mvn compile -pl codex-ir-app && mvn exec:java -pl codex-ir-app -Dexec.mainClass="codex.Main"

# Quick discovery runner
mvn exec:java -pl codex-ir-app -Dexec.mainClass="codex.QuickDiscoveryRunner"
```

## Common Commands

```shell
# Build (includes transitive dependencies on core + web)
mvn compile -pl codex-ir-app

# Tests
mvn test -pl codex-ir-app
```
