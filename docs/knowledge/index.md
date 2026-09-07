---
okf_version: "0.1"
ckf_version: "0.1"
---

# myIR Knowledge Index

This directory is the pilot Codex Knowledge Format (CKF) bundle for durable
project knowledge. The repository [README](../../README.md) remains the human
entry point; this index is the progressive-disclosure entry point for agents.

## Profile

- [CKF Profile](meta/ckf-profile.md) - bundle rules, types, lifecycle metadata,
  and documentation discipline.

## Architecture Decisions

- [ADR-005: Future Field-Aware Indexing](decisions/adr-005-field-aware-indexing.md)
  - options and incremental boundaries for field-aware retrieval.

## Current System Knowledge

- [Current System and Architectural Boundaries](architecture/current-system.md)
  - current retrieval state, intentional module/application boundaries, and constraints.
- [Web Extraction and Application Context](web/extraction-and-applications.md)
  - reusable web capabilities and intentional WordPress/WooCommerce specialization.
- [Retrieval Research Candidate Map](research/candidate-capabilities.md)
  - roadmap sequence, research candidates, and unresolved field-vector question.

## Delivery Phases

- [IR-2: Field Provenance Artifact](phases/core/ir-2-field-provenance.md) -
  completed preservation of per-field tokens without changing postings or ranking.

## Engineering Logs

### Core

- [IR-0: Read/Write Boundary Cleanup](logs/core/ir-0-read-write-boundary.md)
  - immutable corpus and index snapshots for read-path consistency.
- [IR-0.5: Batch Index Build Pipeline](logs/core/ir-0-5-batch-index-build.md)
  - shared-snapshot batch indexing and vectorization.
- [IR-1: PreprocessedDocument Token Artifact](logs/core/ir-1-preprocessed-document.md)
  - completed preprocessing artifact and validation evidence.
- [IR-2: Field Provenance Artifact](logs/core/ir-2-field-provenance.md)
  - preserved per-field normalized token sequences.
- [IR-3: Field-Aware Postings](logs/core/ir-3-field-aware-postings.md)
  - per-field term frequency in postings; whole-document behavior unchanged.
- [IR-4: Field-Aware Ranking / Boosting](logs/core/ir-4-field-aware-ranking.md)
  - RankingContext and FieldWeights apply frequency-weighted field boosts.

### Site Exporter

- [Phase 3: Site Exporter Skeleton](logs/site-exporter/phase-3-site-exporter-skeleton.md)
- [Phase 4: Mirror HTML Vertical Slice](logs/site-exporter/phase-4-mirror-html.md)
- [Phase 5: Manifest Metadata and Hygiene](logs/site-exporter/phase-5-manifest-hygiene.md)
- [Phase 5 Fix: Jackson Manifest Reader](logs/site-exporter/phase-5-fix-jackson-manifest-reader.md)
- [Phase 6A: Asset Discovery and Download](logs/site-exporter/phase-6a-asset-discovery-download.md)
- [Phase 6B: HTML Link Rewriting](logs/site-exporter/phase-6b-html-link-rewriting.md)
- [Phase 7: Publication Pipeline](logs/site-exporter/phase-7-publication-pipeline.md)
- [Phase 7.5: API Hygiene](logs/site-exporter/phase-7-5-api-hygiene.md)
- [Phase 8: PDF Renderer Port](logs/site-exporter/phase-8-pdf-renderer-port.md)
- [Phase 9: CLI Wiring and PDF Assembly](logs/site-exporter/phase-9-cli-wiring-pdf-assembly.md)
- [Phase 9.5: Publication Ordering](logs/site-exporter/phase-9-5-publication-ordering.md)
- [Phase 10: PDF Assembly](logs/site-exporter/phase-10-pdf-assembly.md)
- [Phase 10.5: HTML Sanitization](logs/site-exporter/phase-10-5-html-sanitization.md)
- [Phase 10.6: Print-Friendly HTML](logs/site-exporter/phase-10-6-print-friendly-html-sanitization.md)
- [Phase 10.7: pdf2htmlEX Reader Extraction](logs/site-exporter/phase-10-7-pdf2htmlex-reader-extraction.md)
- [Phase 10.8: Reader Route](logs/site-exporter/phase-10-8-pdf2htmlex-reader-route.md)
- [Phase 10.8: Resume Existing Mirror](logs/site-exporter/phase-10-8-resume-existing-mirror.md)
- [Phase 10.9: Markdown Publication](logs/site-exporter/phase-10-9-markdown-publication-writer.md)
- [Phase 10.10: Publication Drivers](logs/site-exporter/phase-10-10-publication-driver-refactor.md)
- [Phase 11: EPUB Publication](logs/site-exporter/phase-11-epub-publication-driver.md)

## Deep Reviews

- [Field Model Indexing Readiness](reviews/core/field-model-indexing-readiness.md)
  - baseline review that informed the field-aware roadmap.

## Outside This Bundle

- `docs/tasks/` contains execution prompts and plans, not durable CKF concepts.
- `AGENTS.md` and `CLAUDE.md` contain agent operating instructions.
- Root and module READMEs remain repository navigation documents.
- Generated reports remain outside CKF.
