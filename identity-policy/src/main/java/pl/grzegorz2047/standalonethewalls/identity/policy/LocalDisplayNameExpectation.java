package pl.grzegorz2047.standalonethewalls.identity.policy;

import java.util.Objects;
import java.util.Optional;

/** Explicit optimistic-concurrency precondition for a display-name mutation. */
public record LocalDisplayNameExpectation(Mode mode, Optional<LocalDisplayName> exactValue) {
    public enum Mode {
        ABSENT,
        PRESENT,
        EXACT
    }

    public LocalDisplayNameExpectation {
        mode = Objects.requireNonNull(mode, "mode");
        exactValue = Objects.requireNonNull(exactValue, "exactValue");
        if ((mode == Mode.EXACT) != exactValue.isPresent()) {
            throw new IllegalArgumentException("display name expectation shape is invalid");
        }
    }

    public static LocalDisplayNameExpectation absent() {
        return new LocalDisplayNameExpectation(Mode.ABSENT, Optional.empty());
    }

    public static LocalDisplayNameExpectation present() {
        return new LocalDisplayNameExpectation(Mode.PRESENT, Optional.empty());
    }

    public static LocalDisplayNameExpectation exact(LocalDisplayName value) {
        return new LocalDisplayNameExpectation(
                Mode.EXACT, Optional.of(Objects.requireNonNull(value, "value")));
    }

    public boolean matches(Optional<LocalDisplayName> current) {
        Optional<LocalDisplayName> value = Objects.requireNonNull(current, "current");
        return switch (mode) {
            case ABSENT -> value.isEmpty();
            case PRESENT -> value.isPresent();
            case EXACT -> value.equals(exactValue);
        };
    }
}
