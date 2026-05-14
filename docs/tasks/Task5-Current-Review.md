We recently implemented several improvements in myIR around Corpus handling and document fields.

Please perform a fresh architectural and code review of the current myIR project with special focus on:

1. Corpus module
    - Review the current Corpus abstractions and implementations.
    - Check whether corpus statistics, document length, metadata, and refresh/update behavior are still coherent.
    - Identify any race conditions, stale statistics problems, or unclear ownership of responsibilities.
    - Verify whether recent changes introduced unnecessary coupling or duplicated logic.

2. Document and fields model
    - Review the current Document model after the introduction of fields.
    - Check whether canonical fields, custom fields, normalized content, and metadata are modeled clearly.
    - Identify places where the old “single body text” assumption is still leaking.
    - Check if the current design supports future field weighting, searchable/filterable/sortable fields, and per-field indexing.

3. Indexing pipeline
    - Review Indexer, Indexers factory methods, tokenization, normalization, vectorization, and inverted index population.
    - Check whether indexers correctly handle the new field-aware document model.
    - Identify inconsistencies where only body text is indexed but field-aware search is expected.
    - Check if the public API remains clean and implementation details are properly hidden.

4. Search and ranking
    - Review Searcher, Ranker, TF-IDF, BM25, and any binary/vector rankers.
    - Check whether ranking still makes sense after fields were introduced.
    - Identify what should happen next before implementing field weighting.
    - Check if document length and corpus statistics are computed from the correct content source.

5. Tests
    - Identify missing tests caused by the recent Corpus and fields changes.
    - Look for tests that still pass but may be testing outdated assumptions.
    - Recommend high-value tests before further refactoring.

Please produce the review in the following structure:

# myIR Current State Review

## 1. Executive Summary
Briefly explain whether the project is in a healthy state, partially inconsistent state, or needs stabilization.

## 2. What Looks Good
List the parts that are already coherent or improved.

## 3. Main Architectural Risks
List the most important risks, ordered by severity.

## 4. Field Model Alignment
Explain how well the current implementation supports field-aware indexing and search.

## 5. Corpus and Statistics Alignment
Explain whether document statistics, document length, and corpus-level metrics are computed correctly.

## 6. Indexing/Search Pipeline Gaps
Explain any gaps between Document fields, indexing, searching, and ranking.

## 7. Recommended Next Refactor
Suggest the smallest next refactor that improves the architecture without overengineering.

## 8. Recommended Tests
List concrete tests to add or update.

## 9. Do Not Change Yet
Mention things that should not be refactored yet to avoid unnecessary scope.

Important:
- Do not implement code yet.
- Do not perform broad rewrites.
- Prefer small, incremental recommendations.
- Keep the existing didactic/academic nature of myIR.
- The project is intentionally exploring IR concepts from scratch in Java, so avoid replacing the design with external libraries.
- Comments and code comments should remain in English.