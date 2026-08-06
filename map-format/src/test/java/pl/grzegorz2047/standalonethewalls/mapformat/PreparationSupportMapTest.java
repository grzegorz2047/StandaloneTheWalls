package pl.grzegorz2047.standalonethewalls.mapformat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class PreparationSupportMapTest {
    @Test
    void selectsTheHighestSupportAtOrBelowTheRequestedPlayerHeight() {
        PreparationSupportMap supports =
                new PreparationSupportMap(
                        List.of(
                                box("RaisedSupportCollision", -2.0d, 0.0d, -2.0d, 2.0d, 0.5d, 2.0d),
                                box("GroundCollision", -10.0d, -1.0d, -10.0d, 10.0d, 0.0d, 10.0d)));

        assertThat(supports.highestPlayerCenter(0.0d, 0.0d).orElseThrow())
                .isCloseTo(1.0d, within(0.000001d));
        assertThat(supports.highestPlayerCenterAtOrBelow(0.0d, 0.0d, 0.75d).orElseThrow())
                .isCloseTo(0.5d, within(0.000001d));
        assertThat(supports.highestPlayerCenter(8.0d, 8.0d).orElseThrow())
                .isCloseTo(0.5d, within(0.000001d));
    }

    @Test
    void sortsByCanonicalNameAndReturnsEmptyOutsideEveryFootprint() {
        PreparationSupportMap supports =
                new PreparationSupportMap(
                        List.of(
                                box("ZuluSupportCollision", 0.0d, 0.0d, 0.0d, 1.0d, 1.0d, 1.0d),
                                box("GroundCollision", -1.0d, -1.0d, -1.0d, 0.0d, 0.0d, 0.0d)));

        assertThat(supports.boxes())
                .extracting(PreparationSupportBox::name)
                .containsExactly("GroundCollision", "ZuluSupportCollision");
        assertThat(supports.highestPlayerCenter(5.0d, 5.0d)).isEmpty();
    }

    @Test
    void rejectsMissingGroundDuplicateNamesAndInvalidQueries() {
        PreparationSupportBox ground =
                box("GroundCollision", -1.0d, -1.0d, -1.0d, 1.0d, 0.0d, 1.0d);

        assertThatThrownBy(
                        () ->
                                new PreparationSupportMap(
                                        List.of(
                                                box(
                                                        "OnlySupportCollision",
                                                        -1.0d,
                                                        0.0d,
                                                        -1.0d,
                                                        1.0d,
                                                        1.0d,
                                                        1.0d))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PreparationSupportMap(List.of(ground, ground)))
                .isInstanceOf(IllegalArgumentException.class);
        PreparationSupportMap supports = new PreparationSupportMap(List.of(ground));
        assertThatThrownBy(() -> supports.highestPlayerCenter(Double.NaN, 0.0d))
                .isInstanceOf(IllegalArgumentException.class);
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
