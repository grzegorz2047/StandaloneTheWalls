package pl.grzegorz2047.standalonethewalls.server.lobby;

import java.util.Objects;
import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyConfiguration;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyRosterState;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchCommand;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchConfiguration;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchDecision;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchEvent;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchEvent.CountdownCancellationReason;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchLifecycle;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchPhase;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchState;

/**
 * Single-owner adapter joining authoritative lobby readiness with the deterministic match
 * lifecycle.
 *
 * <p>The caller must serialize roster updates and simulation ticks. The coordinator performs no
 * I/O, owns no thread, and rejects gaps instead of silently guessing missing authoritative input.
 */
public final class LobbyMatchCoordinator {
    private final LobbyConfiguration lobbyConfiguration;
    private final MatchConfiguration matchConfiguration;

    private LobbyRosterState roster = LobbyRosterState.initial();
    private MatchState matchState;
    private Optional<CountdownCancellationReason> cancellationReason = Optional.empty();
    private long revision;
    private long lastProcessedTick = LobbyMatchSnapshot.BEFORE_FIRST_TICK;

    public LobbyMatchCoordinator(
            LobbyConfiguration lobbyConfiguration, MatchConfiguration matchConfiguration) {
        this.lobbyConfiguration = Objects.requireNonNull(lobbyConfiguration, "lobbyConfiguration");
        this.matchConfiguration = Objects.requireNonNull(matchConfiguration, "matchConfiguration");
        if (lobbyConfiguration.minimumReadyPlayers() != matchConfiguration.minimumPlayers()) {
            throw new IllegalArgumentException(
                    "lobby and match minimum player counts must be identical");
        }
        matchState = waitingState(matchConfiguration);
    }

    public LobbyMatchSnapshot snapshot() {
        return new LobbyMatchSnapshot(
                revision,
                roster.revision(),
                lastProcessedTick,
                matchState.phase(),
                matchState.ticksRemaining(),
                matchState.connectedPlayers(),
                matchState.roundNumber(),
                matchState.result(),
                cancellationReason);
    }

    public Optional<LobbyMatchSnapshot> updateRoster(LobbyRosterState nextRoster) {
        LobbyRosterState candidate = Objects.requireNonNull(nextRoster, "nextRoster");
        if (candidate.revision() < roster.revision()) {
            throw new IllegalArgumentException("roster revision cannot move backwards");
        }
        if (candidate.revision() == roster.revision()) {
            if (!candidate.equals(roster)) {
                throw new IllegalArgumentException(
                        "the same roster revision cannot describe different state");
            }
            return Optional.empty();
        }
        if (candidate.revision() != Math.addExact(roster.revision(), 1L)) {
            throw new IllegalArgumentException("roster revision gap is not allowed");
        }

        MatchDecision decision =
                MatchLifecycle.apply(
                        matchConfiguration,
                        matchState,
                        new MatchCommand.UpdateLobbyState(
                                candidate.participants().size(),
                                candidate.readyToStart(lobbyConfiguration)));
        requireAccepted(decision, "authoritative roster produced an invalid match update");
        roster = candidate;
        commit(decision);
        advanceRevision();
        return Optional.of(snapshot());
    }

    public Optional<LobbyMatchSnapshot> advanceTick(long tickNumber) {
        if (tickNumber < 0L) {
            throw new IllegalArgumentException("tickNumber cannot be negative");
        }
        if (tickNumber < lastProcessedTick) {
            throw new IllegalArgumentException("tickNumber cannot move backwards");
        }
        if (tickNumber == lastProcessedTick) {
            return Optional.empty();
        }
        long expectedTick = Math.addExact(lastProcessedTick, 1L);
        if (tickNumber != expectedTick) {
            throw new IllegalArgumentException("simulation tick gap is not allowed");
        }

        lastProcessedTick = tickNumber;
        if (matchState.phase() != MatchPhase.START_COUNTDOWN) {
            return Optional.empty();
        }
        MatchDecision decision =
                MatchLifecycle.apply(matchConfiguration, matchState, new MatchCommand.Tick());
        requireAccepted(decision, "authoritative simulation tick was rejected");
        if (!commit(decision)) {
            return Optional.empty();
        }
        advanceRevision();
        return Optional.of(snapshot());
    }

    public boolean acceptsLobbyCommands() {
        return matchState.phase() == MatchPhase.WAITING_FOR_PLAYERS
                || matchState.phase() == MatchPhase.START_COUNTDOWN;
    }

    private boolean commit(MatchDecision decision) {
        MatchState previousState = matchState;
        Optional<CountdownCancellationReason> previousReason = cancellationReason;
        matchState = decision.state();
        for (MatchEvent event : decision.events()) {
            if (event instanceof MatchEvent.CountdownCancelled cancelled) {
                cancellationReason = Optional.of(cancelled.reason());
            } else if (event instanceof MatchEvent.PhaseChanged changed
                    && (changed.to() == MatchPhase.START_COUNTDOWN
                            || changed.to() == MatchPhase.PREPARATION)) {
                cancellationReason = Optional.empty();
            }
        }
        return !matchState.equals(previousState) || !cancellationReason.equals(previousReason);
    }

    private void advanceRevision() {
        revision = Math.addExact(revision, 1L);
    }

    private static MatchState waitingState(MatchConfiguration configuration) {
        MatchState state = MatchState.initial();
        MatchDecision loading =
                MatchLifecycle.apply(configuration, state, new MatchCommand.BeginMapLoad());
        requireAccepted(loading, "match lifecycle rejected map loading bootstrap");
        MatchDecision waiting =
                MatchLifecycle.apply(
                        configuration, loading.state(), new MatchCommand.CompleteMapLoad());
        requireAccepted(waiting, "match lifecycle rejected map loading completion");
        return waiting.state();
    }

    private static void requireAccepted(MatchDecision decision, String message) {
        if (!decision.accepted()) {
            throw new IllegalStateException(message);
        }
    }
}
