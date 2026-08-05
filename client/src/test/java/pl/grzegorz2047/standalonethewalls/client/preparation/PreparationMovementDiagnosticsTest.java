package pl.grzegorz2047.standalonethewalls.client.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PreparationMovementDiagnosticsTest {
    @Test
    void startsWaitingWithoutInventingSnapshotAge() {
        PreparationMovementDiagnostics diagnostics = new PreparationMovementDiagnostics();

        PreparationMovementDiagnostics.Snapshot current = diagnostics.current();

        assertThat(current.snapshotAvailable()).isFalse();
        assertThat(current.snapshotAgeMillis()).isEqualTo(-1L);
        assertThat(current.acknowledgementLagInputs()).isZero();
        assertThat(current.pendingPredictionSteps()).isZero();
        assertThat(current.quality()).isEqualTo(PreparationMovementDiagnostics.Quality.WAITING);
    }

    @Test
    void resetsAgeAndReportsAcknowledgementAndPredictionTail() {
        PreparationMovementDiagnostics diagnostics = new PreparationMovementDiagnostics();
        diagnostics.acceptSnapshot(10L, 3L, 5L, 7);
        diagnostics.advanceFrame(0.125d);

        PreparationMovementDiagnostics.Snapshot aged = diagnostics.current();

        assertThat(aged.snapshotAgeMillis()).isEqualTo(125L);
        assertThat(aged.acknowledgementLagInputs()).isEqualTo(2L);
        assertThat(aged.pendingPredictionSteps()).isEqualTo(7);
        assertThat(aged.quality()).isEqualTo(PreparationMovementDiagnostics.Quality.GOOD);

        diagnostics.acceptSnapshot(11L, 5L, 5L, 0);
        assertThat(diagnostics.current().snapshotAgeMillis()).isZero();
        assertThat(diagnostics.current().acknowledgementLagInputs()).isZero();
    }

    @Test
    void observesNewLocalSubmissionsWithoutResettingSnapshotAge() {
        PreparationMovementDiagnostics diagnostics = new PreparationMovementDiagnostics();
        diagnostics.acceptSnapshot(1L, 0L, 0L, 0);
        diagnostics.advanceFrame(0.1d);

        diagnostics.observeLocalState(3L, 9);
        PreparationMovementDiagnostics.Snapshot observed = diagnostics.current();

        assertThat(observed.snapshotAgeMillis()).isEqualTo(100L);
        assertThat(observed.acknowledgementLagInputs()).isEqualTo(3L);
        assertThat(observed.pendingPredictionSteps()).isEqualTo(9);
        assertThat(observed.quality()).isEqualTo(PreparationMovementDiagnostics.Quality.GOOD);

        diagnostics.observeLocalState(4L, 10);
        assertThat(diagnostics.current().quality())
                .isEqualTo(PreparationMovementDiagnostics.Quality.DELAYED);
    }

    @Test
    void classifiesAgeAtDeterministicThresholds() {
        PreparationMovementDiagnostics diagnostics = new PreparationMovementDiagnostics();
        diagnostics.acceptSnapshot(1L, 0L, 0L, 0);

        diagnostics.advanceFrame(0.249d);
        assertThat(diagnostics.current().quality())
                .isEqualTo(PreparationMovementDiagnostics.Quality.GOOD);

        diagnostics.advanceFrame(0.001d);
        assertThat(diagnostics.current().quality())
                .isEqualTo(PreparationMovementDiagnostics.Quality.DELAYED);

        diagnostics.advanceFrame(0.75d);
        assertThat(diagnostics.current().quality())
                .isEqualTo(PreparationMovementDiagnostics.Quality.STALE);
    }

    @Test
    void classifiesAcknowledgementLagWithoutTreatingPredictionStepsAsAuthority() {
        PreparationMovementDiagnostics diagnostics = new PreparationMovementDiagnostics();
        diagnostics.acceptSnapshot(1L, 0L, 3L, PreparationPredictionHistory.DEFAULT_MAXIMUM_STEPS);
        assertThat(diagnostics.current().quality())
                .isEqualTo(PreparationMovementDiagnostics.Quality.GOOD);

        diagnostics.acceptSnapshot(2L, 0L, 4L, 0);
        assertThat(diagnostics.current().quality())
                .isEqualTo(PreparationMovementDiagnostics.Quality.DELAYED);

        diagnostics.acceptSnapshot(3L, 0L, 20L, 0);
        assertThat(diagnostics.current().quality())
                .isEqualTo(PreparationMovementDiagnostics.Quality.STALE);
    }

    @Test
    void rejectsInvalidFrameTimeTickAcknowledgementAndCounters() {
        PreparationMovementDiagnostics diagnostics = new PreparationMovementDiagnostics();

        assertThrows(IllegalArgumentException.class, () -> diagnostics.advanceFrame(-0.01d));
        assertThrows(IllegalArgumentException.class, () -> diagnostics.advanceFrame(Double.NaN));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        diagnostics.advanceFrame(
                                PreparationMovementDiagnostics.MAXIMUM_FRAME_SECONDS + 0.01d));
        assertThrows(
                IllegalArgumentException.class, () -> diagnostics.acceptSnapshot(-1L, 0L, 0L, 0));
        assertThrows(IllegalArgumentException.class, () -> diagnostics.observeLocalState(0L, -1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        diagnostics.observeLocalState(
                                0L, PreparationPredictionHistory.DEFAULT_MAXIMUM_STEPS + 1));

        diagnostics.acceptSnapshot(1L, 1L, 1L, 0);
        assertThrows(
                IllegalArgumentException.class, () -> diagnostics.acceptSnapshot(1L, 1L, 1L, 0));
        assertThrows(
                IllegalArgumentException.class, () -> diagnostics.acceptSnapshot(2L, 0L, 1L, 0));
        assertThrows(
                IllegalArgumentException.class, () -> diagnostics.acceptSnapshot(2L, 2L, 1L, 0));
        assertThrows(
                IllegalArgumentException.class, () -> diagnostics.acceptSnapshot(2L, 1L, 0L, 0));
        assertThrows(IllegalArgumentException.class, () -> diagnostics.observeLocalState(0L, 0));
        assertThrows(
                IllegalArgumentException.class, () -> diagnostics.acceptSnapshot(2L, 1L, 1L, -1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        diagnostics.acceptSnapshot(
                                2L,
                                1L,
                                1L,
                                PreparationPredictionHistory.DEFAULT_MAXIMUM_STEPS + 1));
    }

    @Test
    void capsReportedAgeInsteadOfOverflowing() {
        PreparationMovementDiagnostics diagnostics = new PreparationMovementDiagnostics();
        diagnostics.acceptSnapshot(1L, 0L, 0L, 0);

        for (int index = 0; index < 200; index++) {
            diagnostics.advanceFrame(PreparationMovementDiagnostics.MAXIMUM_FRAME_SECONDS);
        }

        assertThat(diagnostics.current().snapshotAgeMillis()).isEqualTo(99_999L);
        assertThat(diagnostics.current().quality())
                .isEqualTo(PreparationMovementDiagnostics.Quality.STALE);
    }
}
