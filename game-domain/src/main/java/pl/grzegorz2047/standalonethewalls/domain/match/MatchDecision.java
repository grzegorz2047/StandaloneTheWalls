package pl.grzegorz2047.standalonethewalls.domain.match;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Result of applying one command to one immutable match state. */
public record MatchDecision(
        MatchState state, List<MatchEvent> events, Optional<MatchRejection> rejection) {

    public MatchDecision {
        Objects.requireNonNull(state, "state");
        events = List.copyOf(events);
        Objects.requireNonNull(rejection, "rejection");
        if (rejection.isPresent() && !events.isEmpty()) {
            throw new IllegalArgumentException("rejected decisions cannot emit events");
        }
    }

    public static MatchDecision accepted(MatchState state, MatchEvent... events) {
        return new MatchDecision(state, List.of(events), Optional.empty());
    }

    public static MatchDecision rejected(MatchState unchanged, MatchRejection rejection) {
        return new MatchDecision(unchanged, List.of(), Optional.of(rejection));
    }

    public boolean accepted() {
        return rejection.isEmpty();
    }
}
