module codex.ir.web {

    requires codex.ir.core;
    requires org.slf4j;
    requires org.jsoup;
    requires java.net.http;
    requires com.fasterxml.jackson.databind;

    exports codex.ir.canonicalizer;
    exports codex.ir.ingestion;
    exports codex.ir.ingestion.crawler;
    exports codex.ir.ingestion.crawler.classifier;
    exports codex.ir.ingestion.crawler.metadata;
    exports codex.ir.ingestion.crawler.filter;
    exports codex.ir.ingestion.crawler.product;

}
