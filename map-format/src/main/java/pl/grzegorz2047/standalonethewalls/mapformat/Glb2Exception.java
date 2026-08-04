package pl.grzegorz2047.standalonethewalls.mapformat;

import java.io.Serial;
import java.util.Objects;

/** Terminal structural failure while decoding an embedded GLB 2.0 map member. */
public final class Glb2Exception extends Exception {
    @Serial private static final long serialVersionUID = 1L;

    private final Code code;

    public Glb2Exception(Code code, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.code = Objects.requireNonNull(code, "code");
    }

    public Glb2Exception(Code code, String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code code() {
        return code;
    }

    public enum Code {
        INVALID_SIZE,
        INVALID_HEADER,
        INVALID_CHUNK,
        INVALID_JSON,
        INVALID_DOCUMENT,
        EXTERNAL_RESOURCE,
        LIMIT_EXCEEDED
    }
}
