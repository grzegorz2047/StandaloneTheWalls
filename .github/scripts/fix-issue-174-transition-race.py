from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"expected one match in {path}, found {count}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


replace_once(
    "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/preparation/PreparationTransitionPublisher.java",
    """        Map<LobbyParticipantId, ReliableChannel> availableChannels =
                Map.copyOf(Objects.requireNonNull(channels, \"channels\"));
        Duration boundedTimeout = requireTimeout(timeout);
        List<PreparationClientSpawn> plan =
                PreparationTransitionPlanner.plan(map, roster, matchSnapshot);
        validateCoverageAndPayloads(plan, availableChannels);

        long deadline = System.nanoTime() + boundedTimeout.toNanos();
        byte[] snapshotPayload =
                LobbyMatchProtocolCodec.encodeSnapshot(preparationSnapshot(matchSnapshot));
        publishSnapshot(availableChannels, snapshotPayload, deadline);

        try {
            PreparationSpawnPublisher.publish(plan, availableChannels, remainingDuration(deadline));
        } catch (PreparationSpawnPublishException exception) {
            throw new PreparationTransitionPublishException(
                    PreparationTransitionPublishException.Code.ASSIGNMENT_PUBLISH_FAILED,
                    \"preparation spawn assignment publication failed\",
                    exception);
        }
        return plan;
    }
""",
    """        List<PreparationClientSpawn> plan =
                PreparationTransitionPlanner.plan(map, roster, matchSnapshot);
        publish(plan, matchSnapshot, channels, timeout);
        return plan;
    }

    public static void publish(
            List<PreparationClientSpawn> plan,
            LobbyMatchSnapshot matchSnapshot,
            Map<LobbyParticipantId, ReliableChannel> channels,
            Duration timeout) {
        List<PreparationClientSpawn> planned =
                List.copyOf(Objects.requireNonNull(plan, \"plan\"));
        Map<LobbyParticipantId, ReliableChannel> availableChannels =
                Map.copyOf(Objects.requireNonNull(channels, \"channels\"));
        Duration boundedTimeout = requireTimeout(timeout);
        validateCoverageAndPayloads(planned, availableChannels);

        long deadline = System.nanoTime() + boundedTimeout.toNanos();
        byte[] snapshotPayload =
                LobbyMatchProtocolCodec.encodeSnapshot(preparationSnapshot(matchSnapshot));
        publishSnapshot(availableChannels, snapshotPayload, deadline);

        try {
            PreparationSpawnPublisher.publish(
                    planned, availableChannels, remainingDuration(deadline));
        } catch (PreparationSpawnPublishException exception) {
            throw new PreparationTransitionPublishException(
                    PreparationTransitionPublishException.Code.ASSIGNMENT_PUBLISH_FAILED,
                    \"preparation spawn assignment publication failed\",
                    exception);
        }
    }
""",
)

replace_once(
    "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/lobby/MinimalLobbyRuntime.java",
    """import pl.grzegorz2047.standalonethewalls.server.preparation.PreparationMovementSimulation;
import pl.grzegorz2047.standalonethewalls.server.preparation.PreparationTransitionPublisher;
""",
    """import pl.grzegorz2047.standalonethewalls.server.preparation.PreparationMovementSimulation;
import pl.grzegorz2047.standalonethewalls.server.preparation.PreparationTransitionPlanner;
import pl.grzegorz2047.standalonethewalls.server.preparation.PreparationTransitionPublisher;
""",
)

replace_once(
    "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/lobby/MinimalLobbyRuntime.java",
    """        List<PreparationClientSpawn> plan =
                PreparationTransitionPublisher.publish(
                        preparationMap,
                        state.roster,
                        matchSnapshot,
                        preparationChannels(state),
                        sendTimeout);
        Map<
                        pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId,
                        PreparationSpawnAssignment>
                assignments = new HashMap<>();
        for (PreparationClientSpawn delivery : plan) {
            MemberState member = state.members.get(delivery.participantId());
            if (member == null) {
                throw new IllegalStateException(
                        \"preparation transition plan contains an unowned participant\");
            }
            member.preparationInputMailbox.open(matchSnapshot.roundNumber());
            assignments.put(member.identity.playerId(), delivery.assignment());
        }
        state.preparationMovement =
                PreparationMovementSimulation.start(
                        matchSnapshot.roundNumber(),
                        matchSnapshot.authoritativeTick(),
                        preparationMap,
                        assignments);
        publishPreparationSnapshot(
                state, state.preparationMovement.currentSnapshot().orElseThrow());
""",
    """        List<PreparationClientSpawn> plan =
                PreparationTransitionPlanner.plan(preparationMap, state.roster, matchSnapshot);
        Map<
                        pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId,
                        PreparationSpawnAssignment>
                assignments = new HashMap<>();
        for (PreparationClientSpawn delivery : plan) {
            MemberState member = state.members.get(delivery.participantId());
            if (member == null) {
                throw new IllegalStateException(
                        \"preparation transition plan contains an unowned participant\");
            }
            member.preparationInputMailbox.open(matchSnapshot.roundNumber());
            assignments.put(member.identity.playerId(), delivery.assignment());
        }
        state.preparationMovement =
                PreparationMovementSimulation.start(
                        matchSnapshot.roundNumber(),
                        matchSnapshot.authoritativeTick(),
                        preparationMap,
                        assignments);
        try {
            PreparationTransitionPublisher.publish(
                    plan, matchSnapshot, preparationChannels(state), sendTimeout);
        } catch (RuntimeException exception) {
            state.preparationMovement = null;
            for (MemberState member : state.members.values()) {
                member.preparationInputMailbox.close();
            }
            throw exception;
        }
        publishPreparationSnapshot(
                state, state.preparationMovement.currentSnapshot().orElseThrow());
""",
)
