package codex.ir.ingestion;

import java.net.URI;

import codex.ir.Document;
import java.util.Objects;

public final class Mappers {


    private Mappers() {
    }

    public static DocumentMapper<WebPage> webPage() {
        return new WebPageDocumentMapper();
    }

    private static class WebPageDocumentMapper implements DocumentMapper<WebPage> {

        @Override
        public Document map(final WebPage input) {

            Objects.requireNonNull(input, "webpage must not be null");
            Objects.requireNonNull(input.url(), "url must not be null");
            final URI normalizedUrl = input.url().normalize();
            final String docId = normalizedUrl.toString();
            final String title = input.title() == null ? "" : input.title();
            final String bodyText = input.bodyText() == null ? "" : input.bodyText();
            final String rawContent = title.isBlank() ? bodyText : title + System.lineSeparator() + bodyText;

            return Document.builder()
                    .id(docId)
                    .rawContent(rawContent)
                    .field("title", title)
                    .field("body", bodyText)
                    .title(title)
                    .attribute("rawHtml", input.rawHtml())
                    .attribute("contentType", input.contentType())
                    .attribute("fetchedAt", input.fetchedAt())
                    .attribute("headers", input.headers())
                    .attribute("statusCode", input.statusCode())
                    .attribute("url", normalizedUrl)
                    .build();
        }
    }
}
