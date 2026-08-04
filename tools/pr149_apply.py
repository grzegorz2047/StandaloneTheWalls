from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


controller_path = Path(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/ui/directconnect/DirectConnectUiController.java"
)
controller = controller_path.read_text(encoding="utf-8")
controller = replace_once(
    controller,
    "import pl.grzegorz2047.standalonethewalls.client.i18n.ClientMessages;\n",
    "import pl.grzegorz2047.standalonethewalls.client.i18n.ClientMessages;\n"
    "import pl.grzegorz2047.standalonethewalls.client.preparation.VerifiedPreparationScene;\n",
    "controller preparation scene import",
)
controller = replace_once(
    controller,
    "    @Override\n    public void close() {",
    """    /** Returns the session-owned scene only after all preparation checks succeeded. */
    public Optional<VerifiedPreparationScene> currentVerifiedPreparationScene() {
        requireOpen();
        ConnectedLobbySession session = connectedSession;
        if (session == null || model.phase() != DirectConnectUiPhase.CONNECTED) {
            return Optional.empty();
        }
        return session.currentVerifiedPreparationScene();
    }

    @Override
    public void close() {""",
    "controller verified scene accessor",
)
controller_path.write_text(controller, encoding="utf-8")

client_path = Path(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/SunderfrontClient.java"
)
client = client_path.read_text(encoding="utf-8")
client = replace_once(
    client,
    "import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectService;\n",
    "import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectService;\n"
    "import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationPlayerState;\n"
    "import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationTransitionGate;\n"
    "import pl.grzegorz2047.standalonethewalls.client.preparation.VerifiedPreparationScene;\n",
    "client preparation imports",
)
client = replace_once(
    client,
    "    private final UiPointerRouter pointerRouter = new UiPointerRouter();\n\n"
    "    private StartMenuModel menu;",
    "    private final UiPointerRouter pointerRouter = new UiPointerRouter();\n\n"
    "    private PreparationTransitionGate preparationTransitionGate =\n"
    "            new PreparationTransitionGate();\n"
    "    private StartMenuModel menu;",
    "client transition gate field",
)
client = replace_once(
    client,
    "    private DirectConnectUiController directConnectController;\n"
    "    private DirectConnectScreenModel directConnectModel;\n",
    "    private DirectConnectUiController directConnectController;\n"
    "    private DirectConnectScreenModel directConnectModel;\n"
    "    private PreparationPlayerState preparationPlayerState;\n",
    "client preparation state field",
)
client = replace_once(
    client,
    """        if (screen == Screen.DIRECT_CONNECT && directConnectController != null) {
            directConnectController.refreshConnectedSnapshot();
        }
""",
    """        if ((screen == Screen.DIRECT_CONNECT || screen == Screen.PREPARATION)
                && directConnectController != null) {
            directConnectController.refreshConnectedSnapshot();
            if (screen == Screen.DIRECT_CONNECT) {
                enterPreparationIfReady();
            }
        }
""",
    "client update transition",
)
client = replace_once(
    client,
    """        if (screen == Screen.START_MENU) {
            handleStartMenuAction(name);
        } else {
            handleDirectConnectAction(name);
        }
""",
    """        switch (screen) {
            case START_MENU -> handleStartMenuAction(name);
            case DIRECT_CONNECT -> handleDirectConnectAction(name);
            case PREPARATION -> handlePreparationAction(name);
        }
""",
    "client input routing",
)
client = replace_once(
    client,
    "    private void registerInputs() {",
    """    void exercisePreparationTransition(VerifiedPreparationScene scene) {
        if (!smokeMode) {
            throw new IllegalStateException("preparation transition exercise requires smoke mode");
        }
        if (screen == Screen.START_MENU) {
            screen = Screen.DIRECT_CONNECT;
        }
        preparationTransitionGate
                .poll(Optional.of(Objects.requireNonNull(scene, "scene")))
                .ifPresent(this::enterPreparation);
    }

    boolean isPreparationActive() {
        return screen == Screen.PREPARATION;
    }

    Optional<PreparationPlayerState> currentPreparationPlayerState() {
        return Optional.ofNullable(preparationPlayerState);
    }

    private void registerInputs() {""",
    "client smoke transition seam",
)
client = replace_once(
    client,
    """    private void handleDirectConnectAction(String name) {
        if (directConnectController == null) {
            return;
        }
        switch (name) {
            case INPUT_UP, INPUT_LEFT -> directConnectController.moveFocus(-1);
            case INPUT_DOWN, INPUT_RIGHT, INPUT_NEXT -> directConnectController.moveFocus(1);
            case INPUT_SELECT -> directConnectController.activate();
            case INPUT_BACK -> directConnectController.escape();
            case INPUT_BACKSPACE -> directConnectController.backspace();
            default -> {
                // InputManager invokes this listener only for registered mappings.
            }
        }
    }
""",
    """    private void handleDirectConnectAction(String name) {
        if (directConnectController == null) {
            return;
        }
        switch (name) {
            case INPUT_UP, INPUT_LEFT -> directConnectController.moveFocus(-1);
            case INPUT_DOWN, INPUT_RIGHT, INPUT_NEXT -> directConnectController.moveFocus(1);
            case INPUT_SELECT -> directConnectController.activate();
            case INPUT_BACK -> directConnectController.escape();
            case INPUT_BACKSPACE -> directConnectController.backspace();
            default -> {
                // InputManager invokes this listener only for registered mappings.
            }
        }
    }

    private void handlePreparationAction(String name) {
        if (INPUT_BACK.equals(name)) {
            returnToStartMenu();
        }
    }
""",
    "client preparation input handler",
)
client = replace_once(
    client,
    """    private void openDirectConnectScreen() {
        closeDirectConnectController();
        screen = Screen.DIRECT_CONNECT;
        menuStatus = "";
""",
    """    private void openDirectConnectScreen() {
        closeDirectConnectController();
        preparationTransitionGate = new PreparationTransitionGate();
        preparationPlayerState = null;
        screen = Screen.DIRECT_CONNECT;
        menuStatus = "";
""",
    "client connection reset",
)
client = replace_once(
    client,
    "    private void acceptDirectConnectModel(DirectConnectScreenModel next) {",
    """    private void enterPreparationIfReady() {
        DirectConnectUiController controller = directConnectController;
        if (controller == null) {
            return;
        }
        preparationTransitionGate
                .poll(controller.currentVerifiedPreparationScene())
                .ifPresent(this::enterPreparation);
    }

    private void enterPreparation(PreparationPlayerState entered) {
        if (screen != Screen.DIRECT_CONNECT) {
            return;
        }
        preparationPlayerState = Objects.requireNonNull(entered, "entered");
        screen = Screen.PREPARATION;
        pointerRouter.replaceHitMap(UiHitMap.empty());
        renderCurrentScreen();
    }

    private void acceptDirectConnectModel(DirectConnectScreenModel next) {""",
    "client preparation entry methods",
)
client = replace_once(
    client,
    """    private void returnToStartMenu() {
        screen = Screen.START_MENU;
        menuStatus = "";
        closeDirectConnectController();
        renderCurrentScreen();
    }
""",
    """    private void returnToStartMenu() {
        screen = Screen.START_MENU;
        menuStatus = "";
        preparationTransitionGate = new PreparationTransitionGate();
        preparationPlayerState = null;
        if (!smokeMode && inputManager != null) {
            inputManager.setCursorVisible(true);
        }
        closeDirectConnectController();
        renderCurrentScreen();
    }
""",
    "client return reset",
)
client = replace_once(
    client,
    """        if (screen == Screen.START_MENU) {
            renderStartMenu(targets);
        } else if (directConnectModel != null) {
            renderDirectConnect(directConnectModel, targets);
        }
""",
    """        if (screen == Screen.START_MENU) {
            renderStartMenu(targets);
        } else if (screen == Screen.DIRECT_CONNECT && directConnectModel != null) {
            renderDirectConnect(directConnectModel, targets);
        }
""",
    "client render routing",
)
client = replace_once(
    client,
    """    private enum Screen {
        START_MENU,
        DIRECT_CONNECT
    }
""",
    """    private enum Screen {
        START_MENU,
        DIRECT_CONNECT,
        PREPARATION
    }
""",
    "client preparation screen enum",
)
client_path.write_text(client, encoding="utf-8")

controller_test_path = Path(
    "client/src/test/java/pl/grzegorz2047/standalonethewalls/client/ui/directconnect/DirectConnectUiControllerTest.java"
)
controller_test = controller_test_path.read_text(encoding="utf-8")
controller_test = replace_once(
    controller_test,
    "import java.util.ArrayList;\nimport java.util.List;\n",
    "import java.util.ArrayList;\nimport java.util.HexFormat;\nimport java.util.List;\n",
    "controller test hex import",
)
controller_test = replace_once(
    controller_test,
    "import pl.grzegorz2047.standalonethewalls.client.network.FirstUseConfirmation;\n",
    "import pl.grzegorz2047.standalonethewalls.client.network.FirstUseConfirmation;\n"
    "import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;\n",
    "controller test map import",
)
controller_test = replace_once(
    controller_test,
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;\n",
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCountdownCancellationReason;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhase;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhaseSnapshot;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;\n",
    "controller test protocol imports",
)
controller_test = replace_once(
    controller_test,
    """    @Test
    void escapeFromFirstUseConfirmationDiscardsTrustWithoutReconnect()
""",
    """    @Test
    void exposesTheVerifiedPreparationSceneAfterTheAuthoritativeAssignment()
            throws DirectConnectEndpointException, InterruptedException {
        FakeBackend backend = new FakeBackend();
        DirectConnectUiController controller = controller(backend);
        controller.open();
        startConnection(controller);
        DirectConnectUiTestFixtures.ControlledLobby lobby =
                DirectConnectUiTestFixtures.controlledLobby();
        backend.connectAttempts
                .getFirst()
                .complete(
                        lobby.connectedResult(
                                PlayerSessionAdmissionStatus.LOCAL_FIRST_USE_ACCEPTED));

        assertTrue(controller.currentVerifiedPreparationScene().isEmpty());
        LobbySnapshot roster =
                DirectConnectUiTestFixtures.snapshot(
                        2L, LobbyTeam.GREEN, true, LobbyTeam.BLUE, true);
        lobby.deliverSnapshot(roster, 2L);
        lobby.deliverMatchSnapshot(
                new LobbyMatchPhaseSnapshot(
                        2L,
                        roster.revision(),
                        10L,
                        LobbyMatchPhase.PREPARATION,
                        100L,
                        roster.members().size(),
                        1L,
                        LobbyCountdownCancellationReason.NONE),
                3L);
        byte[] digest =
                HexFormat.of().parseHex(MinimalPreparationBundle.EXPECTED_ARCHIVE_SHA256);
        lobby.deliverPreparationSpawnAssignment(
                new PreparationSpawnAssignment(
                        roster.revision(),
                        1L,
                        MinimalPreparationBundle.MAP_ID,
                        digest,
                        LobbyTeam.GREEN,
                        0,
                        -15.0d,
                        0.5d,
                        -14.0d,
                        45.0d),
                4L);

        waitUntil(() -> controller.currentVerifiedPreparationScene().isPresent());
        assertEquals(
                MinimalPreparationBundle.MAP_ID,
                controller.currentVerifiedPreparationScene().orElseThrow().mapId());
        assertEquals(
                0,
                controller.currentVerifiedPreparationScene().orElseThrow().spawn().index());
        controller.close();
    }

    @Test
    void escapeFromFirstUseConfirmationDiscardsTrustWithoutReconnect()
""",
    "controller verified scene test",
)
controller_test_path.write_text(controller_test, encoding="utf-8")
