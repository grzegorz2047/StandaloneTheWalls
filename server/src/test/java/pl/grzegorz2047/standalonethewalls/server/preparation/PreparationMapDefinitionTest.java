package pl.grzegorz2047.standalonethewalls.server.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;

class PreparationMapDefinitionTest {
    @Test
    void defensivelyCopiesIdentityAndSpawnCollection() {
        byte[] sourceDigest = digest();
        PreparationSpawnPoint spawn =
                new PreparationSpawnPoint(3, TeamId.GREEN, 1.0d, 2.0d, 3.0d, 45.0d);
        List<PreparationSpawnPoint> sourceSpawns = new ArrayList<>(List.of(spawn));
        PreparationMapDefinition map =
                new PreparationMapDefinition("arena-one", sourceDigest, sourceSpawns);

        sourceDigest[0] = 99;
        sourceSpawns.clear();
        byte[] returnedDigest = map.mapSha256();
        returnedDigest[1] = 99;

        assertThat(map.mapId()).isEqualTo("arena-one");
        assertThat(map.mapSha256()).containsExactly(digest());
        assertThat(map.spawnPoints()).containsExactly(spawn);
        assertThatThrownBy(() -> map.spawnPoints().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidIdentityDigestAndSpawnCollection() {
        PreparationSpawnPoint spawn =
                new PreparationSpawnPoint(1, TeamId.BLUE, 0.0d, 0.0d, 0.0d, 0.0d);
        String overlongMapId = "m".repeat(PreparationMapDefinition.MAXIMUM_MAP_ID_BYTES + 1);
        PreparationSpawnPoint duplicateIndex =
                new PreparationSpawnPoint(1, TeamId.GREEN, 10.0d, 0.0d, 0.0d, 90.0d);

        assertThatThrownBy(() -> new PreparationMapDefinition("", digest(), List.of(spawn)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new PreparationMapDefinition("map id", digest(), List.of(spawn)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new PreparationMapDefinition(overlongMapId, digest(), List.of(spawn)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new PreparationMapDefinition(
                                        "arena-one", Arrays.copyOf(digest(), 31), List.of(spawn)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new PreparationMapDefinition("arena-one", digest(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new PreparationMapDefinition(
                                        "arena-one", digest(), List.of(spawn, duplicateIndex)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] digest() {
        byte[] digest = new byte[PreparationMapDefinition.SHA_256_BYTES];
        for (int index = 0; index < digest.length; index++) {
            digest[index] = (byte) index;
        }
        return digest;
    }
}
