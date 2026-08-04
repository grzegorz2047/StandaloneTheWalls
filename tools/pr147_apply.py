from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


session_path = Path(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/network/ConnectedLobbySession.java"
)
session = session_path.read_text(encoding="utf-8")
session = replace_once(
    session,
    "import java.util.function.LongFunction;\nimport pl.grzegorz2047.standalonethewalls.protocol.MessageType;",
    "import java.util.function.LongFunction;\n"
    "import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationSceneLoadException;\n"
    "import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationSceneLoader;\n"
    "import pl.grzegorz2047.standalonethewalls.client.preparation.VerifiedPreparationScene;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.MessageType;",
    "preparation imports",
)
session = replace_once(
    session,
    "    private final AtomicReference<PreparationSpawnAssignment> preparationSpawnAssignment =\n"
    "            new AtomicReference<>();",
    "    private final AtomicReference<PreparationState> preparationState = new AtomicReference<>();",
    "atomic preparation state",
)
session = replace_once(
    session,
    """    public Optional<PreparationSpawnAssignment> currentPreparationSpawnAssignment() {
        return Optional.ofNullable(preparationSpawnAssignment.get());
    }
""",
    """    public Optional<PreparationSpawnAssignment> currentPreparationSpawnAssignment() {
        PreparationState current = preparationState.get();
        return current == null ? Optional.empty() : Optional.of(current.assignment());
    }

    public Optional<VerifiedPreparationScene> currentVerifiedPreparationScene() {
        PreparationState current = preparationState.get();
        return current == null ? Optional.empty() : Optional.of(current.scene());
    }
""",
    "preparation getters",
)
old_process = """    private Optional<DirectConnectFailure> processPreparationSpawnAssignment(byte[] payload) {
        PreparationSpawnAssignment assignment;
        try {
            assignment = PreparationSpawnProtocolCodec.decodeAssignment(payload);
        } catch (PreparationProtocolException exception) {
            return Optional.of(
                    DirectConnectFailure.of(DirectConnectFailureCode.PREPARATION_SPAWN_MALFORMED));
        }

        synchronized (commandLock) {
            if (preparationSpawnAssignment.get() != null) {
                return Optional.of(
                        DirectConnectFailure.of(
                                DirectConnectFailureCode.PREPARATION_SPAWN_DUPLICATE));
            }
            LobbyMatchPhaseSnapshot currentMatch = matchSnapshot.get();
            if (currentMatch.phase() != LobbyMatchPhase.PREPARATION) {
                return Optional.of(
                        DirectConnectFailure.of(
                                DirectConnectFailureCode.PREPARATION_SPAWN_UNEXPECTED_PHASE));
            }
            LobbySnapshot currentRoster = snapshot.get();
            if (assignment.rosterRevision() != currentRoster.revision()
                    || assignment.rosterRevision() != currentMatch.rosterRevision()) {
                return Optional.of(
                        DirectConnectFailure.of(
                                DirectConnectFailureCode.PREPARATION_SPAWN_ROSTER_MISMATCH));
            }
            if (assignment.roundNumber() != currentMatch.roundNumber()) {
                return Optional.of(
                        DirectConnectFailure.of(
                                DirectConnectFailureCode.PREPARATION_SPAWN_ROUND_MISMATCH));
            }
            Optional<LobbyTeam> selfTeam = selfTeam(currentRoster);
            if (selfTeam.isEmpty() || assignment.team() != selfTeam.orElseThrow()) {
                return Optional.of(
                        DirectConnectFailure.of(
                                DirectConnectFailureCode.PREPARATION_SPAWN_TEAM_MISMATCH));
            }
            preparationSpawnAssignment.set(assignment);
        }
        return Optional.empty();
    }
"""
new_process = """    private Optional<DirectConnectFailure> processPreparationSpawnAssignment(byte[] payload) {
        PreparationSpawnAssignment assignment;
        try {
            assignment = PreparationSpawnProtocolCodec.decodeAssignment(payload);
        } catch (PreparationProtocolException exception) {
            return Optional.of(
                    DirectConnectFailure.of(DirectConnectFailureCode.PREPARATION_SPAWN_MALFORMED));
        }

        synchronized (commandLock) {
            if (preparationState.get() != null) {
                return Optional.of(
                        DirectConnectFailure.of(
                                DirectConnectFailureCode.PREPARATION_SPAWN_DUPLICATE));
            }
            LobbyMatchPhaseSnapshot currentMatch = matchSnapshot.get();
            if (currentMatch.phase() != LobbyMatchPhase.PREPARATION) {
                return Optional.of(
                        DirectConnectFailure.of(
                                DirectConnectFailureCode.PREPARATION_SPAWN_UNEXPECTED_PHASE));
            }
            LobbySnapshot currentRoster = snapshot.get();
            if (assignment.rosterRevision() != currentRoster.revision()
                    || assignment.rosterRevision() != currentMatch.rosterRevision()) {
                return Optional.of(
                        DirectConnectFailure.of(
                                DirectConnectFailureCode.PREPARATION_SPAWN_ROSTER_MISMATCH));
            }
            if (assignment.roundNumber() != currentMatch.roundNumber()) {
                return Optional.of(
                        DirectConnectFailure.of(
                                DirectConnectFailureCode.PREPARATION_SPAWN_ROUND_MISMATCH));
            }
            Optional<LobbyTeam> selfTeam = selfTeam(currentRoster);
            if (selfTeam.isEmpty() || assignment.team() != selfTeam.orElseThrow()) {
                return Optional.of(
                        DirectConnectFailure.of(
                                DirectConnectFailureCode.PREPARATION_SPAWN_TEAM_MISMATCH));
            }
            VerifiedPreparationScene scene;
            try {
                scene = PreparationSceneLoader.loadDefault(assignment);
            } catch (PreparationSceneLoadException exception) {
                return Optional.of(
                        DirectConnectFailure.of(preparationSceneFailureCode(exception.code())));
            }
            preparationState.set(new PreparationState(assignment, scene));
        }
        return Optional.empty();
    }
"""
session = replace_once(session, old_process, new_process, "assignment processing")
session = replace_once(
    session,
    "    private static final class PendingCommand {",
    """    private static DirectConnectFailureCode preparationSceneFailureCode(
            PreparationSceneLoadException.Code code) {
        return switch (Objects.requireNonNull(code, "code")) {
            case BUNDLE_LOAD_FAILED -> DirectConnectFailureCode.PREPARATION_MAP_UNAVAILABLE;
            case MAP_ID_MISMATCH -> DirectConnectFailureCode.PREPARATION_MAP_ID_MISMATCH;
            case MAP_SHA256_MISMATCH -> DirectConnectFailureCode.PREPARATION_MAP_SHA256_MISMATCH;
            case SCENE_INVALID, COLLISION_INVALID ->
                    DirectConnectFailureCode.PREPARATION_SCENE_INVALID;
            case REGION_MISSING, SPAWN_MISSING, SPAWN_STATE_MISMATCH ->
                    DirectConnectFailureCode.PREPARATION_SPAWN_NOT_IN_MAP;
        };
    }

    private record PreparationState(
            PreparationSpawnAssignment assignment, VerifiedPreparationScene scene) {
        private PreparationState {
            Objects.requireNonNull(assignment, "assignment");
            Objects.requireNonNull(scene, "scene");
        }
    }

    private static final class PendingCommand {",
    "failure mapping and preparation record",
)
session_path.write_text(session, encoding="utf-8")

for path, marker, additions in (
    (
        Path("client/src/main/resources/i18n/messages_en.properties"),
        "direct.failure.preparation_spawn_team_mismatch=The preparation spawn assignment targeted another team.\n",
        "direct.failure.preparation_map_unavailable=The required preparation map could not be verified locally.\n"
        "direct.failure.preparation_map_id_mismatch=The server selected a different preparation map than the verified local map.\n"
        "direct.failure.preparation_map_sha256_mismatch=The server preparation map digest does not match the verified local archive.\n"
        "direct.failure.preparation_scene_invalid=The verified preparation scene or collision document is invalid.\n"
        "direct.failure.preparation_spawn_not_in_map=The server preparation spawn is not present in the verified local map.\n",
    ),
    (
        Path("client/src/main/resources/i18n/messages_pl.properties"),
        "direct.failure.preparation_spawn_team_mismatch=Przypisanie spawnu dotyczylo innej druzyny.\n",
        "direct.failure.preparation_map_unavailable=Nie udalo sie lokalnie zweryfikowac wymaganej mapy przygotowania.\n"
        "direct.failure.preparation_map_id_mismatch=Serwer wybral inna mape przygotowania niz zweryfikowana mapa lokalna.\n"
        "direct.failure.preparation_map_sha256_mismatch=Digest mapy przygotowania serwera nie pasuje do zweryfikowanego lokalnego archiwum.\n"
        "direct.failure.preparation_scene_invalid=Zweryfikowana scena przygotowania albo dokument kolizji jest nieprawidlowy.\n"
        "direct.failure.preparation_spawn_not_in_map=Spawn przygotowania wskazany przez serwer nie istnieje w zweryfikowanej lokalnej mapie.\n",
    ),
):
    text = path.read_text(encoding="utf-8")
    text = replace_once(text, marker, marker + additions, str(path))
    path.write_text(text, encoding="utf-8")

TEST_CONTENT = r'''package pl.grzegorz2047.standalonethewalls.client.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.client.preparation.VerifiedPreparationScene;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionStatus;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCountdownCancellationReason;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhase;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhaseSnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;

class ConnectedPreparationSpawnAssignmentTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    @Test
    void acceptsOneVerifiedSceneAfterTheAuthoritativePreparationSnapshot()
            throws InterruptedException, ExecutionException, TimeoutException {
        PreparedLobby prepared = preparedLobby();
        PreparationSpawnAssignment assignment = assignment(2L, 1L, LobbyTeam.GREEN);

        prepared.lobby().deliverPreparationSpawnAssignment(assignment, 4L);
        waitUntil(() -> prepared.session().currentVerifiedPreparationScene().isPresent());

        assertThat(prepared.session().currentPreparationSpawnAssignment()).contains(assignment);
        VerifiedPreparationScene scene =
                prepared.session().currentVerifiedPreparationScene().orElseThrow();
        assertThat(scene.mapId()).isEqualTo(MinimalPreparationBundle.MAP_ID);
        assertThat(scene.mapSha256()).containsExactly(mapDigest());
        assertThat(scene.spawn().index()).isZero();
        assertThat(scene.region().contains(scene.spawn().position())).isTrue();
        assertThat(prepared.session().terminalFailure()).isEmpty();
        prepared.session()
                .closeAsync()
                .toCompletableFuture()
                .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Test
    void closesOnMalformedAssignmentPayload()
            throws InterruptedException, ExecutionException, TimeoutException {
        PreparedLobby prepared = preparedLobby();
        prepared.lobby().deliverPreparationSpawnPayload(new byte[] {1}, 4L);
        assertFailure(prepared.session(), DirectConnectFailureCode.PREPARATION_SPAWN_MALFORMED);
    }

    @Test
    void closesWhenAssignmentArrivesBeforePreparation()
            throws InterruptedException, ExecutionException, TimeoutException {
        DirectConnectUiTestFixtures.ControlledLobby lobby =
                DirectConnectUiTestFixtures.controlledLobby();
        ConnectedLobbySession session = takeSession(lobby);
        lobby.deliverPreparationSpawnAssignment(assignment(1L, 1L, LobbyTeam.GREEN), 2L);
        assertFailure(session, DirectConnectFailureCode.PREPARATION_SPAWN_UNEXPECTED_PHASE);
    }

    @Test
    void closesOnDuplicateAssignment()
            throws InterruptedException, ExecutionException, TimeoutException {
        PreparedLobby prepared = preparedLobby();
        PreparationSpawnAssignment assignment = assignment(2L, 1L, LobbyTeam.GREEN);
        prepared.lobby().deliverPreparationSpawnAssignment(assignment, 4L);
        waitUntil(() -> prepared.session().currentVerifiedPreparationScene().isPresent());
        prepared.lobby().deliverPreparationSpawnAssignment(assignment, 5L);
        assertFailure(prepared.session(), DirectConnectFailureCode.PREPARATION_SPAWN_DUPLICATE);
    }

    @Test
    void closesWhenAssignmentTargetsAnotherRosterRevision()
            throws InterruptedException, ExecutionException, TimeoutException {
        PreparedLobby prepared = preparedLobby();
        prepared.lobby().deliverPreparationSpawnAssignment(assignment(3L, 1L, LobbyTeam.GREEN), 4L);
        assertFailure(
                prepared.session(), DirectConnectFailureCode.PREPARATION_SPAWN_ROSTER_MISMATCH);
    }

    @Test
    void closesWhenAssignmentTargetsAnotherRound()
            throws InterruptedException, ExecutionException, TimeoutException {
        PreparedLobby prepared = preparedLobby();
        prepared.lobby().deliverPreparationSpawnAssignment(assignment(2L, 2L, LobbyTeam.GREEN), 4L);
        assertFailure(
                prepared.session(), DirectConnectFailureCode.PREPARATION_SPAWN_ROUND_MISMATCH);
    }

    @Test
    void closesWhenAssignmentTargetsAnotherTeam()
            throws InterruptedException, ExecutionException, TimeoutException {
        PreparedLobby prepared = preparedLobby();
        prepared.lobby().deliverPreparationSpawnAssignment(assignment(2L, 1L, LobbyTeam.BLUE), 4L);
        assertFailure(prepared.session(), DirectConnectFailureCode.PREPARATION_SPAWN_TEAM_MISMATCH);
    }

    @Test
    void closesWhenTheServerMapIdDoesNotMatchTheVerifiedLocalMap()
            throws InterruptedException, ExecutionException, TimeoutException {
        PreparedLobby prepared = preparedLobby();
        PreparationSpawnAssignment assignment =
                assignment(
                        2L,
                        1L,
                        "other_map",
                        mapDigest(),
                        LobbyTeam.GREEN,
                        0,
                        -15.0d,
                        0.5d,
                        -14.0d,
                        45.0d);
        prepared.lobby().deliverPreparationSpawnAssignment(assignment, 4L);
        assertFailure(prepared.session(), DirectConnectFailureCode.PREPARATION_MAP_ID_MISMATCH);
    }

    @Test
    void closesWhenTheServerDigestDoesNotMatchTheVerifiedLocalArchive()
            throws InterruptedException, ExecutionException, TimeoutException {
        PreparedLobby prepared = preparedLobby();
        byte[] digest = mapDigest();
        digest[0] ^= 0x01;
        PreparationSpawnAssignment assignment =
                assignment(
                        2L,
                        1L,
                        MinimalPreparationBundle.MAP_ID,
                        digest,
                        LobbyTeam.GREEN,
                        0,
                        -15.0d,
                        0.5d,
                        -14.0d,
                        45.0d);
        prepared.lobby().deliverPreparationSpawnAssignment(assignment, 4L);
        assertFailure(
                prepared.session(), DirectConnectFailureCode.PREPARATION_MAP_SHA256_MISMATCH);
    }

    @Test
    void closesWhenTheServerSpawnDoesNotExistInTheVerifiedLocalMap()
            throws InterruptedException, ExecutionException, TimeoutException {
        PreparedLobby prepared = preparedLobby();
        PreparationSpawnAssignment assignment =
                assignment(
                        2L,
                        1L,
                        MinimalPreparationBundle.MAP_ID,
                        mapDigest(),
                        LobbyTeam.GREEN,
                        4_095,
                        -15.0d,
                        0.5d,
                        -14.0d,
                        45.0d);
        prepared.lobby().deliverPreparationSpawnAssignment(assignment, 4L);
        assertFailure(prepared.session(), DirectConnectFailureCode.PREPARATION_SPAWN_NOT_IN_MAP);
    }

    private static PreparedLobby preparedLobby() throws InterruptedException {
        DirectConnectUiTestFixtures.ControlledLobby lobby =
                DirectConnectUiTestFixtures.controlledLobby();
        ConnectedLobbySession session = takeSession(lobby);
        LobbySnapshot roster =
                DirectConnectUiTestFixtures.snapshot(
                        2L, LobbyTeam.GREEN, true, LobbyTeam.BLUE, true);
        lobby.deliverSnapshot(roster, 2L);
        waitUntil(() -> session.currentSnapshot().revision() == 2L);

        LobbyMatchPhaseSnapshot preparation =
                new LobbyMatchPhaseSnapshot(
                        2L,
                        2L,
                        10L,
                        LobbyMatchPhase.PREPARATION,
                        100L,
                        roster.members().size(),
                        1L,
                        LobbyCountdownCancellationReason.NONE);
        lobby.deliverMatchSnapshot(preparation, 3L);
        waitUntil(() -> session.currentMatchSnapshot().phase() == LobbyMatchPhase.PREPARATION);
        return new PreparedLobby(lobby, session);
    }

    private static ConnectedLobbySession takeSession(
            DirectConnectUiTestFixtures.ControlledLobby lobby) {
        DirectConnectResult result =
                lobby.connectedResult(PlayerSessionAdmissionStatus.LOCAL_FIRST_USE_ACCEPTED);
        return ((DirectConnectResult.Connected) result).takeSession();
    }

    private static PreparationSpawnAssignment assignment(
            long rosterRevision, long roundNumber, LobbyTeam team) {
        return assignment(
                rosterRevision,
                roundNumber,
                MinimalPreparationBundle.MAP_ID,
                mapDigest(),
                team,
                0,
                -15.0d,
                0.5d,
                -14.0d,
                45.0d);
    }

    private static PreparationSpawnAssignment assignment(
            long rosterRevision,
            long roundNumber,
            String mapId,
            byte[] digest,
            LobbyTeam team,
            int spawnIndex,
            double x,
            double y,
            double z,
            double yawDegrees) {
        return new PreparationSpawnAssignment(
                rosterRevision,
                roundNumber,
                mapId,
                digest,
                team,
                spawnIndex,
                x,
                y,
                z,
                yawDegrees);
    }

    private static byte[] mapDigest() {
        return HexFormat.of().parseHex(MinimalPreparationBundle.EXPECTED_ARCHIVE_SHA256);
    }

    private static void assertFailure(
            ConnectedLobbySession session, DirectConnectFailureCode expected)
            throws InterruptedException, ExecutionException, TimeoutException {
        Optional<DirectConnectFailure> failure =
                session.termination()
                        .toCompletableFuture()
                        .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        assertThat(failure).isPresent();
        assertThat(failure.orElseThrow().code()).isEqualTo(expected);
        assertThat(session.currentPreparationSpawnAssignment()).isEmpty();
        assertThat(session.currentVerifiedPreparationScene()).isEmpty();
        assertThat(session.isOpen()).isFalse();
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private record PreparedLobby(
            DirectConnectUiTestFixtures.ControlledLobby lobby, ConnectedLobbySession session) {}
}
'''
Path(
    "client/src/test/java/pl/grzegorz2047/standalonethewalls/client/network/ConnectedPreparationSpawnAssignmentTest.java"
).write_text(TEST_CONTENT, encoding="utf-8")
