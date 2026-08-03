package pl.grzegorz2047.standalonethewalls.domain.lobby;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Result of applying one command to the deterministic lobby roster. */
public record LobbyRosterDecision(
        LobbyRosterState state,
        List<LobbyRosterEvent> events,
        Optional<LobbyRosterRejection> rejection) {
    public LobbyRosterDecision {
        Objects.requireNonNull(state, "state");
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        Objects.requireNonNull(rejection, "rejection");
        if (rejection.isPresent() && !events.isEmpty()) {
            throw new IllegalArgumentException("rejected lobby command cannot emit events");
        }
    }

    public static LobbyRosterDecision accepted(
            LobbyRosterState state, List<LobbyRosterEvent> events) {
        return new LobbyRosterDecision(state, events, Optional.empty());
    }

    public static LobbyRosterDecision rejected(
            LobbyRosterState unchangedState, LobbyRosterRejection rejection) {
        return new LobbyRosterDecision(
                unchangedState,
                List.of(),
                Optional.of(Objects.requireNonNull(rejection, "rejection")));
    }

    public boolean accepted() {
        return rejection.isEmpty();
    }
}
