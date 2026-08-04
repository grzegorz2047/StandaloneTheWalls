package pl.grzegorz2047.standalonethewalls.mapformat;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Terminal fail-closed error while loading and verifying one `.twmap` archive. */
public final class TwMapBundleException extends Exception {
    @Serial private static final long serialVersionUID = 1L;

    private final Code code;
    private final ArrayList<MapValidationIssue> manifestIssues;

    public TwMapBundleException(Code code, String message) {
        this(code, message, null, List.of());
    }

    public TwMapBundleException(Code code, String message, Throwable cause) {
        this(code, message, cause, List.of());
    }

    public TwMapBundleException(
            Code code, String message, List<MapValidationIssue> manifestIssues) {
        this(code, message, null, manifestIssues);
    }

    private TwMapBundleException(
            Code code,
            String message,
            Throwable cause,
            List<MapValidationIssue> manifestIssues) {
        super(Objects.requireNonNull(message, "message"), cause);
        this.code = Objects.requireNonNull(code, "code");
        this.manifestIssues =
                new ArrayList<>(Objects.requireNonNull(manifestIssues, "manifestIssues"));
    }

    public Code code() {
        return code;
    }

    public List<MapValidationIssue> manifestIssues() {
        return List.copyOf(manifestIssues);
    }

    public enum Code {
        INVALID_ARCHIVE_SIZE,
        MALFORMED_ARCHIVE,
        UNSAFE_ENTRY,
        DUPLICATE_ENTRY,
        TOO_MANY_ENTRIES,
        MISSING_MANIFEST,
        INVALID_MANIFEST_JSON,
        INVALID_MANIFEST,
        UNDECLARED_ENTRY,
        MISSING_ENTRY,
        ENTRY_SIZE_LIMIT,
        EXPANSION_LIMIT,
        HASH_MISMATCH,
        INVALID_GAMEPLAY
    }
}
