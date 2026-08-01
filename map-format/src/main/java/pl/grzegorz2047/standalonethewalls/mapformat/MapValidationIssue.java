package pl.grzegorz2047.standalonethewalls.mapformat;

import java.util.Objects;

/** One bounded semantic validation error with a stable field path and code. */
public record MapValidationIssue(String path, Code code, String message) {
    public MapValidationIssue {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }

    public enum Code {
        REQUIRED,
        FORMAT,
        RANGE,
        UNSUPPORTED,
        CONFLICT,
        UNSAFE_PATH
    }
}
