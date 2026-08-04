package pl.grzegorz2047.standalonethewalls.server.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyParticipantId;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;

class PreparationSpawnProtocolAdapterTest {
    @Test
    void mapsPinnedMapIdentityAndEveryAuthoritativeSpawnField() {
        byte[] digest = digest();
        PreparationSpawnPoint spawn =
                new PreparationSpawnPoint(17, TeamId.RED, 10.5d, 2.0d, -4.25d, 90.0d);
        PreparationMapDefinition map =
                new PreparationMapDefinition("arena-one", digest, List.of(spawn));
        PreparationSpawnAssignment allocation =
                new PreparationSpawnAssignment(
                        new LobbyParticipantId("player-one"), TeamId.RED, spawn);

        var protocol = PreparationSpawnProtocolAdapter.toProtocol(map, 7L, 2L, allocation);

        assertThat(protocol.rosterRevision()).isEqualTo(7L);
        assertThat(protocol.roundNumber()).isEqualTo(2L);
        assertThat(protocol.mapId()).isEqualTo("arena-one");
        assertThat(protocol.mapSha256()).containsExactly(digest());
        assertThat(protocol.team()).isEqualTo(LobbyTeam.RED);
        assertThat(protocol.spawnIndex()).isEqualTo(17);
        assertThat(protocol.x()).isEqualTo(10.5d);
        assertThat(protocol.y()).isEqualTo(2.0d);
        assertThat(protocol.z()).isEqualTo(-4.25d);
        assertThat(protocol.yawDegrees()).isEqualTo(90.0d);

        digest[0] = 99;
        assertThat(protocol.mapSha256()).containsExactly(digest());
    }

    @Test
    void mapsEveryDomainTeamWithoutClientSelectedFallbacks() {
        for (TeamId team : TeamId.values()) {
            PreparationSpawnPoint spawn =
                    new PreparationSpawnPoint(team.ordinal(), team, 0.0d, 0.0d, 0.0d, 0.0d);
            PreparationMapDefinition map =
                    new PreparationMapDefinition("arena-one", digest(), List.of(spawn));
            PreparationSpawnAssignment allocation =
                    new PreparationSpawnAssignment(
                            new LobbyParticipantId("player-" + team.ordinal()), team, spawn);

            var protocol = PreparationSpawnProtocolAdapter.toProtocol(map, 0L, 1L, allocation);

            assertThat(protocol.team().name()).isEqualTo(team.name());
        }
    }

    @Test
    void rejectsMissingInputsAndInvalidLifecycleCoordinates() {
        PreparationSpawnPoint spawn =
                new PreparationSpawnPoint(1, TeamId.GREEN, 0.0d, 0.0d, 0.0d, 0.0d);
        PreparationMapDefinition map =
                new PreparationMapDefinition("arena-one", digest(), List.of(spawn));
        PreparationSpawnAssignment allocation =
                new PreparationSpawnAssignment(
                        new LobbyParticipantId("player-one"), TeamId.GREEN, spawn);

        assertThatThrownBy(
                        () -> PreparationSpawnProtocolAdapter.toProtocol(null, 0L, 1L, allocation))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () -> PreparationSpawnProtocolAdapter.toProtocol(map, 0L, 1L, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () -> PreparationSpawnProtocolAdapter.toProtocol(map, -1L, 1L, allocation))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> PreparationSpawnProtocolAdapter.toProtocol(map, 0L, 0L, allocation))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] digest() {
        byte[] digest = new byte[PreparationMapDefinition.SHA_256_BYTES];
        for (int index = 0; index < digest.length; index++) {
            digest[index] = (byte) (index + 1);
        }
        return digest;
    }
}
