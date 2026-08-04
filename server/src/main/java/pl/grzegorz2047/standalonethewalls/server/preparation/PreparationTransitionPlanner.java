package pl.grzegorz2047.standalonethewalls.server.preparation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyRosterState;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchPhase;
import pl.grzegorz2047.standalonethewalls.server.lobby.LobbyMatchSnapshot;

/** Builds the complete participant-targeted spawn delivery plan before any network send occurs. */
public final class PreparationTransitionPlanner {
    private PreparationTransitionPlanner() {
        throw new AssertionError("No instances");
    }

    public static List<PreparationClientSpawn> plan(
            PreparationMapDefinition map,
            LobbyRosterState roster,
            LobbyMatchSnapshot matchSnapshot) {
        PreparationMapDefinition pinnedMap = Objects.requireNonNull(map, "map");
        LobbyRosterState authoritativeRoster = Objects.requireNonNull(roster, "roster");
        LobbyMatchSnapshot authoritativeMatch =
                Objects.requireNonNull(matchSnapshot, "matchSnapshot");

        if (authoritativeMatch.phase() != MatchPhase.PREPARATION) {
            throw failure(
                    PreparationTransitionPlanException.Code.INVALID_PHASE,
                    "preparation transition plan requires the preparation phase");
        }
        if (authoritativeMatch.rosterRevision() != authoritativeRoster.revision()) {
            throw failure(
                    PreparationTransitionPlanException.Code.ROSTER_REVISION_MISMATCH,
                    "preparation match snapshot does not describe the authoritative roster");
        }
        if (authoritativeMatch.connectedPlayers() != authoritativeRoster.participants().size()) {
            throw failure(
                    PreparationTransitionPlanException.Code.PLAYER_COUNT_MISMATCH,
                    "preparation match snapshot player count does not match the roster");
        }

        List<PreparationSpawnAssignment> allocations;
        try {
            allocations =
                    PreparationSpawnAllocator.allocate(
                            authoritativeRoster, pinnedMap.spawnPoints());
        } catch (PreparationSpawnAllocationException exception) {
            throw new PreparationTransitionPlanException(
                    PreparationTransitionPlanException.Code.SPAWN_ALLOCATION_FAILED,
                    "preparation spawn allocation failed",
                    exception);
        }
        if (allocations.size() != authoritativeRoster.participants().size()) {
            throw failure(
                    PreparationTransitionPlanException.Code.ASSIGNMENT_COUNT_MISMATCH,
                    "preparation spawn allocation did not cover the complete roster");
        }

        List<PreparationClientSpawn> planned = new ArrayList<>(allocations.size());
        for (PreparationSpawnAssignment allocation : allocations) {
            planned.add(
                    new PreparationClientSpawn(
                            allocation.participantId(),
                            PreparationSpawnProtocolAdapter.toProtocol(
                                    pinnedMap,
                                    authoritativeRoster.revision(),
                                    authoritativeMatch.roundNumber(),
                                    allocation)));
        }
        return List.copyOf(planned);
    }

    private static PreparationTransitionPlanException failure(
            PreparationTransitionPlanException.Code code, String message) {
        return new PreparationTransitionPlanException(code, message);
    }
}
