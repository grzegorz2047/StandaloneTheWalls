from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


path = Path(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/SunderfrontClient.java"
)
text = path.read_text(encoding="utf-8")
text = replace_once(
    text,
    "import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationCameraPlacement;\n",
    "import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationCameraPlacement;\n"
    "import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationCollisionWorld;\n",
    "collision world import",
)
text = replace_once(
    text,
    "    private PreparationPlayerState preparationPlayerState;\n"
    "    private Node preparationWorld;\n",
    "    private PreparationPlayerState preparationPlayerState;\n"
    "    private PreparationCollisionWorld preparationCollisionWorld;\n"
    "    private Node preparationWorld;\n",
    "collision world field",
)
text = replace_once(
    text,
    """        Node loadedWorld = null;
        if (!smokeMode) {
            try {
                loadedWorld = PreparationSceneGraphLoader.load(assetManager, player.scene());
            } catch (PreparationSceneGraphException exception) {
                failPreparationSceneEntry();
                return;
            }
        }
""",
    """        Node loadedWorld = null;
        PreparationCollisionWorld loadedCollisions = null;
        if (!smokeMode) {
            try {
                loadedWorld = PreparationSceneGraphLoader.load(assetManager, player.scene());
                loadedCollisions = PreparationCollisionWorld.load(assetManager, player.scene());
            } catch (PreparationSceneGraphException exception) {
                failPreparationSceneEntry();
                return;
            }
        }
""",
    "atomic visual and collision load",
)
text = replace_once(
    text,
    """        if (!smokeMode) {
            preparationWorld = loadedWorld;
            rootNode.attachChild(loadedWorld);
            PreparationCameraPlacement.apply(cam, player);
        }
""",
    """        if (!smokeMode) {
            preparationWorld = loadedWorld;
            preparationCollisionWorld = loadedCollisions;
            rootNode.attachChild(loadedWorld);
            PreparationCameraPlacement.apply(cam, player);
        }
""",
    "store invisible collision world",
)
text = replace_once(
    text,
    """    private void detachPreparationWorld() {
        Node current = preparationWorld;
        preparationWorld = null;
""",
    """    private void detachPreparationWorld() {
        preparationCollisionWorld = null;
        Node current = preparationWorld;
        preparationWorld = null;
""",
    "collision world cleanup",
)
path.write_text(text, encoding="utf-8")
