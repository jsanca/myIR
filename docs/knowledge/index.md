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

## Current Phases

- [IR-2: Field Provenance Artifact](phases/core/ir-2-field-provenance.md) -
  preserve per-field tokens without changing postings or ranking.

## Engineering Logs

- [IR-1: PreprocessedDocument Token Artifact](logs/core/ir-1-preprocessed-document.md)
  - completed preprocessing artifact and validation evidence.
- [IR-3: Field-Aware Postings](logs/core/ir-3-field-aware-postings.md)
  - per-field term frequency in postings; whole-document behavior unchanged.

## Deep Reviews

- [Field Model Indexing Readiness](reviews/core/field-model-indexing-readiness.md)
  - baseline review that informed the field-aware roadmap.

## Outside This Bundle

- `docs/tasks/` contains execution prompts and plans, not durable CKF concepts.
- `AGENTS.md` and `CLAUDE.md` contain agent operating instructions.
- Root and module READMEs remain repository navigation documents.
- Generated reports remain outside CKF.
