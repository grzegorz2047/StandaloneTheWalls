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
    """import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationRemotePlayerRenderer;
import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationSceneGraphException;
""",
    """import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationRemotePlayerRenderer;
import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationRemoteSnapshotInterpolator;
import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationSceneGraphException;
""",
)

replace_once(
    path,
    """    private PreparationCollisionWorld preparationCollisionWorld;
    private PreparationRemotePlayerRenderer preparationRemotePlayers;
    private Node preparationWorld;
""",
    """    private PreparationCollisionWorld preparationCollisionWorld;
    private PreparationRemotePlayerRenderer preparationRemotePlayers;
    private PreparationRemoteSnapshotInterpolator preparationRemoteInterpolator;
    private Node preparationWorld;
""",
)

replace_once(
    path,
    """        if (screen == Screen.PREPARATION && !smokeMode) {
            applyPreparationSnapshot();
            updatePreparationMovement(timePerFrame);
            submitPreparationInput(timePerFrame);
        }
""",
    """        if (screen == Screen.PREPARATION && !smokeMode) {
            applyPreparationSnapshot();
            updatePreparationRemotePlayers(timePerFrame);
            updatePreparationMovement(timePerFrame);
            submitPreparationInput(timePerFrame);
        }
""",
)

replace_once(
    path,
    """        PlayerId localPlayerId = preparationPlayerId;
        PreparationRemotePlayerRenderer remotePlayers = preparationRemotePlayers;
        if (controller == null
                || current == null
                || localPlayerId == null
                || remotePlayers == null) {
""",
    """        PlayerId localPlayerId = preparationPlayerId;
        PreparationRemoteSnapshotInterpolator remoteInterpolator = preparationRemoteInterpolator;
        if (controller == null
                || current == null
                || localPlayerId == null
                || remoteInterpolator == null) {
""",
)

replace_once(
    path,
    """            PreparationCameraPlacement.apply(cam, preparationPlayerState);
            remotePlayers.apply(snapshot, localPlayerId);
            appliedPreparationSnapshotTick = snapshot.authoritativeTick();
""",
    """            PreparationCameraPlacement.apply(cam, preparationPlayerState);
            remoteInterpolator.offer(snapshot);
            appliedPreparationSnapshotTick = snapshot.authoritativeTick();
""",
)

replace_once(
    path,
    """    private void submitPreparationInput(float timePerFrame) {
""",
    """    private void updatePreparationRemotePlayers(float timePerFrame) {
        PreparationRemotePlayerRenderer remotePlayers = preparationRemotePlayers;
        PreparationRemoteSnapshotInterpolator remoteInterpolator = preparationRemoteInterpolator;
        if (remotePlayers == null
                || remoteInterpolator == null
                || !Float.isFinite(timePerFrame)
                || timePerFrame < 0.0f) {
            failPreparationSceneEntry();
            return;
        }
        try {
            remotePlayers.apply(remoteInterpolator.advance(timePerFrame));
        } catch (IllegalArgumentException exception) {
            failPreparationSceneEntry();
        }
    }

    private void submitPreparationInput(float timePerFrame) {
""",
)

replace_once(
    path,
    """            preparationInputAccumulator = 0.0d;
            preparationRemotePlayers = new PreparationRemotePlayerRenderer(assetManager);
            rootNode.attachChild(loadedWorld);
""",
    """            preparationInputAccumulator = 0.0d;
            preparationRemoteInterpolator =
                    new PreparationRemoteSnapshotInterpolator(
                            preparationRoundNumber, preparationPlayerId);
            preparationRemotePlayers = new PreparationRemotePlayerRenderer(assetManager);
            rootNode.attachChild(loadedWorld);
""",
)

replace_once(
    path,
    """        preparationPlayerId = null;
        preparationRoundNumber = 0L;
""",
    """        preparationPlayerId = null;
        preparationRemoteInterpolator = null;
        preparationRoundNumber = 0L;
""",
)
