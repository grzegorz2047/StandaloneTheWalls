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
    "import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationCollisionWorld;\n"
    "import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationPlayerState;\n",
    "import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationCollisionWorld;\n"
    "import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationInputState;\n"
    "import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationInputState.Direction;\n"
    "import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationMovementController;\n"
    "import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationPlayerState;\n",
    "preparation input imports",
)
text = replace_once(
    text,
    "    private static final String INPUT_BACKSPACE = \"sunderfront-ui-backspace\";\n",
    "    private static final String INPUT_BACKSPACE = \"sunderfront-ui-backspace\";\n"
    "    private static final String INPUT_MOVE_FORWARD = \"sunderfront-move-forward\";\n"
    "    private static final String INPUT_MOVE_BACKWARD = \"sunderfront-move-backward\";\n"
    "    private static final String INPUT_MOVE_LEFT = \"sunderfront-move-left\";\n"
    "    private static final String INPUT_MOVE_RIGHT = \"sunderfront-move-right\";\n",
    "preparation movement mappings",
)
text = replace_once(
    text,
    "    private final UiPointerRouter pointerRouter = new UiPointerRouter();\n\n"
    "    private PreparationTransitionGate preparationTransitionGate = new PreparationTransitionGate();\n",
    "    private final UiPointerRouter pointerRouter = new UiPointerRouter();\n"
    "    private final PreparationInputState preparationInput = new PreparationInputState();\n\n"
    "    private PreparationTransitionGate preparationTransitionGate = new PreparationTransitionGate();\n",
    "preparation input field",
)
text = replace_once(
    text,
    """        if ((screen == Screen.DIRECT_CONNECT || screen == Screen.PREPARATION)
                && directConnectController != null) {
            directConnectController.refreshConnectedSnapshot();
            if (screen == Screen.DIRECT_CONNECT) {
                enterPreparationIfReady();
            }
        }
        if (!smokeMode && (renderedWidth != cam.getWidth() || renderedHeight != cam.getHeight())) {
""",
    """        if ((screen == Screen.DIRECT_CONNECT || screen == Screen.PREPARATION)
                && directConnectController != null) {
            directConnectController.refreshConnectedSnapshot();
            if (screen == Screen.DIRECT_CONNECT) {
                enterPreparationIfReady();
            }
        }
        if (screen == Screen.PREPARATION && !smokeMode) {
            updatePreparationMovement(timePerFrame);
        }
        if (!smokeMode && (renderedWidth != cam.getWidth() || renderedHeight != cam.getHeight())) {
""",
    "preparation movement update",
)
text = replace_once(
    text,
    """    public void onAction(String name, boolean isPressed, float timePerFrame) {
        if (!isPressed || smokeMode) {
            return;
        }
        switch (screen) {
            case START_MENU -> handleStartMenuAction(name);
            case DIRECT_CONNECT -> handleDirectConnectAction(name);
            case PREPARATION -> handlePreparationAction(name);
        }
    }
""",
    """    public void onAction(String name, boolean isPressed, float timePerFrame) {
        if (smokeMode) {
            return;
        }
        if (screen == Screen.PREPARATION) {
            handlePreparationAction(name, isPressed);
            return;
        }
        if (!isPressed) {
            return;
        }
        switch (screen) {
            case START_MENU -> handleStartMenuAction(name);
            case DIRECT_CONNECT -> handleDirectConnectAction(name);
            case PREPARATION -> throw new IllegalStateException("preparation input was not routed");
        }
    }
""",
    "preparation press and release routing",
)
text = replace_once(
    text,
    """    public void onMouseMotionEvent(MouseMotionEvent event) {
        if (!smokeMode) {
            handlePointerMotion(event.getX(), event.getY());
        }
    }

    @Override
    public void onMouseButtonEvent(MouseButtonEvent event) {
        if (!smokeMode) {
            handlePointerButton(
                    event.getButtonIndex(), event.isPressed(), event.getX(), event.getY());
        }
    }
""",
    """    public void onMouseMotionEvent(MouseMotionEvent event) {
        if (smokeMode) {
            return;
        }
        if (screen == Screen.PREPARATION) {
            if (preparationInput.captured() && event.getDX() != 0) {
                rotatePreparation(event.getDX());
            }
            return;
        }
        handlePointerMotion(event.getX(), event.getY());
    }

    @Override
    public void onMouseButtonEvent(MouseButtonEvent event) {
        if (smokeMode) {
            return;
        }
        if (screen == Screen.PREPARATION) {
            if (event.getButtonIndex() == UiPointerRouter.PRIMARY_BUTTON && event.isPressed()) {
                capturePreparationInput();
            }
            return;
        }
        handlePointerButton(
                event.getButtonIndex(), event.isPressed(), event.getX(), event.getY());
    }
""",
    "preparation mouse routing",
)
text = replace_once(
    text,
    """    Optional<PreparationCollisionWorld> currentPreparationCollisionWorld() {
        return Optional.ofNullable(preparationCollisionWorld);
    }

    private void registerInputs() {
""",
    """    Optional<PreparationCollisionWorld> currentPreparationCollisionWorld() {
        return Optional.ofNullable(preparationCollisionWorld);
    }

    boolean isPreparationInputCaptured() {
        return preparationInput.captured();
    }

    void exercisePreparationInputCapture() {
        if (!smokeMode || screen != Screen.PREPARATION) {
            throw new IllegalStateException(
                    "preparation input capture exercise requires smoke preparation mode");
        }
        preparationInput.capture();
    }

    void exercisePreparationInputRelease() {
        if (!smokeMode || screen != Screen.PREPARATION) {
            throw new IllegalStateException(
                    "preparation input release exercise requires smoke preparation mode");
        }
        preparationInput.release();
    }

    private void registerInputs() {
""",
    "preparation capture smoke seam",
)
text = replace_once(
    text,
    """        inputManager.addMapping(INPUT_BACK, new KeyTrigger(KeyInput.KEY_ESCAPE));
        inputManager.addMapping(INPUT_BACKSPACE, new KeyTrigger(KeyInput.KEY_BACK));
        inputManager.addListener(
""",
    """        inputManager.addMapping(INPUT_BACK, new KeyTrigger(KeyInput.KEY_ESCAPE));
        inputManager.addMapping(INPUT_BACKSPACE, new KeyTrigger(KeyInput.KEY_BACK));
        inputManager.addMapping(INPUT_MOVE_FORWARD, new KeyTrigger(KeyInput.KEY_W));
        inputManager.addMapping(INPUT_MOVE_BACKWARD, new KeyTrigger(KeyInput.KEY_S));
        inputManager.addMapping(INPUT_MOVE_LEFT, new KeyTrigger(KeyInput.KEY_A));
        inputManager.addMapping(INPUT_MOVE_RIGHT, new KeyTrigger(KeyInput.KEY_D));
        inputManager.addListener(
""",
    "register preparation mappings",
)
text = replace_once(
    text,
    """                INPUT_SELECT,
                INPUT_BACK,
                INPUT_BACKSPACE);
""",
    """                INPUT_SELECT,
                INPUT_BACK,
                INPUT_BACKSPACE,
                INPUT_MOVE_FORWARD,
                INPUT_MOVE_BACKWARD,
                INPUT_MOVE_LEFT,
                INPUT_MOVE_RIGHT);
""",
    "listen for preparation mappings",
)
text = replace_once(
    text,
    """    private void handlePreparationAction(String name) {
        if (INPUT_BACK.equals(name)) {
            returnToStartMenu();
        }
    }

    private void handlePointerMotion(float x, float y) {
""",
    """    private void handlePreparationAction(String name, boolean pressed) {
        switch (name) {
            case INPUT_MOVE_FORWARD -> preparationInput.set(Direction.FORWARD, pressed);
            case INPUT_MOVE_BACKWARD -> preparationInput.set(Direction.BACKWARD, pressed);
            case INPUT_MOVE_LEFT -> preparationInput.set(Direction.LEFT, pressed);
            case INPUT_MOVE_RIGHT -> preparationInput.set(Direction.RIGHT, pressed);
            case INPUT_SELECT -> {
                if (pressed) {
                    capturePreparationInput();
                }
            }
            case INPUT_BACK -> {
                if (!pressed) {
                    return;
                }
                if (preparationInput.captured()) {
                    releasePreparationInput();
                } else {
                    returnToStartMenu();
                }
            }
            default -> {
                // InputManager invokes this listener only for registered mappings.
            }
        }
    }

    private void capturePreparationInput() {
        if (screen != Screen.PREPARATION || !preparationInput.capture()) {
            return;
        }
        if (inputManager != null) {
            inputManager.setCursorVisible(false);
        }
        renderCurrentScreen();
    }

    private void releasePreparationInput() {
        if (!preparationInput.release()) {
            return;
        }
        if (inputManager != null) {
            inputManager.setCursorVisible(true);
        }
        renderCurrentScreen();
    }

    private void updatePreparationMovement(float timePerFrame) {
        if (!preparationInput.captured()) {
            return;
        }
        PreparationPlayerState current = preparationPlayerState;
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
    }

    private void rotatePreparation(double horizontalMousePixels) {
        PreparationPlayerState current = preparationPlayerState;
        if (current == null) {
            failPreparationSceneEntry();
            return;
        }
        PreparationPlayerState rotated =
                PreparationMovementController.rotate(current, horizontalMousePixels);
        if (rotated == current) {
            return;
        }
        preparationPlayerState = rotated;
        PreparationCameraPlacement.apply(cam, rotated);
    }

    private void handlePointerMotion(float x, float y) {
""",
    "preparation controls",
)
text = replace_once(
    text,
    """        preparationPlayerState = player;
        screen = Screen.PREPARATION;
        pointerRouter.replaceHitMap(UiHitMap.empty());
        if (!smokeMode) {
""",
    """        preparationPlayerState = player;
        preparationInput.release();
        screen = Screen.PREPARATION;
        pointerRouter.replaceHitMap(UiHitMap.empty());
        if (!smokeMode) {
            inputManager.setCursorVisible(true);
""",
    "preparation starts uncaptured",
)
text = replace_once(
    text,
    """    private void detachPreparationWorld() {
        preparationCollisionWorld = null;
""",
    """    private void detachPreparationWorld() {
        preparationInput.release();
        preparationCollisionWorld = null;
""",
    "clear preparation input during teardown",
)
text = replace_once(
    text,
    """        if (screen == Screen.START_MENU) {
            renderStartMenu(targets);
        } else if (screen == Screen.DIRECT_CONNECT && directConnectModel != null) {
            renderDirectConnect(directConnectModel, targets);
        }
""",
    """        if (screen == Screen.START_MENU) {
            renderStartMenu(targets);
        } else if (screen == Screen.DIRECT_CONNECT && directConnectModel != null) {
            renderDirectConnect(directConnectModel, targets);
        } else if (screen == Screen.PREPARATION) {
            renderPreparationHud();
        }
""",
    "render preparation HUD",
)
text = replace_once(
    text,
    "    private void renderStartMenu(List<UiHitTarget> targets) {",
    """    private void renderPreparationHud() {
        String message =
                messages.text(
                        preparationInput.captured()
                                ? "preparation.controls.captured"
                                : "preparation.controls.capture");
        addCenteredText(message, 17f, MUTED_TEXT, 42f);
        if (preparationInput.captured()) {
            addCenteredText("+", 24f, PRIMARY_TEXT, cam.getHeight() / 2.0f);
        }
    }

    private void renderStartMenu(List<UiHitTarget> targets) {""",
    "preparation HUD method",
)
path.write_text(text, encoding="utf-8")

for resource, marker, addition in (
    (
        Path("client/src/main/resources/i18n/messages_en.properties"),
        "preparation.scene_load_failed=The verified preparation scene could not be loaded. The connection was closed.\n",
        "preparation.controls.capture=Click or press Enter to capture controls. Esc disconnects.\n"
        "preparation.controls.captured=WASD moves. Mouse turns. Esc releases the cursor.\n",
    ),
    (
        Path("client/src/main/resources/i18n/messages_pl.properties"),
        "preparation.scene_load_failed=Nie udalo sie zaladowac zweryfikowanej sceny przygotowania. Polaczenie zostalo zamkniete.\n",
        "preparation.controls.capture=Kliknij lub nacisnij Enter, aby przejac sterowanie. Esc rozlacza.\n"
        "preparation.controls.captured=WASD porusza. Mysz obraca. Esc zwalnia kursor.\n",
    ),
):
    content = resource.read_text(encoding="utf-8")
    resource.write_text(
        replace_once(content, marker, marker + addition, str(resource)), encoding="utf-8"
    )

transition_test = Path(
    "client/src/test/java/pl/grzegorz2047/standalonethewalls/client/SunderfrontPreparationTransitionTest.java"
)
test = transition_test.read_text(encoding="utf-8")
test = replace_once(
    test,
    """        assertThat(player.yawDegrees()).isEqualTo(45.0d);
    }
""",
    """        assertThat(player.yawDegrees()).isEqualTo(45.0d);
        assertThat(client.isPreparationInputCaptured()).isFalse();

        client.exercisePreparationInputCapture();
        assertThat(client.isPreparationInputCaptured()).isTrue();
        client.exercisePreparationInputRelease();
        assertThat(client.isPreparationInputCaptured()).isFalse();
    }
""",
    "preparation capture lifecycle test",
)
transition_test.write_text(test, encoding="utf-8")
