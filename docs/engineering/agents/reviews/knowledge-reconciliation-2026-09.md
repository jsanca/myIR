---
type: Engineering Review
title: "myIR Knowledge Reconciliation & Curator Review — September 2026"
description: "Reconciles project knowledge with implementation and test evidence without selecting a new iteration."
tags: [curation, qa, roadmap, core, web, app]
timestamp: 2026-09-04T00:00:00Z
---

# myIR Knowledge Reconciliation & Curator Review — September 2026

## Sources inspected

The review used the [State of the Art assessment](../reports/myir-state-of-the-art-2026-09.md) as its evidence baseline, checked the core and web source/test surfaces, and reviewed CKF, OSK, historical ADRs, roadmap material, `Future-Forward.md`, root documentation, agent guides, engineering indexes, and task histories.

## Reconciliations performed

| Source | Finding | Resolution |
| --- | --- | --- |
| `Future-Forward.md` | Treats fields, field weighting, and crawling as future work | Marked as superseded historical planning; current facts link to CKF and roadmap |
| ADR-004 | Says field postings and lexical boosts are unsupported | Retained decision history; added an explicit partial-supersession note pointing to IR-3/IR-4 |
| CKF ADR-005 | Still proposed and stops at IR-2 | Updated to record completed IR-3/IR-4 while retaining unresolved field-vector/BM25F choices |
| historical core engineering index | CKF transition ends at IR-2 | Extended compatibility links through IR-4 |
| `ROADMAP.md` | Placeholder with no authoritative state | Established completed IR-0–IR-4 and the unselected IR-5/IR-6 sequence |
| web/extraction task history | Many historical task prompts without a canonical concept | Added a capability-and-boundary page rather than task-by-task CKF transcription |

## Current knowledge baseline

The canonical map now distinguishes current architecture, application context, research candidates, and engineering evidence. It preserves `core ← web ← app` as intentional layering. WordPress/WooCommerce specialization is application context, while exported specialized defaults remain a maintenance/API review concern only where concrete evidence supports it. Site-exporter/IR independence is intentional and is not a core architectural gap.

## QA evidence assessment

| Claim | Assessment | Basis and gap |
| --- | --- | --- |
| IR-0 snapshot isolation | Strong evidence | Production snapshots plus `SnapshotSearchTest`; concurrent read/write stress remains untested |
| IR-0.5 batch build | Strong evidence | `BatchIndexerTest` verifies shared-snapshot batch vector weighting |
| IR-1 preprocessing | Strong evidence | Artifact and preprocessing tests verify token/metadata and field aggregation contracts |
| IR-2 provenance | Strong evidence | `FieldAnalyzedDocumentTest` verifies independent normalized field sequences |
| IR-3 postings | Strong evidence | `FieldAwarePostingsTest` verifies frequency capture, isolation, snapshots, and batch propagation |
| IR-4 lexical boosts | Strong evidence | `FieldAwareRankingTest` covers neutral behavior, boosts, raw-content compatibility, TF-IDF and BM25 |
| BM25 | Adequate evidence | Direction and selected formula behavior are tested; reference-value and k1/b sensitivity coverage is absent |
| TF-IDF and sparse cosine | Adequate evidence | Ranker/weighter/vector search behavior is tested; no concurrent live-index consistency test |
| field-aware vectors | Untested capability | Not implemented by design; current vector behavior is aggregated-document only |
| HTTP/dynamic crawling | Weak evidence | Stubs and inline HTML exercise unit behavior; no real HTTP integration test and Playwright is a stub |

## Maintenance backlog, separate from IR research

- Repair and verify `JdkWebHttpFetcher.close()` resource lifecycle.
- Investigate TF-IDF weighting’s live-index versus snapshot consistency during concurrent indexing.
- Implement or explicitly retire the Playwright dynamic fetcher stub.
- Add real HTTP integration coverage and targeted concurrency verification.
- Assess replacement or mitigation for archived `openhtmltopdf-pdfbox`.

These findings are not roadmap selection decisions and do not change the IR research sequence.

## Unresolved questions for planning

1. Should IR-5 or IR-6 be the next selected objective?
2. What judged corpus/query set would make IR-6 useful?
3. Should sparse-vector representations ever preserve field provenance, and what retrieval model warrants it?
4. Which positional capability—phrase, proximity, passage, or snippets—would justify query-time use of stored positions?
5. When would current in-memory scale justify persistence or approximate/vector infrastructure?

## Result

IR-0 through IR-4 are recorded as complete. IR-5 and IR-6 remain existing, unselected roadmap candidates. Broader structural analysis, rank fusion, dense vectors, and persistence remain North Star/research direction rather than scheduled work.
