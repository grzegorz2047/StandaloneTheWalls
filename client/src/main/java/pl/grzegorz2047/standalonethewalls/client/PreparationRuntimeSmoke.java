package pl.grzegorz2047.standalonethewalls.client;

import com.jme3.app.SimpleApplication;
import com.jme3.app.state.AppState;
import com.jme3.scene.Node;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationCameraPlacement;
import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationCollisionWorld;
import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationInputState;
import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationMovementController;
import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationPlayerState;
import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationSceneGraphException;
import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationSceneGraphLoader;
import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationSceneLoadException;
import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationSceneLoader;
import pl.grzegorz2047.standalonethewalls.client.preparation.VerifiedPreparationScene;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;

/** Bounded preparation application used by packaged-client verification. */
final class PreparationRuntimeSmoke extends SimpleApplication {
    private static final double HORIZONTAL_MOUSE_PIXELS = 24.0d;
    private static final double VERTICAL_MOUSE_PIXELS = 18.0d;
    private static final double MOVEMENT_SECONDS = 0.1d;

    private final CompletableFuture<Void> completed = new CompletableFuture<>();

    PreparationRuntimeSmoke() {
        super(new AppState[0]);
    }

    @Override
    public void simpleInitApp() {
        Node world = null;
        PreparationInputState input = new PreparationInputState();
        try {
            VerifiedPreparationScene scene = loadScene();
            world = PreparationSceneGraphLoader.load(assetManager, scene);
            PreparationCollisionWorld collisions =
                    PreparationCollisionWorld.load(assetManager, scene);
            rootNode.attachChild(world);

            PreparationPlayerState initial =
                    PreparationPlayerState.atAuthoritativeSpawn(scene);
            PreparationCameraPlacement.apply(cam, initial);
            if (!input.capture() || !input.captured()) {
                throw new IllegalStateException("preparation input was not captured");
            }

            PreparationPlayerState rotated =
                    PreparationMovementController.rotate(
                            initial, HORIZONTAL_MOUSE_PIXELS, VERTICAL_MOUSE_PIXELS);
            if (Double.compare(rotated.pitchDegrees(), initial.pitchDegrees()) == 0) {
                throw new IllegalStateException("preparation pitch did not change");
            }
            PreparationCameraPlacement.apply(cam, rotated);

            PreparationPlayerState moved =
                    PreparationMovementController.move(
                            rotated, collisions, 1.0d, 0.0d, MOVEMENT_SECONDS);
            if (moved == rotated || moved.position().equals(rotated.position())) {
                throw new IllegalStateException("preparation player did not move");
            }
            if (!scene.region().contains(moved.position())) {
                throw new IllegalStateException(
                        "preparation movement left the verified team region");
            }
            if (Double.compare(moved.pitchDegrees(), rotated.pitchDegrees()) != 0) {
                throw new IllegalStateException("preparation movement changed camera pitch");
            }
            PreparationCameraPlacement.apply(cam, moved);
            if (!input.release() || input.captured()) {
                throw new IllegalStateException("preparation input remained captured");
            }
            completed.complete(null);
        } catch (PreparationSceneLoadException
                | PreparationSceneGraphException
                | RuntimeException exception) {
            completed.completeExceptionally(exception);
            stop();
        } finally {
            input.release();
            if (world != null) {
                world.removeFromParent();
            }
        }
    }

    void awaitCompletion(Duration timeout) throws InterruptedException, TimeoutException {
        Duration boundedTimeout = Objects.requireNonNull(timeout, "timeout");
        if (boundedTimeout.isZero() || boundedTimeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        try {
            completed.get(boundedTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException exception) {
            throw new IllegalStateException(
                    "packaged preparation smoke failed", exception.getCause());
        }
    }

    private static VerifiedPreparationScene loadScene() throws PreparationSceneLoadException {
        byte[] digest = HexFormat.of().parseHex(MinimalPreparationBundle.EXPECTED_ARCHIVE_SHA256);
        PreparationSpawnAssignment assignment =
                new PreparationSpawnAssignment(
                        1L,
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
