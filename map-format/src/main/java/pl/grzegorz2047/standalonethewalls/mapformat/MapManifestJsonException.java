package pl.grzegorz2047.standalonethewalls.mapformat;

import java.io.Serial;
import java.util.Objects;

/** Bounded parse failure for an untrusted map manifest JSON document. */
public final class MapManifestJsonException extends Exception {
    @Serial private static final long serialVersionUID = 1L;

    private final Code code;

    public MapManifestJsonException(Code code, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.code = Objects.requireNonNull(code, "code");
    }

    public MapManifestJsonException(Code code, String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code code() {
        return code;
    }

    public enum Code {
        INVALID_SIZE,
        MALFORMED_JSON,
        UNKNOWN_FIELD,
        TOO_MANY_FILES
    }
}
