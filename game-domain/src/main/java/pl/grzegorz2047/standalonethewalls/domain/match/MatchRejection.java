package pl.grzegorz2047.standalonethewalls.domain.match;

import java.util.Objects;

/** Bounded, semantic rejection suitable for mapping to logs or protocol codes. */
public record MatchRejection(Code code, String detail) {
    public MatchRejection {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(detail, "detail");
    }

    public enum Code {
        INVALID_PHASE,
        INVALID_PLAYER_COUNT,
        INVALID_RESULT
    }
}
