package codex.apps.siteexporter;

/** Outcome of attempting to mirror a single page. */
public enum MirrorStatus {
    /** Page was fetched and written to disk successfully. */
    SUCCESS,
    /** Page fetch failed before any content was received. */
    FETCH_FAILED,
    /** Page was fetched but could not be written to disk. */
    WRITE_FAILED,
    /** Page was excluded from mirroring (e.g. non-HTML, out-of-domain). */
    SKIPPED
}
