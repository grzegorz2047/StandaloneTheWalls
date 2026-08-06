package pl.grzegorz2047.standalonethewalls.client.preparation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationInputState.Direction;

class PreparationInputStateTest {
    @Test
    void ignoresMovementUntilInputIsExplicitlyCaptured() {
        PreparationInputState input = new PreparationInputState();

        input.set(Direction.FORWARD, true);
        input.set(Direction.RIGHT, true);
        input.setSprinting(true);
        input.setCrouching(true);

        assertThat(input.captured()).isFalse();
        assertThat(input.forwardAxis()).isZero();
        assertThat(input.rightAxis()).isZero();
        assertThat(input.sprinting()).isFalse();
        assertThat(input.crouching()).isFalse();
    }

    @Test
    void resolvesOpposingDirectionsToBoundedAxes() {
        PreparationInputState input = new PreparationInputState();
        assertThat(input.capture()).isTrue();

        input.set(Direction.FORWARD, true);
        input.set(Direction.RIGHT, true);
        assertThat(input.forwardAxis()).isEqualTo(1.0d);
        assertThat(input.rightAxis()).isEqualTo(1.0d);

        input.set(Direction.BACKWARD, true);
        input.set(Direction.LEFT, true);
        assertThat(input.forwardAxis()).isZero();
        assertThat(input.rightAxis()).isZero();
    }

    @Test
    void releaseClearsEveryLatchedDirection() {
        PreparationInputState input = new PreparationInputState();
        input.capture();
        input.set(Direction.FORWARD, true);
        input.set(Direction.LEFT, true);
        input.setSprinting(true);
        input.setCrouching(true);

        assertThat(input.release()).isTrue();

        assertThat(input.captured()).isFalse();
        assertThat(input.forwardAxis()).isZero();
        assertThat(input.rightAxis()).isZero();
        assertThat(input.sprinting()).isFalse();
        assertThat(input.crouching()).isFalse();
        assertThat(input.release()).isFalse();
    }

    @Test
    void crouchingOverridesSprintButReleasingItRestoresHeldSprint() {
        PreparationInputState input = new PreparationInputState();
        input.capture();
        input.setSprinting(true);

        assertThat(input.sprinting()).isTrue();
        assertThat(input.crouching()).isFalse();

        input.setCrouching(true);
        assertThat(input.sprinting()).isFalse();
        assertThat(input.crouching()).isTrue();

        input.setCrouching(false);
        assertThat(input.sprinting()).isTrue();
        assertThat(input.crouching()).isFalse();
    }

    @Test
    void repeatedCaptureDoesNotResetPressedDirections() {
        PreparationInputState input = new PreparationInputState();
        input.capture();
        input.set(Direction.BACKWARD, true);
        input.setSprinting(true);

        assertThat(input.capture()).isFalse();
        assertThat(input.forwardAxis()).isEqualTo(-1.0d);
        assertThat(input.sprinting()).isTrue();
    }
}
