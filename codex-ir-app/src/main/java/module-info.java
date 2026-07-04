module codex.ir.app {

    requires codex.ir.core;
    requires codex.ir.web;
    requires java.xml;
    requires com.fasterxml.jackson.databind;
    requires org.jsoup;
    requires java.net.http;

    // Required so Jackson can access private Builder constructors via reflection
    opens codex.apps.siteexporter to com.fasterxml.jackson.databind;

}
