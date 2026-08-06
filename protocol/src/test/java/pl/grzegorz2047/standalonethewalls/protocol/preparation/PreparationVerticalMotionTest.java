package pl.grzegorz2047.standalonethewalls.protocol.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class PreparationVerticalMotionTest {
    @Test
    void startsAJumpAndIntegratesTheSharedBallisticArc() {
        PreparationVerticalMotion.Step first =
                PreparationVerticalMotion.advance(0.5d, 0.5d, 0.0d, true, true, 0.05d);

        assertThat(first.grounded()).isFalse();
        assertThat(first.heightMetres()).isCloseTo(0.7775d, within(0.0000001d));
        assertThat(first.verticalVelocityMetresPerSecond())
                .isCloseTo(5.1d, within(0.0000001d));

        PreparationVerticalMotion.Step state = first;
        for (int index = 0; index < 20 && !state.grounded(); index++) {
            state =
                    PreparationVerticalMotion.advance(
                            state.heightMetres(),
                            0.5d,
                            state.verticalVelocityMetresPerSecond(),
                            state.grounded(),
                            false,
                            0.05d);
        }

        assertThat(state.heightMetres()).isEqualTo(0.5d);
        assertThat(state.verticalVelocityMetresPerSecond()).isZero();
        assertThat(state.grounded()).isTrue();
    }

    @Test
    void ignoresAirJumpAndDoesNotQueueItForLanding() {
        PreparationVerticalMotion.Step withJump =
                PreparationVerticalMotion.advance(1.0d, 0.5d, -5.0d, false, true, 0.05d);
        PreparationVerticalMotion.Step withoutJump =
                PreparationVerticalMotion.advance(1.0d, 0.5d, -5.0d, false, false, 0.05d);

        assertThat(withJump).isEqualTo(withoutJump);
    }

    @Test
    void capsLongStepsAndTerminalFallSpeedThroughValidation() {
        PreparationVerticalMotion.Step falling =
                PreparationVerticalMotion.advance(100.0d, 0.0d, -29.5d, false, false, 0.1d);

        assertThat(falling.verticalVelocityMetresPerSecond()).isEqualTo(-30.0d);
        assertThat(falling.heightMetres()).isLessThan(97.0d);
        assertThatThrownBy(
                        () ->
                                PreparationVerticalMotion.advance(
                                        1.0d, 0.0d, 0.0d, false, false, 0.100001d))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonCanonicalGroundedAndOutOfRangeStates() {
        assertThatThrownBy(
                        () ->
                                PreparationVerticalMotion.advance(
                                        0.0d, 0.0d, 1.0d, true, false, 0.05d))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                PreparationVerticalMotion.advance(
                                        -0.01d, 0.0d, 0.0d, false, false, 0.05d))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                PreparationVerticalMotion.advance(
                                        1.0d, 0.0d, -30.01d, false, false, 0.05d))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
