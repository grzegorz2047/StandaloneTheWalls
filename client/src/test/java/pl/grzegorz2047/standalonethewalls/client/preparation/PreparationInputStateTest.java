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

        assertThat(input.captured()).isFalse();
        assertThat(input.forwardAxis()).isZero();
        assertThat(input.rightAxis()).isZero();
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

        assertThat(input.release()).isTrue();

        assertThat(input.captured()).isFalse();
        assertThat(input.forwardAxis()).isZero();
        assertThat(input.rightAxis()).isZero();
        assertThat(input.release()).isFalse();
    }

    @Test
    void repeatedCaptureDoesNotResetPressedDirections() {
        PreparationInputState input = new PreparationInputState();
        input.capture();
        input.set(Direction.BACKWARD, true);

        assertThat(input.capture()).isFalse();
        assertThat(input.forwardAxis()).isEqualTo(-1.0d);
    }
}
