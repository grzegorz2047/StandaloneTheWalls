package pl.grzegorz2047.standalonethewalls.mapformat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;

class PreparationWorldBoundsTest {
    @Test
    void coversEveryVerifiedRegionAndClampsFiniteHorizontalCoordinates() {
        PreparationWorldBounds bounds =
                PreparationWorldBounds.fromRegions(
                        List.of(
                                new PreparationRegion(
                                        PreparationTeam.RED,
                                        new MapVector3(-10.0d, -1.0d, -8.0d),
                                        new MapVector3(-0.5d, 6.0d, 8.0d)),
                                new PreparationRegion(
                                        PreparationTeam.BLUE,
                                        new MapVector3(-0.5d, -2.0d, -12.0d),
                                        new MapVector3(11.0d, 7.0d, 12.0d))));

        assertThat(bounds.minimum()).isEqualTo(new MapVector3(-10.0d, -2.0d, -12.0d));
        assertThat(bounds.maximum()).isEqualTo(new MapVector3(11.0d, 7.0d, 12.0d));
        assertThat(bounds.clampX(-20.0d)).isEqualTo(-10.0d);
        assertThat(bounds.clampX(4.0d)).isEqualTo(4.0d);
        assertThat(bounds.clampZ(20.0d)).isEqualTo(12.0d);
        assertThat(bounds.contains(new MapVector3(0.0d, 0.5d, 0.0d))).isTrue();
    }

    @Test
    void rejectsEmptyRegionSetsAndNonFiniteClampInputs() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PreparationWorldBounds.fromRegions(List.of()))
                .withMessageContaining("at least one");
        PreparationWorldBounds bounds =
                new PreparationWorldBounds(
                        new MapVector3(-1.0d, -1.0d, -1.0d),
                        new MapVector3(1.0d, 1.0d, 1.0d));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> bounds.clampX(Double.NaN))
                .withMessageContaining("finite");
    }
}
