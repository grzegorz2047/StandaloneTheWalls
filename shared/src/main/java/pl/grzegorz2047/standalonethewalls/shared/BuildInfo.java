package pl.grzegorz2047.standalonethewalls.shared;

/** Shared build metadata that is safe to use in every module. */
public final class BuildInfo {
    public static final String PRODUCT_NAME = "Sunderfront";
    public static final String VERSION = "0.1.0-alpha.3";
    public static final String RELEASE_TAG = "v" + VERSION;

    private BuildInfo() {
        throw new AssertionError("No instances");
    }
}
