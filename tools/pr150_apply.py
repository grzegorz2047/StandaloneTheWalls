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
    "import com.jme3.math.ColorRGBA;\n",
    "import com.jme3.math.ColorRGBA;\nimport com.jme3.scene.Node;\n",
    "jme node import",
)
text = replace_once(
    text,
    "import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationPlayerState;\n",
    "import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationCameraPlacement;\n"
    "import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationPlayerState;\n"
    "import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationSceneGraphException;\n"
    "import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationSceneGraphLoader;\n",
    "preparation graph imports",
)
text = replace_once(
    text,
    "    private PreparationPlayerState preparationPlayerState;\n",
    "    private PreparationPlayerState preparationPlayerState;\n"
    "    private Node preparationWorld;\n",
    "preparation world field",
)
text = replace_once(
    text,
    """                viewPort.setBackgroundColor(BACKGROUND);
                inputManager.setCursorVisible(true);
                font = assetManager.loadFont("Interface/Fonts/Default.fnt");
""",
    """                viewPort.setBackgroundColor(BACKGROUND);
                inputManager.setCursorVisible(true);
                flyCam.setEnabled(false);
                font = assetManager.loadFont("Interface/Fonts/Default.fnt");
""",
    "disable default fly camera",
)
text = replace_once(
    text,
    """        pointerRouter.replaceHitMap(UiHitMap.empty());
        closeDirectConnectController();
        if (!smokeMode && inputManager != null) {
""",
    """        pointerRouter.replaceHitMap(UiHitMap.empty());
        detachPreparationWorld();
        closeDirectConnectController();
        if (!smokeMode && inputManager != null) {
""",
    "destroy preparation world",
)
text = replace_once(
    text,
    """    private void openDirectConnectScreen() {
        closeDirectConnectController();
        preparationTransitionGate = new PreparationTransitionGate();
""",
    """    private void openDirectConnectScreen() {
        detachPreparationWorld();
        closeDirectConnectController();
        preparationTransitionGate = new PreparationTransitionGate();
""",
    "clear world before connection",
)
text = replace_once(
    text,
    """    private void enterPreparation(PreparationPlayerState entered) {
        if (screen != Screen.DIRECT_CONNECT) {
            return;
        }
        preparationPlayerState = Objects.requireNonNull(entered, "entered");
        screen = Screen.PREPARATION;
        pointerRouter.replaceHitMap(UiHitMap.empty());
        renderCurrentScreen();
    }
""",
    """    private void enterPreparation(PreparationPlayerState entered) {
        if (screen != Screen.DIRECT_CONNECT) {
            return;
        }
        PreparationPlayerState player = Objects.requireNonNull(entered, "entered");
        Node loadedWorld = null;
        if (!smokeMode) {
            try {
                loadedWorld = PreparationSceneGraphLoader.load(assetManager, player.scene());
            } catch (PreparationSceneGraphException exception) {
                failPreparationSceneEntry();
                return;
            }
        }
        preparationPlayerState = player;
        screen = Screen.PREPARATION;
        pointerRouter.replaceHitMap(UiHitMap.empty());
        if (!smokeMode) {
            preparationWorld = loadedWorld;
            rootNode.attachChild(loadedWorld);
            PreparationCameraPlacement.apply(cam, player);
        }
        renderCurrentScreen();
    }

    private void failPreparationSceneEntry() {
        screen = Screen.START_MENU;
        menuStatus = messages.text("preparation.scene_load_failed");
        preparationTransitionGate = new PreparationTransitionGate();
        preparationPlayerState = null;
        detachPreparationWorld();
        if (!smokeMode && inputManager != null) {
            inputManager.setCursorVisible(true);
        }
        closeDirectConnectController();
        renderCurrentScreen();
    }
""",
    "load verified preparation graph",
)
text = replace_once(
    text,
    """        preparationTransitionGate = new PreparationTransitionGate();
        preparationPlayerState = null;
        if (!smokeMode && inputManager != null) {
""",
    """        preparationTransitionGate = new PreparationTransitionGate();
        preparationPlayerState = null;
        detachPreparationWorld();
        if (!smokeMode && inputManager != null) {
""",
    "detach world on menu return",
)
text = replace_once(
    text,
    "    private void closeDirectConnectController() {",
    """    private void detachPreparationWorld() {
        Node current = preparationWorld;
        preparationWorld = null;
        if (current != null) {
            current.removeFromParent();
        }
    }

    private void closeDirectConnectController() {""",
    "world detach helper",
)
path.write_text(text, encoding="utf-8")

for resource, marker, replacement in (
    (
        Path("client/src/main/resources/i18n/messages_en.properties"),
        "menu.help=Use Up/Down and Enter. Esc exits.\n",
        "menu.help=Use Up/Down and Enter. Esc exits.\n"
        "preparation.scene_load_failed=The verified preparation scene could not be loaded. The connection was closed.\n",
    ),
    (
        Path("client/src/main/resources/i18n/messages_pl.properties"),
        "menu.help=Uzyj strzalek gora/dol i Enter. Esc zamyka gre.\n",
        "menu.help=Uzyj strzalek gora/dol i Enter. Esc zamyka gre.\n"
        "preparation.scene_load_failed=Nie udalo sie zaladowac zweryfikowanej sceny przygotowania. Polaczenie zostalo zamkniete.\n",
    ),
):
    content = resource.read_text(encoding="utf-8")
    resource.write_text(
        replace_once(content, marker, replacement, str(resource)), encoding="utf-8"
    )
