package pl.grzegorz2047.standalonethewalls.client.ui.pointer;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable renderer-independent identifier for one interactive UI target. */
public record UiTargetId(String value) {
    private static final int MAXIMUM_LENGTH = 64;
    private static final Pattern CANONICAL = Pattern.compile("[a-z0-9][a-z0-9._-]*");

    public UiTargetId {
        Objects.requireNonNull(value, "value");
        if (value.length() > MAXIMUM_LENGTH || !CANONICAL.matcher(value).matches()) {
            throw new IllegalArgumentException("UI target id is not canonical");
        }
    }
}
