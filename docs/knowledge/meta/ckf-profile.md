---
type: CKF Profile
title: Codex Knowledge Format Profile
description: Minimal OKF-compatible documentation profile for myIR engineering knowledge.
tags: [documentation, knowledge, okf, ckf]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: active
ckf_scope: repository
ckf_owner: project
---

# Purpose

CKF is a producer profile built on the [Open Knowledge Format v0.1](https://github.com/GoogleCloudPlatform/knowledge-catalog/blob/main/okf/SPEC.md).
It adds a small lifecycle vocabulary for myIR while preserving ordinary OKF
Markdown, frontmatter, paths, and links.

# Concept Types

- `Architecture Decision`
- `Delivery Phase`
- `Engineering Log Entry`
- `Deep Review`
- `System Concept`
- `Runbook`
- `Reference`

# Frontmatter

Every concept requires the OKF `type` field. CKF concepts should also provide
`title`, `description`, `tags`, and the following extension fields when useful:

| Field | Meaning |
|---|---|
| `ckf_version` | CKF profile version used by the producer |
| `ckf_status` | `proposed`, `accepted`, `active`, `completed`, `superseded`, or `deprecated` |
| `ckf_scope` | Owning project area such as `core`, `web`, or `site-exporter` |
| `ckf_owner` | Steward responsible for maintaining the concept |

# Documentation Discipline

1. Treat each CKF concept as the canonical document for its subject.
2. Move or replace prior canonical content with a link; do not maintain copies.
3. Connect decisions, phases, reviews, and logs with ordinary Markdown links.
4. Create or update an Engineering Log Entry when a phase completes.
5. Keep task prompts, agent instructions, generated output, and transient notes
   outside the bundle.
6. Keep the root [knowledge index](../index.md) concise and navigable.
7. Add or update `package-info.java` whenever a code change introduces or
   materially changes a package-level concept.

# Relationship Vocabulary

OKF links are intentionally untyped. CKF expresses relationship meaning in the
surrounding prose using stable labels such as `Implements`, `Validated by`,
`Supersedes`, `Depends on`, and `Related`.

# Conformance

- Concept files are UTF-8 Markdown with parseable YAML frontmatter.
- Every concept has a non-empty `type`.
- The root `index.md` declares `okf_version` and `ckf_version`.
- Local links resolve within the repository or bundle.
- Unknown frontmatter fields remain tolerated.
