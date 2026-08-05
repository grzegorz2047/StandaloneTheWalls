from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"expected one match in {path}, found {count}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


path = "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/SunderfrontClient.java"

replace_once(
    path,
    """import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationPlayerState;
import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationRemotePlayerRenderer;
""",
    """import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationPlayerState;
import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationPredictionHistory;
import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationRemotePlayerRenderer;
""",
)

replace_once(
    path,
    """    private PreparationPlayerState preparationPlayerState;
    private PreparationCollisionWorld preparationCollisionWorld;
    private PreparationRemotePlayerRenderer preparationRemotePlayers;
""",
    """    private PreparationPlayerState preparationPlayerState;
    private PreparationCollisionWorld preparationCollisionWorld;
    private PreparationPredictionHistory preparationPredictionHistory;
    private PreparationRemotePlayerRenderer preparationRemotePlayers;
""",
)

replace_once(
    path,
    """        PreparationPlayerState current = preparationPlayerState;
        PreparationCollisionWorld collisions = preparationCollisionWorld;
        if (current == null || collisions == null || !Float.isFinite(timePerFrame)) {
            failPreparationSceneEntry();
            return;
        }
        PreparationPlayerState moved =
                PreparationMovementController.move(
                        current,
                        collisions,
                        preparationInput.forwardAxis(),
                        preparationInput.rightAxis(),
                        Math.max(0.0d, timePerFrame));
        if (moved == current) {
            return;
        }
        preparationPlayerState = moved;
        PreparationCameraPlacement.apply(cam, moved);
""",
    """        PreparationPlayerState current = preparationPlayerState;
        PreparationCollisionWorld collisions = preparationCollisionWorld;
        PreparationPredictionHistory predictionHistory = preparationPredictionHistory;
        if (current == null
                || collisions == null
                || predictionHistory == null
                || !Float.isFinite(timePerFrame)
                || timePerFrame < 0.0f) {
            failPreparationSceneEntry();
            return;
        }
        try {
            PreparationPlayerState moved =
                    predictionHistory.predict(
                            current,
                            collisions,
                            nextPreparationInputSequence.get(),
                            preparationInput.forwardAxis(),
                            preparationInput.rightAxis(),
                            Math.min(
                                    timePerFrame,
                                    PreparationMovementController.MAXIMUM_STEP_SECONDS));
            if (moved != current) {
                preparationPlayerState = moved;
                PreparationCameraPlacement.apply(cam, moved);
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            failPreparationSceneEntry();
        }
""",
)

replace_once(
    path,
    """        PlayerId localPlayerId = preparationPlayerId;
        PreparationRemoteSnapshotInterpolator remoteInterpolator = preparationRemoteInterpolator;
        if (controller == null
                || current == null
                || localPlayerId == null
                || remoteInterpolator == null) {
""",
    """        PlayerId localPlayerId = preparationPlayerId;
        PreparationCollisionWorld collisions = preparationCollisionWorld;
        PreparationPredictionHistory predictionHistory = preparationPredictionHistory;
        PreparationRemoteSnapshotInterpolator remoteInterpolator = preparationRemoteInterpolator;
        if (controller == null
                || current == null
                || localPlayerId == null
                || collisions == null
                || predictionHistory == null
                || remoteInterpolator == null) {
""",
)

replace_once(
    path,
    """            preparationPlayerState =
                    current.withAuthoritativeState(
                            authoritative.xMetres(),
                            authoritative.yMetres(),
                            authoritative.zMetres(),
                            authoritative.yawDegrees(),
                            authoritative.pitchDegrees());
            PreparationCameraPlacement.apply(cam, preparationPlayerState);
""",
    """            PreparationPlayerState authoritativeState =
                    current.withAuthoritativeState(
                            authoritative.xMetres(),
                            authoritative.yMetres(),
                            authoritative.zMetres(),
                            authoritative.yawDegrees(),
                            authoritative.pitchDegrees());
            preparationPlayerState =
                    predictionHistory.reconcile(
                            authoritativeState,
                            collisions,
                            authoritative.lastProcessedInputSequence());
            PreparationCameraPlacement.apply(cam, preparationPlayerState);
""",
)

replace_once(
    path,
    """        if (controller.submitPreparationInput(input)) {
            nextPreparationInputSequence.incrementAndGet();
        }
""",
    """        if (controller.submitPreparationInput(input)) {
            PreparationPredictionHistory predictionHistory = preparationPredictionHistory;
            if (predictionHistory == null) {
                failPreparationSceneEntry();
                return;
            }
            try {
                predictionHistory.markSubmitted(inputSequence);
                nextPreparationInputSequence.incrementAndGet();
            } catch (IllegalArgumentException exception) {
                failPreparationSceneEntry();
            }
        }
""",
)

replace_once(
    path,
    """            preparationInputAccumulator = 0.0d;
            preparationRemoteInterpolator =
""",
    """            preparationInputAccumulator = 0.0d;
            preparationPredictionHistory = new PreparationPredictionHistory();
            preparationRemoteInterpolator =
""",
)

replace_once(
    path,
    """        preparationCollisionWorld = null;
        preparationPlayerId = null;
        preparationRemoteInterpolator = null;
""",
    """        preparationCollisionWorld = null;
        preparationPlayerId = null;
        preparationPredictionHistory = null;
        preparationRemoteInterpolator = null;
""",
)
