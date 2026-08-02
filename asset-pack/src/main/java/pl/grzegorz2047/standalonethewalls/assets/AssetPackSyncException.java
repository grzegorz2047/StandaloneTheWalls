package pl.grzegorz2047.standalonethewalls.assets;

import java.util.Objects;

/** Bounded synchronization failure that is safe to branch on without parsing messages. */
public final class AssetPackSyncException extends Exception {
    private static final long serialVersionUID = 1L;

    private final Code code;

    public AssetPackSyncException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public AssetPackSyncException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code code() {
        return code;
    }

    public enum Code {
        PROVIDER_FAILED,
        ARCHIVE_TRUNCATED,
        ARCHIVE_OVERSIZED,
        ARCHIVE_HASH_MISMATCH,
        ARCHIVE_INVALID,
        MANIFEST_INVALID,
        CACHE_MISSING,
        CACHE_STALE,
        CACHE_CONFLICT,
        CACHE_IO
    }
}
