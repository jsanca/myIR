package codex.ir.search;

import codex.ir.Document;

import java.util.List;

/**
 * Rich search result returned by the search layer.
 *
 * A SearchResult represents a ranked result returned by the search layer.
 *
 * It wraps the matched document together with retrieval metadata such as
 * the ranking score assigned by the ranking model and the query terms that
 * matched the document.
 *
 * @param documentId identifier of the matched document
 * @param document matched document resolved from the corpus
 * @param score ranking score assigned to the document for the query
 * @param matchedTerms normalized query terms that matched this document
 */
public record SearchResult(
        String documentId,
        Document document,
        double score,
        List<String> matchedTerms
) {
}
