package codex.ir.concurrent;

/**
 * Encapsulates the app configuration
 * @param maxConcurrentDownloads
 * @author jsanca & elo
 */
public record VTConfig(
        int maxConcurrentDownloads
) {
    public static VTConfig defaultConfig() {
        return new VTConfig(4);
    }
}
