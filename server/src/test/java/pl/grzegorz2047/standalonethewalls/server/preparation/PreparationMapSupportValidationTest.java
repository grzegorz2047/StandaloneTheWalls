package pl.grzegorz2047.standalonethewalls.server.preparation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;
import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationSupportBox;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationSupportMap;

class PreparationMapSupportValidationTest {
    private static final byte[] MAP_DIGEST = new byte[32];
    private static final PreparationRegionBounds REGION =
            new PreparationRegionBounds(TeamId.RED, -1_000, 0, -1_000, 1_000, 2_000, 1_000);
    private static final PreparationSpawnPoint SPAWN =
            new PreparationSpawnPoint(0, TeamId.RED, 0.0d, 0.5d, 0.0d, 0.0d);

    @Test
    void rejectsSupportOutsideEveryPlayableRegion() {
        PreparationSupportMap supports =
                new PreparationSupportMap(
                        List.of(
                                box("GroundCollision", -1.0d, -1.0d, -1.0d, 1.0d, 0.0d, 1.0d),
                                box(
                                        "OutsideSupportCollision",
                                        2.0d,
                                        0.0d,
                                        2.0d,
                                        3.0d,
                                        0.5d,
                                        3.0d)));

        assertThatThrownBy(() -> definition(supports))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside every authoritative region");
    }

    @Test
    void rejectsSupportWithoutFullJumpClearance() {
        PreparationSupportMap supports =
                new PreparationSupportMap(
                        List.of(
                                box("GroundCollision", -1.0d, -1.0d, -1.0d, 1.0d, 0.0d, 1.0d),
                                box(
                                        "HighSupportCollision",
                                        0.5d,
                                        0.0d,
                                        -0.5d,
                                        1.0d,
                                        1.0d,
                                        0.5d)));

        assertThatThrownBy(() -> definition(supports))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vertical region bounds");
    }

    private static PreparationMapDefinition definition(PreparationSupportMap supports) {
        return new PreparationMapDefinition(
                "minimal_preparation",
                MAP_DIGEST,
                List.of(SPAWN),
                Map.of(TeamId.RED, REGION),
                supports);
    }

    private static PreparationSupportBox box(
            String name,
            double minimumX,
            double minimumY,
            double minimumZ,
            double maximumX,
            double maximumY,
            double maximumZ) {
        return new PreparationSupportBox(
                name,
                new MapVector3(minimumX, minimumY, minimumZ),
                new MapVector3(maximumX, maximumY, maximumZ));
    }
}
