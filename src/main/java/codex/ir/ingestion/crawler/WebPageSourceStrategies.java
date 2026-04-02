package codex.ir.ingestion.crawler;

import codex.ir.ingestion.WebPage;

import java.util.function.Consumer;

public class WebPageSourceStrategies {

    private static class SiteTraversalStrategy implements WebPageSourceStrategy  {

        @Override
        public void readInto(final Consumer<WebPage> consumer) {

        }
    }

}
