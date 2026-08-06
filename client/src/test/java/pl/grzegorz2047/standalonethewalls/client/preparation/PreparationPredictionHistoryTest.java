package pl.grzegorz2047.standalonethewalls.client.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jme3.asset.DesktopAssetManager;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationObstacleBox;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationObstacleMap;
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
    void replayPreservesCrouchModeForEachUnacknowledgedStep()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        PreparationPlayerState spawn = player();
        PreparationCollisionWorld collisions = collisions(spawn);
        PreparationPredictionHistory history = new PreparationPredictionHistory();
        PreparationPlayerState walking =
                history.predict(spawn, collisions, 1L, 1.0d, 0.0d, false, false, 0.05d);
        history.markSubmitted(1L);
        PreparationPlayerState sprinting =
                history.predict(walking, collisions, 2L, 1.0d, 0.0d, true, false, 0.05d);
        history.markSubmitted(2L);
        history.predict(sprinting, collisions, 3L, 1.0d, 0.0d, false, true, 0.05d);
        PreparationPlayerState authoritative =
                spawn.withAuthoritativeState(-15.5d, 0.5d, -14.5d, 45.0d, 0.0d);

        PreparationPlayerState reconciled = history.reconcile(authoritative, collisions, 1L);
        PreparationPlayerState expectedSprint =
                PreparationMovementController.move(
                        authoritative, collisions, 1.0d, 0.0d, true, false, 0.05d);
        PreparationPlayerState expected =
                PreparationMovementController.move(
                        expectedSprint, collisions, 1.0d, 0.0d, false, true, 0.05d);

        assertThat(reconciled.position()).isEqualTo(expected.position());
        assertThat(history.pendingStepCount()).isEqualTo(2);
        assertThrows(
                IllegalArgumentException.class,
                () -> history.predict(reconciled, collisions, 3L, 0.0d, 0.0d, true, true, 0.01d));
    }

    @Test
    void reconciliationPreservesAuthoritativeCrouchWhenStandingIsStillBlocked()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        VerifiedPreparationScene scene =
                withObstacle(
                        verifiedScene(),
                        new PreparationObstacleBox(
                                "LowCeilingObstacleCollision",
                                new MapVector3(-14.64d, 1.15d, -14.5d),
                                new MapVector3(-13.5d, 1.35d, -13.5d)));
        PreparationPlayerState spawn =
                PreparationPlayerState.atAuthoritativeSpawn(scene)
                        .withAuthoritativeState(
                                -15.0d, 0.5d, -14.0d, 0.0d, true, false, 0.0d, 0.0d);
        PreparationCollisionWorld collisions = collisions(spawn);
        PreparationPredictionHistory history = new PreparationPredictionHistory();

        PreparationPlayerState crouched =
                history.predict(
                        spawn, collisions, 1L, 1.0d, 0.0d, false, true, false, 0.05d);
        history.markSubmitted(1L);
        PreparationPlayerState locallyBlocked =
                history.predict(
                        crouched, collisions, 2L, 0.0d, 0.0d, false, false, false, 0.05d);

        PreparationPlayerState reconciled = history.reconcile(crouched, collisions, 1L);

        assertThat(crouched.crouching()).isTrue();
        assertThat(locallyBlocked.crouching()).isTrue();
        assertThat(reconciled.crouching()).isTrue();
        assertThat(reconciled.position()).isEqualTo(crouched.position());
        assertThat(history.pendingStepCount()).isOne();
    }

    @Test
    void reconciliationReplaysCeilingCollisionFromTheCorrectedServerState()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        VerifiedPreparationScene scene =
                withObstacle(
                        verifiedScene(),
                        new PreparationObstacleBox(
                                "SpawnCeilingObstacleCollision",
                                new MapVector3(-15.5d, 2.0d, -14.5d),
                                new MapVector3(-14.5d, 2.2d, -13.5d)));
        PreparationPlayerState authoritative =
                PreparationPlayerState.atAuthoritativeSpawn(scene)
                        .withAuthoritativeState(
                                -15.0d, 0.5d, -14.0d, 0.0d, true, false, 0.0d, 0.0d);
        PreparationCollisionWorld collisions = collisions(authoritative);
        PreparationPredictionHistory history = new PreparationPredictionHistory();
        history.markSubmitted(1L);

        PreparationPlayerState predicted =
                history.predict(
                        authoritative,
                        collisions,
                        2L,
                        0.0d,
                        0.0d,
                        false,
                        false,
                        true,
                        0.1d);
        PreparationPlayerState reconciled = history.reconcile(authoritative, collisions, 1L);

        assertThat(predicted.position().y()).isEqualTo(0.7d);
        assertThat(predicted.verticalVelocityMetresPerSecond()).isZero();
        assertThat(predicted.grounded()).isFalse();
        assertThat(reconciled.position()).isEqualTo(predicted.position());
        assertThat(reconciled.verticalVelocityMetresPerSecond()).isZero();
        assertThat(reconciled.grounded()).isFalse();
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

    private static VerifiedPreparationScene withObstacle(
            VerifiedPreparationScene scene, PreparationObstacleBox obstacle) {
        List<PreparationObstacleBox> boxes = new ArrayList<>(scene.obstacleMap().boxes());
        boxes.add(obstacle);
        return new VerifiedPreparationScene(
                scene.mapId(),
                scene.mapSha256(),
                scene.sceneGlb(),
                scene.collisionGlb(),
                scene.sceneDocument(),
                scene.collisionDocument(),
                scene.supportMap(),
                new PreparationObstacleMap(boxes),
                scene.region(),
                scene.spawn());
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
