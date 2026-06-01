package codex;

/**
 * Hardcoded-entry-point: runs {@link DiscoveryRunner} with parameters
 * baked into the code so you can launch from the IDE without editing
 * run configurations.
 *
 * <p>Edit {@link #ARGS} to change sites, limits, output mode, etc.</p>
 */
public final class QuickDiscoveryRunner {

    private static final String[] ARGS = {
            "--sitemap", "https://syjleathers.com/product-sitemap.xml",
            "--sitemap", "https://syjleathers.com/product_cat-sitemap.xml",
            "--limit", "50",
            "--output", "both",
            "--out-dir", "./reports"
    };

    public static void main(final String[] args) {
        DiscoveryRunner.main(ARGS);
    }
}
