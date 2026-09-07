# Web Extraction and Application Context

`codex-ir-web` provides reusable acquisition capabilities: HTTP and HTML fetching, BFS and sitemap traversal, URI canonicalization, URL/page classification, page metadata extraction, product extraction, and mapping of web pages to core `Document` values. Its behavior is supported by the existing web test suite and source implementation; historical implementation prompts in `docs/tasks/apps/information-extraction/` are retained as task history, not canonical knowledge.

WordPress/WooCommerce classifiers, sitemap conventions, and extractors are an intentional specialization for the product-discovery application. This is not evidence that the core retrieval engine should adopt web or commerce concepts. The appropriate maintenance question is whether any exported API presents a specialized default as generic—not whether the specialization should be moved or redesigned automatically.

The site exporter is a separate application pipeline that crawls and publishes PDF, Markdown, and EPUB artifacts. It does not need to index output or connect to the IR engine merely because both reside in this repository.

## Curated historical representation

The substantial task history is represented here as capabilities and boundaries. Detailed execution evidence remains in the task files and the [site-exporter engineering logs](../index.md#site-exporter). Future work should add durable concepts or decisions only when it establishes a reusable current fact; it should not transcribe every historical task into CKF.

## Evidence

- [State of the Art assessment](../../engineering/agents/reports/myir-state-of-the-art-2026-09.md#6-web--extraction-surface)
- `codex-ir-web/src/main/java/codex/ir/`
- `docs/tasks/apps/information-extraction/` (historical task evidence)
