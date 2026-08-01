package pl.grzegorz2047.standalonethewalls.shared;

/** Shared build metadata that is safe to use in every module. */
public final class BuildInfo {
    public static final String PRODUCT_NAME = "Sunderfront";

    private BuildInfo() {
        throw new AssertionError("No instances");
    }
}
