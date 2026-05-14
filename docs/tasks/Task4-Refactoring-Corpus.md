Implement Task 4: Version CorpusStatistics snapshots.

Goal:
Make CorpusStatistics expose a monotonically increasing version so rankers can later invalidate IDF caches when corpus statistics change.

Scope:
1. Add a long version field to CorpusStatistics.
2. Add a static empty() factory returning version 0.
3. Add a static snapshot(long version, int documentCount, long totalDocumentLength, int documentsWithLength) factory that computes averageDocumentLength safely.
4. Update InMemoryCorpus so each statistics snapshot refresh increments the version.
5. Keep Corpus.statistics() lock-free by reading from the existing AtomicReference<CorpusStatistics>.
6. Do not refactor the synchronization model yet.
7. Do not introduce LongAdder yet.
8. Do not modify rankers yet, except where compilation requires adapting to the new CorpusStatistics shape.

Testing:
- Add or update tests proving that empty statistics starts at version 0.
- Adding a document increments the statistics version.
- Replacing an existing document increments the statistics version.
- documentCount remains correct when replacing a document.
- averageDocumentLength is computed using documentsWithLength and avoids division by zero.

Important:
This task is only about versioning CorpusStatistics. Do not clean up AtomicReference counters or synchronized blocks yet; that will be Task 2.
Do not change the ingestion/concurrency model in this task.

Expected result:
CorpusStatistics should expose:

- version()
- documentCount()
- totalDocumentLength()
- documentsWithLength()
- averageDocumentLength()

This prepares the ranking layer for a later task where TF-IDF and BM25 IDF caches will become version-aware or invalidated when CorpusStatistics.version() changes.