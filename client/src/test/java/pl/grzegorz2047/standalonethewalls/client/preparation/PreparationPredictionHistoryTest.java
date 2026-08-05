package pl.grzegorz2047.standalonethewalls.client.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jme3.asset.DesktopAssetManager;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;

class PreparationPredictionHistoryTest {
    @Test
    void replaysOnlyTheUnacknowledgedTailAfterAServerCorrection()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        PreparationPlayerState spawn = player();
        PreparationCollisionWorld collisions = collisions(spawn);
        PreparationPredictionHistory history = new PreparationPredictionHistory();

        PreparationPlayerState predicted =
                history.predict(spawn, collisions, 1L, 1.0d, 0.0d, 0.05d);
        predicted = history.predict(predicted, collisions, 1L, 1.0d, 0.0d, 0.05d);
        history.markSubmitted(1L);
        predicted = history.predict(predicted, collisions, 2L, 0.0d, 1.0d, 0.05d);
        history.markSubmitted(2L);
        assertThat(history.pendingStepCount()).isEqualTo(3);

        PreparationPlayerState authoritative =
                spawn.withAuthoritativeState(-14.5d, 0.5d, -13.5d, 45.0d, 0.0d);
        PreparationPlayerState reconciled = history.reconcile(authoritative, collisions, 1L);
        PreparationPlayerState expected =
                PreparationMovementController.move(authoritative, collisions, 0.0d, 1.0d, 0.05d);

        assertThat(reconciled.position()).isEqualTo(expected.position());
        assertThat(reconciled.position()).isNotEqualTo(predicted.position());
        assertThat(history.pendingStepCount()).isOne();
        assertThat(history.lastAcknowledgedSequence()).isEqualTo(1L);
    }

    @Test
    void fullAcknowledgementEndsExactlyAtTheAuthoritativeState()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        PreparationPlayerState spawn = player();
        PreparationCollisionWorld collisions = collisions(spawn);
        PreparationPredictionHistory history = new PreparationPredictionHistory();
        PreparationPlayerState predicted =
                history.predict(spawn, collisions, 1L, 1.0d, 0.0d, 0.05d);
        history.markSubmitted(1L);
        history.predict(predicted, collisions, 2L, 1.0d, 0.0d, 0.05d);
        history.markSubmitted(2L);
        PreparationPlayerState authoritative =
                spawn.withAuthoritativeState(-16.0d, 0.5d, -15.0d, -30.0d, 12.0d);

        PreparationPlayerState reconciled = history.reconcile(authoritative, collisions, 2L);

        assertThat(reconciled.position()).isEqualTo(authoritative.position());
        assertThat(reconciled.yawDegrees()).isEqualTo(-30.0d);
        assertThat(reconciled.pitchDegrees()).isEqualTo(12.0d);
        assertThat(history.pendingStepCount()).isZero();
    }

    @Test
    void replaysSeveralFramesOfOneSequenceAndPreservesNewestLocalView()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        PreparationPlayerState spawn = player();
        PreparationCollisionWorld collisions = collisions(spawn);
        PreparationPredictionHistory history = new PreparationPredictionHistory();
        PreparationPlayerState firstView = spawn.rotateView(15.0d, 5.0d);
        PreparationPlayerState predicted =
                history.predict(firstView, collisions, 1L, 1.0d, 0.0d, 0.02d);
        PreparationPlayerState secondView = predicted.rotateView(20.0d, 7.0d);
        predicted = history.predict(secondView, collisions, 1L, 1.0d, 0.0d, 0.03d);
        history.markSubmitted(1L);
        PreparationPlayerState latestView = predicted.rotateView(-10.0d, -2.0d);
        history.predict(latestView, collisions, 2L, 0.0d, 0.0d, 0.01d);

        PreparationPlayerState authoritative =
                spawn.withAuthoritativeState(-15.5d, 0.5d, -14.5d, 45.0d, 0.0d);
        PreparationPlayerState reconciled = history.reconcile(authoritative, collisions, 1L);

        assertThat(reconciled.position()).isEqualTo(authoritative.position());
        assertThat(reconciled.yawDegrees()).isEqualTo(latestView.yawDegrees());
        assertThat(reconciled.pitchDegrees()).isEqualTo(latestView.pitchDegrees());
        assertThat(history.pendingStepCount()).isOne();
    }

    @Test
    void replayPreservesSprintModeForEachUnacknowledgedStep()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        PreparationPlayerState spawn = player();
        PreparationCollisionWorld collisions = collisions(spawn);
        PreparationPredictionHistory history = new PreparationPredictionHistory();
        PreparationPlayerState walking =
                history.predict(spawn, collisions, 1L, 1.0d, 0.0d, false, 0.05d);
        history.markSubmitted(1L);
        history.predict(walking, collisions, 2L, 1.0d, 0.0d, true, 0.05d);
        PreparationPlayerState authoritative =
                spawn.withAuthoritativeState(-15.5d, 0.5d, -14.5d, 45.0d, 0.0d);

        PreparationPlayerState reconciled = history.reconcile(authoritative, collisions, 1L);
        PreparationPlayerState expected =
                PreparationMovementController.move(
                        authoritative, collisions, 1.0d, 0.0d, true, 0.05d);

        assertThat(reconciled.position()).isEqualTo(expected.position());
        assertThat(history.pendingStepCount()).isOne();
    }

    @Test
    void acceptsAcknowledgementForSubmittedZeroInputWithoutPredictionSteps()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        PreparationPlayerState authoritative = player();
        PreparationCollisionWorld collisions = collisions(authoritative);
        PreparationPredictionHistory history = new PreparationPredictionHistory();
        history.markSubmitted(1L);

        PreparationPlayerState reconciled = history.reconcile(authoritative, collisions, 1L);

        assertThat(reconciled).isSameAs(authoritative);
        assertThat(history.lastAcknowledgedSequence()).isEqualTo(1L);
        assertThat(history.highestSubmittedSequence()).isEqualTo(1L);
    }

    @Test
    void duplicateAcknowledgementIsIdempotentButRegressionAndFutureAckFail()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        PreparationPlayerState authoritative = player();
        PreparationCollisionWorld collisions = collisions(authoritative);
        PreparationPredictionHistory history = new PreparationPredictionHistory();
        history.markSubmitted(1L);
        assertThat(history.reconcile(authoritative, collisions, 1L)).isSameAs(authoritative);
        assertThat(history.reconcile(authoritative, collisions, 1L)).isSameAs(authoritative);

        assertThrows(
                IllegalArgumentException.class,
                () -> history.reconcile(authoritative, collisions, 0L));
        assertThrows(
                IllegalArgumentException.class,
                () -> history.reconcile(authoritative, collisions, 2L));
    }

    @Test
    void rejectsInvalidSequenceStepAndSubmissionOrder()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        PreparationPlayerState player = player();
        PreparationCollisionWorld collisions = collisions(player);
        PreparationPredictionHistory history = new PreparationPredictionHistory();

        assertThrows(
                IllegalArgumentException.class,
                () -> history.predict(player, collisions, 2L, 0.0d, 0.0d, 0.01d));
        assertThrows(
                IllegalArgumentException.class,
                () -> history.predict(player, collisions, 1L, 2.0d, 0.0d, 0.01d));
        assertThrows(
                IllegalArgumentException.class,
                () -> history.predict(player, collisions, 1L, 0.0d, 0.0d, 0.11d));
        assertThrows(IllegalArgumentException.class, () -> history.markSubmitted(2L));
    }

    @Test
    void failsClosedAtTheBoundedHistoryLimit()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        PreparationPlayerState player = player();
        PreparationCollisionWorld collisions = collisions(player);
        PreparationPredictionHistory history = new PreparationPredictionHistory(2);
        player = history.predict(player, collisions, 1L, 1.0d, 0.0d, 0.01d);
        player = history.predict(player, collisions, 1L, 1.0d, 0.0d, 0.01d);
        PreparationPlayerState current = player;

        assertThrows(
                IllegalStateException.class,
                () -> history.predict(current, collisions, 1L, 1.0d, 0.0d, 0.01d));
        assertThat(history.pendingStepCount()).isEqualTo(2);
    }

    @Test
    void replayUsesTheCorrectedServerPositionAsItsBase()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        PreparationPlayerState spawn = player();
        PreparationCollisionWorld collisions = collisions(spawn);
        PreparationPredictionHistory history = new PreparationPredictionHistory();
        PreparationPlayerState predicted =
                history.predict(spawn, collisions, 1L, 1.0d, 0.0d, 0.05d);
        history.markSubmitted(1L);
        history.predict(predicted, collisions, 2L, 1.0d, 0.0d, 0.05d);
        PreparationPlayerState corrected =
                spawn.withAuthoritativeState(-17.0d, 0.5d, -17.0d, 45.0d, 0.0d);

        PreparationPlayerState reconciled = history.reconcile(corrected, collisions, 1L);

        assertThat(reconciled.position().x()).isCloseTo(-16.8232233d, within(0.000001d));
        assertThat(reconciled.position().z()).isCloseTo(-16.8232233d, within(0.000001d));
    }

    private static PreparationPlayerState player() throws PreparationSceneLoadException {
        return PreparationPlayerState.atAuthoritativeSpawn(verifiedScene());
    }

    private static PreparationCollisionWorld collisions(PreparationPlayerState player)
            throws PreparationSceneGraphException {
        return PreparationCollisionWorld.load(new DesktopAssetManager(true), player.scene());
    }

    private static VerifiedPreparationScene verifiedScene() throws PreparationSceneLoadException {
        byte[] digest = HexFormat.of().parseHex(MinimalPreparationBundle.EXPECTED_ARCHIVE_SHA256);
        PreparationSpawnAssignment assignment =
                new PreparationSpawnAssignment(
                        8L,
                        1L,
                        MinimalPreparationBundle.MAP_ID,
                        digest,
                        LobbyTeam.GREEN,
                        0,
                        -15.0d,
                        0.5d,
                        -14.0d,
                        45.0d);
        return PreparationSceneLoader.loadDefault(assignment);
    }
}
