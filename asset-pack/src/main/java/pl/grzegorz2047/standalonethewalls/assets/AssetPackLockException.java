package pl.grzegorz2047.standalonethewalls.assets;

/** Bounded failure raised while parsing or validating an asset lock or manifest. */
public final class AssetPackLockException extends Exception {
    public AssetPackLockException(String message) {
        super(message);
    }

    public AssetPackLockException(String message, Throwable cause) {
        super(message, cause);
    }
}
