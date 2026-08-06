package pl.grzegorz2047.standalonethewalls.client.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class PreparationRemotePlayerRendererTest {
    @Test
    void keepsRemoteFeetGroundedAcrossTheInterpolatedCrouchRange() {
        assertPosture(0.0d, 0.9f);
        assertPosture(0.5d, 0.725f);
        assertPosture(1.0d, 0.55f);
    }

    @Test
    void rejectsInvalidPresentationGeometryInputs() {
        assertThatThrownBy(() -> PreparationRemotePlayerRenderer.halfHeight(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PreparationRemotePlayerRenderer.halfHeight(-0.01d))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PreparationRemotePlayerRenderer.halfHeight(1.01d))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PreparationRemotePlayerRenderer.centreY(Double.NaN, 0.5d))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertPosture(double crouchAmount, float expectedHalfHeight) {
        float groundY = 10.0f;
        float halfHeight = PreparationRemotePlayerRenderer.halfHeight(crouchAmount);
        float centreY = PreparationRemotePlayerRenderer.centreY(groundY, crouchAmount);

        assertThat(halfHeight).isCloseTo(expectedHalfHeight, within(0.000001f));
        assertThat(centreY).isCloseTo(groundY + expectedHalfHeight, within(0.000001f));
        assertThat(centreY - halfHeight).isCloseTo(groundY, within(0.000001f));
    }
}
