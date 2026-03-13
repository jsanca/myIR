package codex.ir;

import java.util.List;

/**
 * Rich search result returned by the search layer.
 *
 * A SearchResult wraps the matched document together with additional
 * retrieval metadata that can later be used for ranking, explanations,
 * snippets, or debugging.
 *
 * @param documentId identifier of the matched document
 * @param document matched document resolved from the corpus
 * @param matchedTerms normalized query terms that matched this document
 */
public record SearchResult(
        String documentId,
        Document document,
        List<String> matchedTerms
) {
}
