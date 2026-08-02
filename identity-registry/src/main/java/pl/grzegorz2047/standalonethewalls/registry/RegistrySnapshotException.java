package pl.grzegorz2047.standalonethewalls.registry;

/** Bounded semantic failure while verifying or activating a registry snapshot. */
public final class RegistrySnapshotException extends Exception {
    private static final long serialVersionUID = 1L;

    private final Code code;

    public RegistrySnapshotException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public RegistrySnapshotException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        INVALID_ARTIFACT_SIZE,
        INVALID_DIGEST_LENGTH,
        INVALID_SIGNATURE_LENGTH,
        NON_CANONICAL_JSON,
        MALFORMED_JSON,
        UNSUPPORTED_SCHEMA,
        UNKNOWN_FIELD,
        MISSING_FIELD,
        INVALID_SEQUENCE,
        INVALID_TIMESTAMP,
        UNKNOWN_ROOT_KEY,
        INVALID_PUBLIC_KEY,
        DIGEST_MISMATCH,
        INVALID_SIGNATURE,
        TOO_MANY_ENTRIES,
        INVALID_ENTRY,
        UNSORTED_ENTRIES,
        DUPLICATE_HANDLE,
        PLAYER_ID_MISMATCH,
        BELOW_MINIMUM_SEQUENCE,
        SNAPSHOT_TOO_OLD,
        SNAPSHOT_FROM_FUTURE,
        ROLLBACK,
        EQUIVOCATION,
        CRYPTOGRAPHY_FAILURE
    }
}
