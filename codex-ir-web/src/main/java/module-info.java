module codex.ir.web {

    requires codex.ir.core;
    requires org.slf4j;
    requires org.jsoup;
    requires java.net.http;

    exports codex.ir.canonicalizer;
    exports codex.ir.ingestion;
    exports codex.ir.ingestion.crawler;

}
