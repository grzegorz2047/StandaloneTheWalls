package pl.grzegorz2047.standalonethewalls.client;

import com.jme3.app.SimpleApplication;
import com.jme3.app.state.AppState;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.event.JoyAxisEvent;
import com.jme3.input.event.JoyButtonEvent;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.input.event.TouchEvent;
import com.jme3.math.ColorRGBA;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientMessages;
import pl.grzegorz2047.standalonethewalls.client.identity.ClientIdentityStorage;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectService;
import pl.grzegorz2047.standalonethewalls.client.ui.StartMenuAction;
import pl.grzegorz2047.standalonethewalls.client.ui.StartMenuModel;
import pl.grzegorz2047.standalonethewalls.client.ui.directconnect.ConnectedLobbyScreenModel;
import pl.grzegorz2047.standalonethewalls.client.ui.directconnect.DirectConnectScreenModel;
import pl.grzegorz2047.standalonethewalls.client.ui.directconnect.DirectConnectUiController;
import pl.grzegorz2047.standalonethewalls.client.ui.directconnect.DirectConnectUiFocus;
import pl.grzegorz2047.standalonethewalls.client.ui.directconnect.DirectConnectUiPhase;
import pl.grzegorz2047.standalonethewalls.client.ui.lobby.LobbyMemberRowModel;
import pl.grzegorz2047.standalonethewalls.client.ui.lobby.LobbyPanelGeometry;
import pl.grzegorz2047.standalonethewalls.client.ui.lobby.LobbyTeamPanelModel;
import pl.grzegorz2047.standalonethewalls.client.ui.pointer.UiHitMap;
import pl.grzegorz2047.standalonethewalls.client.ui.pointer.UiHitTarget;
import pl.grzegorz2047.standalonethewalls.client.ui.pointer.UiPointerRouter;
import pl.grzegorz2047.standalonethewalls.client.ui.pointer.UiRect;
import pl.grzegorz2047.standalonethewalls.client.ui.pointer.UiTargetId;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;

/** Keyboard-and-pointer jMonkeyEngine shell for the menu and Direct Connect flow. */
public final class SunderfrontClient extends SimpleApplication
        implements ActionListener, RawInputListener {
    private static final String INPUT_UP = "sunderfront-ui-up";
    private static final String INPUT_DOWN = "sunderfront-ui-down";
    private static final String INPUT_LEFT = "sunderfront-ui-left";
    private static final String INPUT_RIGHT = "sunderfront-ui-right";
    private static final String INPUT_NEXT = "sunderfront-ui-next";
    private static final String INPUT_SELECT = "sunderfront-ui-select";
    private static final String INPUT_BACK = "sunderfront-ui-back";
    private static final String INPUT_BACKSPACE = "sunderfront-ui-backspace";

    private static final UiTargetId DIRECT_ENDPOINT_TARGET = new UiTargetId("direct.endpoint");
    private static final UiTargetId DIRECT_HANDLE_TARGET = new UiTargetId("direct.handle");
    private static final UiTargetId DIRECT_TEAM_RED_TARGET = new UiTargetId("direct.team.red");
    private static final UiTargetId DIRECT_TEAM_BLUE_TARGET = new UiTargetId("direct.team.blue");
    private static final UiTargetId DIRECT_TEAM_GREEN_TARGET = new UiTargetId("direct.team.green");
    private static final UiTargetId DIRECT_TEAM_YELLOW_TARGET =
            new UiTargetId("direct.team.yellow");
    private static final UiTargetId DIRECT_READY_TARGET = new UiTargetId("direct.ready");
    private static final UiTargetId DIRECT_PRIMARY_TARGET = new UiTargetId("direct.primary");
    private static final UiTargetId DIRECT_SECONDARY_TARGET = new UiTargetId("direct.secondary");

    private static final ColorRGBA BACKGROUND = new ColorRGBA(0.025f, 0.035f, 0.06f, 1f);
    private static final ColorRGBA PRIMARY_TEXT = new ColorRGBA(0.88f, 0.91f, 0.96f, 1f);
    private static final ColorRGBA MUTED_TEXT = new ColorRGBA(0.58f, 0.65f, 0.76f, 1f);
    private static final ColorRGBA SELECTED_TEXT = new ColorRGBA(0.94f, 0.72f, 0.28f, 1f);
    private static final ColorRGBA SUCCESS_TEXT = new ColorRGBA(0.35f, 0.86f, 0.55f, 1f);
    private static final ColorRGBA WARNING_TEXT = new ColorRGBA(0.98f, 0.66f, 0.22f, 1f);
    private static final ColorRGBA ERROR_TEXT = new ColorRGBA(0.96f, 0.34f, 0.31f, 1f);
    private static final ColorRGBA RED_TEAM = new ColorRGBA(0.96f, 0.35f, 0.35f, 1f);
    private static final ColorRGBA BLUE_TEAM = new ColorRGBA(0.35f, 0.62f, 0.98f, 1f);
    private static final ColorRGBA GREEN_TEAM = new ColorRGBA(0.35f, 0.86f, 0.48f, 1f);
    private static final ColorRGBA YELLOW_TEAM = new ColorRGBA(0.96f, 0.82f, 0.28f, 1f);

    private final ClientMessages messages;
    private final boolean smokeMode;
    private final Path dataDirectory;
    private final CompletableFuture<Void> initialized = new CompletableFuture<>();
    private final UiPointerRouter pointerRouter = new UiPointerRouter();

    private StartMenuModel menu;
    private Screen screen = Screen.START_MENU;
    private String menuStatus = "";
    private BitmapFont font;
    private DirectConnectUiController directConnectController;
    private DirectConnectScreenModel directConnectModel;
    private volatile int renderedWidth = -1;
    private volatile int renderedHeight = -1;
    private volatile boolean shuttingDown;

    public SunderfrontClient(ClientMessages messages, boolean smokeMode) {
        this(messages, smokeMode, defaultDataDirectory());
    }

    SunderfrontClient(ClientMessages messages, boolean smokeMode, Path dataDirectory) {
        super(new AppState[0]);
        this.messages = Objects.requireNonNull(messages, "messages");
        this.smokeMode = smokeMode;
        this.dataDirectory =
                Objects.requireNonNull(dataDirectory, "dataDirectory").toAbsolutePath().normalize();
    }

    @Override
    public void simpleInitApp() {
        try {
            menu = StartMenuModel.create(messages);
            if (!smokeMode) {
                viewPort.setBackgroundColor(BACKGROUND);
                inputManager.setCursorVisible(true);
                font = assetManager.loadFont("Interface/Fonts/Default.fnt");
                registerInputs();
                renderCurrentScreen();
            }
            initialized.complete(null);
        } catch (RuntimeException exception) {
            initialized.completeExceptionally(exception);
            stop();
        }
    }

    @Override
    public void simpleUpdate(float timePerFrame) {
        if (screen == Screen.DIRECT_CONNECT && directConnectController != null) {
            directConnectController.refreshConnectedSnapshot();
        }
        if (!smokeMode && (renderedWidth != cam.getWidth() || renderedHeight != cam.getHeight())) {
            renderCurrentScreen();
        }
    }

    @Override
    public void onAction(String name, boolean isPressed, float timePerFrame) {
        if (!isPressed || smokeMode) {
            return;
        }
        if (screen == Screen.START_MENU) {
            handleStartMenuAction(name);
        } else {
            handleDirectConnectAction(name);
        }
    }

    @Override
    public void onKeyEvent(KeyInputEvent event) {
        if (smokeMode
                || screen != Screen.DIRECT_CONNECT
                || directConnectController == null
                || !event.isPressed()
                || isMappedControlKey(event.getKeyCode())) {
            return;
        }
        char character = event.getKeyChar();
        if (character != 0 && !Character.isISOControl(character)) {
            directConnectController.appendCharacter(character);
        }
    }

    @Override
    public void beginInput() {}

    @Override
    public void endInput() {}

    @Override
    public void onJoyAxisEvent(JoyAxisEvent event) {}

    @Override
    public void onJoyButtonEvent(JoyButtonEvent event) {}

    @Override
    public void onMouseMotionEvent(MouseMotionEvent event) {
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

    @Override
    public void onTouchEvent(TouchEvent event) {}

    @Override
    public void destroy() {
        shuttingDown = true;
        pointerRouter.replaceHitMap(UiHitMap.empty());
        closeDirectConnectController();
        if (!smokeMode && inputManager != null) {
            inputManager.removeListener(this);
            inputManager.removeRawInputListener(this);
        }
        super.destroy();
    }

    public void awaitInitialization(Duration timeout)
            throws InterruptedException, TimeoutException {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        try {
            initialized.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("client initialization failed", exception.getCause());
        }
    }

    void exerciseDirectConnectNavigation(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (!smokeMode) {
            throw new IllegalStateException("navigation smoke exercise requires smoke mode");
        }
        openDirectConnectScreen();
        if (screen != Screen.DIRECT_CONNECT) {
            throw new IllegalStateException("Direct Connect screen did not open in smoke mode");
        }
        directConnectController.escape();
        if (screen != Screen.START_MENU) {
            throw new IllegalStateException(
                    "Direct Connect screen did not return to the start menu");
        }
    }

    void exercisePointerNavigation(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (!smokeMode) {
            throw new IllegalStateException("pointer smoke exercise requires smoke mode");
        }

        pointerRouter.replaceHitMap(
                new UiHitMap(
                        List.of(
                                UiHitTarget.enabled(menuTargetId(0), new UiRect(0f, 0f, 100f, 40f)),
                                UiHitTarget.enabled(
                                        menuTargetId(1), new UiRect(0f, 50f, 100f, 40f)))));
        handlePointerMotion(20f, 70f);
        if (menu.selectedIndex() != 1) {
            throw new IllegalStateException("menu hover did not update selection");
        }
        handlePointerMotion(20f, 20f);
        handlePointerButton(UiPointerRouter.PRIMARY_BUTTON, true, 20f, 20f);
        handlePointerButton(UiPointerRouter.PRIMARY_BUTTON, false, 20f, 20f);
        if (screen != Screen.DIRECT_CONNECT || directConnectController == null) {
            throw new IllegalStateException("pointer did not open Direct Connect");
        }

        pointerRouter.replaceHitMap(
                new UiHitMap(
                        List.of(
                                UiHitTarget.enabled(
                                        DIRECT_ENDPOINT_TARGET, new UiRect(0f, 0f, 100f, 40f)),
                                UiHitTarget.enabled(
                                        DIRECT_SECONDARY_TARGET, new UiRect(0f, 50f, 100f, 40f)))));
        handlePointerMotion(20f, 20f);
        if (directConnectController.model().focus() != DirectConnectUiFocus.ENDPOINT) {
            throw new IllegalStateException("field hover did not update Direct Connect focus");
        }
        handlePointerMotion(20f, 70f);
        if (directConnectController.model().focus() != DirectConnectUiFocus.SECONDARY_ACTION) {
            throw new IllegalStateException("action hover did not update Direct Connect focus");
        }
        handlePointerButton(UiPointerRouter.PRIMARY_BUTTON, true, 20f, 70f);
        handlePointerButton(UiPointerRouter.PRIMARY_BUTTON, false, 20f, 70f);
        if (screen != Screen.START_MENU) {
            throw new IllegalStateException("pointer did not return to the start menu");
        }
    }

    private void registerInputs() {
        inputManager.addMapping(INPUT_UP, new KeyTrigger(KeyInput.KEY_UP));
        inputManager.addMapping(INPUT_DOWN, new KeyTrigger(KeyInput.KEY_DOWN));
        inputManager.addMapping(INPUT_LEFT, new KeyTrigger(KeyInput.KEY_LEFT));
        inputManager.addMapping(INPUT_RIGHT, new KeyTrigger(KeyInput.KEY_RIGHT));
        inputManager.addMapping(INPUT_NEXT, new KeyTrigger(KeyInput.KEY_TAB));
        inputManager.addMapping(INPUT_SELECT, new KeyTrigger(KeyInput.KEY_RETURN));
        inputManager.addMapping(INPUT_BACK, new KeyTrigger(KeyInput.KEY_ESCAPE));
        inputManager.addMapping(INPUT_BACKSPACE, new KeyTrigger(KeyInput.KEY_BACK));
        inputManager.addListener(
                this,
                INPUT_UP,
                INPUT_DOWN,
                INPUT_LEFT,
                INPUT_RIGHT,
                INPUT_NEXT,
                INPUT_SELECT,
                INPUT_BACK,
                INPUT_BACKSPACE);
        inputManager.addRawInputListener(this);
    }

    private void handleStartMenuAction(String name) {
        switch (name) {
            case INPUT_UP, INPUT_LEFT -> {
                menu = menu.move(-1);
                renderCurrentScreen();
            }
            case INPUT_DOWN, INPUT_RIGHT, INPUT_NEXT -> {
                menu = menu.move(1);
                renderCurrentScreen();
            }
            case INPUT_SELECT -> activateSelectedEntry();
            case INPUT_BACK -> stop();
            default -> {
                // InputManager invokes this listener only for registered mappings.
            }
        }
    }

    private void handleDirectConnectAction(String name) {
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

    private void handlePointerMotion(float x, float y) {
        pointerRouter.hover(x, y).ifPresent(this::focusPointerTarget);
    }

    private void handlePointerButton(int buttonIndex, boolean pressed, float x, float y) {
        pointerRouter.button(buttonIndex, pressed, x, y).ifPresent(this::activatePointerTarget);
    }

    private void focusPointerTarget(UiTargetId target) {
        if (screen == Screen.START_MENU) {
            int index = menuIndex(target);
            if (index >= 0 && index != menu.selectedIndex()) {
                menu = menu.select(index);
                renderCurrentScreen();
            }
            return;
        }
        directFocus(target)
                .ifPresent(
                        focus -> {
                            if (directConnectController != null) {
                                directConnectController.focus(focus);
                            }
                        });
    }

    private void activatePointerTarget(UiTargetId target) {
        if (screen == Screen.START_MENU) {
            int index = menuIndex(target);
            if (index < 0) {
                return;
            }
            menu = menu.select(index);
            activateSelectedEntry();
            return;
        }
        Optional<DirectConnectUiFocus> requested = directFocus(target);
        if (requested.isEmpty() || directConnectController == null) {
            return;
        }
        DirectConnectUiFocus focus = requested.orElseThrow();
        if (!directConnectController.focus(focus)) {
            return;
        }
        if (focus != DirectConnectUiFocus.ENDPOINT && focus != DirectConnectUiFocus.HANDLE) {
            directConnectController.activate();
        }
    }

    private void activateSelectedEntry() {
        StartMenuAction action = menu.selectedEntry().action();
        switch (action) {
            case PLAY -> openDirectConnectScreen();
            case SETTINGS -> {
                menuStatus = messages.text("menu.unavailable");
                renderCurrentScreen();
            }
            case EXIT -> stop();
        }
    }

    private void openDirectConnectScreen() {
        closeDirectConnectController();
        screen = Screen.DIRECT_CONNECT;
        menuStatus = "";
        DirectConnectService service =
                new DirectConnectService(new ClientIdentityStorage(dataDirectory));
        directConnectController =
                new DirectConnectUiController(
                        service,
                        messages,
                        this::dispatchToRenderer,
                        this::acceptDirectConnectModel,
                        this::returnToStartMenu);
        directConnectController.open();
    }

    private void acceptDirectConnectModel(DirectConnectScreenModel next) {
        directConnectModel = Objects.requireNonNull(next, "next");
        renderCurrentScreen();
    }

    private void dispatchToRenderer(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (shuttingDown) {
            return;
        }
        enqueue(
                () -> {
                    if (!shuttingDown) {
                        task.run();
                    }
                    return null;
                });
    }

    private void returnToStartMenu() {
        screen = Screen.START_MENU;
        menuStatus = "";
        closeDirectConnectController();
        renderCurrentScreen();
    }

    private void closeDirectConnectController() {
        DirectConnectUiController current = directConnectController;
        directConnectController = null;
        directConnectModel = null;
        if (current != null) {
            current.close();
        }
    }

    private void renderCurrentScreen() {
        if (smokeMode || font == null || cam == null || guiNode == null) {
            return;
        }
        renderedWidth = cam.getWidth();
        renderedHeight = cam.getHeight();
        pointerRouter.replaceHitMap(UiHitMap.empty());
        guiNode.detachAllChildren();
        List<UiHitTarget> targets = new ArrayList<>();
        if (screen == Screen.START_MENU) {
            renderStartMenu(targets);
        } else if (directConnectModel != null) {
            renderDirectConnect(directConnectModel, targets);
        }
        pointerRouter.replaceHitMap(new UiHitMap(targets));
    }

    private void renderStartMenu(List<UiHitTarget> targets) {
        addCenteredText(messages.text("app.title"), 54f, PRIMARY_TEXT, cam.getHeight() - 90f);
        addCenteredText(messages.text("app.subtitle"), 22f, MUTED_TEXT, cam.getHeight() - 140f);

        float menuTop = cam.getHeight() - 260f;
        for (int index = 0; index < menu.entries().size(); index++) {
            boolean selected = index == menu.selectedIndex();
            String label = (selected ? "> " : "  ") + menu.entries().get(index).label();
            BitmapText entry =
                    addText(
                            label,
                            30f,
                            selected ? SELECTED_TEXT : PRIMARY_TEXT,
                            cam.getWidth() * 0.34f,
                            menuTop - (index * 56f));
            targets.add(UiHitTarget.enabled(menuTargetId(index), hitBounds(entry, 18f, 10f, 240f)));
        }
        if (!menuStatus.isBlank()) {
            addCenteredText(menuStatus, 18f, WARNING_TEXT, 105f);
        }
        addCenteredText(messages.text("menu.help"), 17f, MUTED_TEXT, 52f);
    }

    private void renderDirectConnect(DirectConnectScreenModel model, List<UiHitTarget> targets) {
        float width = cam.getWidth();
        float height = cam.getHeight();
        float left = Math.max(42f, width * 0.12f);

        addCenteredText(model.title(), 40f, PRIMARY_TEXT, height - 65f);
        addCenteredText(model.status(), 22f, statusColor(model.phase()), height - 112f);

        if (model.connectedLobby().isPresent()) {
            renderConnectedLobby(
                    model, model.connectedLobby().orElseThrow(), targets, width, height);
        } else {
            renderConnectionDetails(model, targets, left, width, height);
        }
        renderBottomActions(model, targets, width);
        addCenteredText(messages.text("direct.help"), 15f, MUTED_TEXT, 34f);
    }

    private void renderConnectionDetails(
            DirectConnectScreenModel model,
            List<UiHitTarget> targets,
            float left,
            float width,
            float height) {
        BitmapText endpoint =
                addText(
                        fieldLine(
                                messages.text("direct.field.endpoint"),
                                model.endpointText(),
                                model.focus() == DirectConnectUiFocus.ENDPOINT,
                                model.editingEnabled()),
                        22f,
                        model.focus() == DirectConnectUiFocus.ENDPOINT
                                ? SELECTED_TEXT
                                : PRIMARY_TEXT,
                        left,
                        height - 180f);
        targets.add(
                new UiHitTarget(
                        DIRECT_ENDPOINT_TARGET,
                        hitBounds(endpoint, 14f, 8f, 0f),
                        model.editingEnabled()));

        BitmapText handle =
                addText(
                        fieldLine(
                                messages.text("direct.field.handle"),
                                model.handleText(),
                                model.focus() == DirectConnectUiFocus.HANDLE,
                                model.editingEnabled()),
                        22f,
                        model.focus() == DirectConnectUiFocus.HANDLE ? SELECTED_TEXT : PRIMARY_TEXT,
                        left,
                        height - 222f);
        targets.add(
                new UiHitTarget(
                        DIRECT_HANDLE_TARGET,
                        hitBounds(handle, 14f, 8f, 0f),
                        model.editingEnabled()));

        float detailY = height - 278f;
        for (String line : wrap(model.detail(), Math.max(44, (int) (width / 14f)))) {
            addText(line, 17f, MUTED_TEXT, left, detailY);
            detailY -= 23f;
        }

        if (model.fingerprint().isPresent()) {
            addText(
                    messages.text("direct.confirm.fingerprint"),
                    17f,
                    MUTED_TEXT,
                    left,
                    detailY - 10f);
            addText(model.fingerprint().orElseThrow(), 30f, WARNING_TEXT, left, detailY - 48f);
        }
    }

    private void renderConnectedLobby(
            DirectConnectScreenModel screenModel,
            ConnectedLobbyScreenModel connected,
            List<UiHitTarget> targets,
            float width,
            float height) {
        LobbyPanelGeometry geometry = LobbyPanelGeometry.forViewport(width, height);
        for (int index = 0; index < connected.lobby().teamPanels().size(); index++) {
            LobbyTeamPanelModel panel = connected.lobby().teamPanels().get(index);
            UiRect bounds = geometry.panels().get(index);
            renderTeamPanel(screenModel, connected, panel, bounds);
            targets.add(
                    new UiHitTarget(teamTarget(panel.team()), bounds, connected.controlsEnabled()));
        }

        String unassigned =
                connected.lobby().unassignedMembers().isEmpty()
                        ? messages.text("direct.lobby.unassigned.none")
                        : connected.lobby().unassignedMembers().stream()
                                .map(this::memberLine)
                                .collect(Collectors.joining(", "));
        addCenteredText(
                messages.text("direct.lobby.unassigned", unassigned), 14f, MUTED_TEXT, 210f);
        addCenteredText(
                connected.commandStatus(),
                15f,
                connected.commandInFlight() ? WARNING_TEXT : MUTED_TEXT,
                178f);

        BitmapText ready =
                addText(
                        actionLabel(
                                connected.readyAction(),
                                screenModel.focus() == DirectConnectUiFocus.READY_ACTION,
                                connected.controlsEnabled()),
                        21f,
                        actionColor(
                                screenModel.focus() == DirectConnectUiFocus.READY_ACTION,
                                connected.controlsEnabled()),
                        0f,
                        135f);
        ready.setLocalTranslation(Math.max(20f, (width - ready.getLineWidth()) / 2f), 135f, 0f);
        targets.add(
                new UiHitTarget(
                        DIRECT_READY_TARGET,
                        hitBounds(ready, 18f, 10f, 160f),
                        connected.controlsEnabled()));
    }

    private void renderTeamPanel(
            DirectConnectScreenModel screenModel,
            ConnectedLobbyScreenModel connected,
            LobbyTeamPanelModel panel,
            UiRect bounds) {
        boolean selected = screenModel.focus() == teamFocus(panel.team());
        String title =
                (selected ? "> " : "  ")
                        + messages.text(
                                "direct.lobby.team."
                                        + panel.team().name().toLowerCase(Locale.ROOT));
        float titleSize = Math.max(15f, Math.min(21f, bounds.width() / 12f));
        addText(
                title,
                titleSize,
                selected ? SELECTED_TEXT : teamColor(panel.team()),
                bounds.left() + 10f,
                bounds.bottom() + bounds.height() - 10f);

        int contentRows = panel.members().size() + 2;
        float memberSize = Math.max(10f, Math.min(14f, bounds.height() / contentRows));
        String body =
                messages.text("direct.lobby.occupied", panel.occupiedSlots())
                        + (panel.members().isEmpty()
                                ? ""
                                : "\n"
                                        + panel.members().stream()
                                                .map(this::memberLine)
                                                .collect(Collectors.joining("\n")));
        addText(
                body,
                memberSize,
                connected.controlsEnabled() ? PRIMARY_TEXT : MUTED_TEXT,
                bounds.left() + 10f,
                bounds.bottom() + bounds.height() - titleSize - 22f);
    }

    private String memberLine(LobbyMemberRowModel member) {
        String ownPrefix = member.ownPlayer() ? messages.text("direct.lobby.you_prefix") : "";
        String readiness =
                messages.text(member.ready() ? "direct.lobby.ready" : "direct.lobby.not_ready");
        return messages.text("direct.lobby.member", ownPrefix, member.handle(), readiness);
    }

    private void renderBottomActions(
            DirectConnectScreenModel model, List<UiHitTarget> targets, float width) {
        BitmapText primary =
                addText(
                        actionLabel(
                                model.primaryAction(),
                                model.focus() == DirectConnectUiFocus.PRIMARY_ACTION,
                                model.primaryEnabled()),
                        22f,
                        actionColor(
                                model.focus() == DirectConnectUiFocus.PRIMARY_ACTION,
                                model.primaryEnabled()),
                        0f,
                        78f);
        BitmapText secondary =
                addText(
                        actionLabel(
                                model.secondaryAction(),
                                model.focus() == DirectConnectUiFocus.SECONDARY_ACTION,
                                model.secondaryEnabled()),
                        22f,
                        actionColor(
                                model.focus() == DirectConnectUiFocus.SECONDARY_ACTION,
                                model.secondaryEnabled()),
                        0f,
                        78f);
        float gap = Math.max(36f, Math.min(72f, width * 0.07f));
        float actionWidth = primary.getLineWidth() + gap + secondary.getLineWidth();
        float actionLeft = Math.max(20f, (width - actionWidth) / 2f);
        primary.setLocalTranslation(actionLeft, 78f, 0f);
        secondary.setLocalTranslation(actionLeft + primary.getLineWidth() + gap, 78f, 0f);
        targets.add(
                new UiHitTarget(
                        DIRECT_PRIMARY_TARGET,
                        hitBounds(primary, 18f, 10f, 100f),
                        model.primaryEnabled()));
        targets.add(
                new UiHitTarget(
                        DIRECT_SECONDARY_TARGET,
                        hitBounds(secondary, 18f, 10f, 100f),
                        model.secondaryEnabled()));
    }

    private BitmapText addText(String text, float size, ColorRGBA color, float x, float y) {
        BitmapText bitmapText = new BitmapText(font);
        bitmapText.setText(text);
        bitmapText.setSize(size);
        bitmapText.setColor(color);
        bitmapText.setLocalTranslation(x, y, 0f);
        guiNode.attachChild(bitmapText);
        return bitmapText;
    }

    private void addCenteredText(String text, float size, ColorRGBA color, float y) {
        BitmapText bitmapText = addText(text, size, color, 0f, y);
        bitmapText.setLocalTranslation(
                Math.max(20f, (cam.getWidth() - bitmapText.getLineWidth()) / 2f), y, 0f);
    }

    private static UiRect hitBounds(
            BitmapText text, float horizontalPadding, float verticalPadding, float minimumWidth) {
        float renderedWidth = text.getLineWidth();
        float contentWidth = Math.max(minimumWidth, renderedWidth);
        float extraWidth = (contentWidth - renderedWidth) / 2f;
        float lineHeight = Math.max(1f, text.getLineHeight());
        return new UiRect(
                text.getLocalTranslation().x - horizontalPadding - extraWidth,
                text.getLocalTranslation().y - lineHeight - verticalPadding,
                contentWidth + (2f * horizontalPadding),
                lineHeight + (2f * verticalPadding));
    }

    private static String fieldLine(
            String label, String value, boolean selected, boolean editingEnabled) {
        String cursor = selected ? "> " : "  ";
        String rendered = editingEnabled && selected ? "[" + value + "]" : value;
        return cursor + label + ": " + rendered;
    }

    private static String actionLabel(String label, boolean selected, boolean enabled) {
        if (!enabled) {
            return "(" + label + ")";
        }
        return selected ? "[" + label + "]" : label;
    }

    private static ColorRGBA actionColor(boolean selected, boolean enabled) {
        if (!enabled) {
            return MUTED_TEXT;
        }
        return selected ? SELECTED_TEXT : PRIMARY_TEXT;
    }

    private static UiTargetId menuTargetId(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("menu index must not be negative");
        }
        return new UiTargetId("menu.entry." + index);
    }

    private int menuIndex(UiTargetId target) {
        for (int index = 0; index < menu.entries().size(); index++) {
            if (menuTargetId(index).equals(target)) {
                return index;
            }
        }
        return -1;
    }

    private static UiTargetId teamTarget(LobbyTeam team) {
        return switch (team) {
            case RED -> DIRECT_TEAM_RED_TARGET;
            case BLUE -> DIRECT_TEAM_BLUE_TARGET;
            case GREEN -> DIRECT_TEAM_GREEN_TARGET;
            case YELLOW -> DIRECT_TEAM_YELLOW_TARGET;
            case UNASSIGNED -> throw new IllegalArgumentException("unassigned has no team target");
        };
    }

    private static DirectConnectUiFocus teamFocus(LobbyTeam team) {
        return switch (team) {
            case RED -> DirectConnectUiFocus.TEAM_RED;
            case BLUE -> DirectConnectUiFocus.TEAM_BLUE;
            case GREEN -> DirectConnectUiFocus.TEAM_GREEN;
            case YELLOW -> DirectConnectUiFocus.TEAM_YELLOW;
            case UNASSIGNED -> throw new IllegalArgumentException("unassigned has no team focus");
        };
    }

    private static ColorRGBA teamColor(LobbyTeam team) {
        return switch (team) {
            case RED -> RED_TEAM;
            case BLUE -> BLUE_TEAM;
            case GREEN -> GREEN_TEAM;
            case YELLOW -> YELLOW_TEAM;
            case UNASSIGNED -> MUTED_TEXT;
        };
    }

    private static Optional<DirectConnectUiFocus> directFocus(UiTargetId target) {
        if (DIRECT_ENDPOINT_TARGET.equals(target)) {
            return Optional.of(DirectConnectUiFocus.ENDPOINT);
        }
        if (DIRECT_HANDLE_TARGET.equals(target)) {
            return Optional.of(DirectConnectUiFocus.HANDLE);
        }
        if (DIRECT_TEAM_RED_TARGET.equals(target)) {
            return Optional.of(DirectConnectUiFocus.TEAM_RED);
        }
        if (DIRECT_TEAM_BLUE_TARGET.equals(target)) {
            return Optional.of(DirectConnectUiFocus.TEAM_BLUE);
        }
        if (DIRECT_TEAM_GREEN_TARGET.equals(target)) {
            return Optional.of(DirectConnectUiFocus.TEAM_GREEN);
        }
        if (DIRECT_TEAM_YELLOW_TARGET.equals(target)) {
            return Optional.of(DirectConnectUiFocus.TEAM_YELLOW);
        }
        if (DIRECT_READY_TARGET.equals(target)) {
            return Optional.of(DirectConnectUiFocus.READY_ACTION);
        }
        if (DIRECT_PRIMARY_TARGET.equals(target)) {
            return Optional.of(DirectConnectUiFocus.PRIMARY_ACTION);
        }
        if (DIRECT_SECONDARY_TARGET.equals(target)) {
            return Optional.of(DirectConnectUiFocus.SECONDARY_ACTION);
        }
        return Optional.empty();
    }

    private static List<String> wrap(String value, int maximumCharacters) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : value.split(" ")) {
            if (current.length() > 0 && current.length() + 1 + word.length() > maximumCharacters) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(word);
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines.isEmpty() ? List.of("") : List.copyOf(lines);
    }

    private static ColorRGBA statusColor(DirectConnectUiPhase phase) {
        return switch (phase) {
            case CONNECTED -> SUCCESS_TEXT;
            case CONFIRMING_IDENTITY -> WARNING_TEXT;
            case SECURITY_ALERT, ADMISSION_REJECTED, FAILED, DISCONNECTED -> ERROR_TEXT;
            default -> PRIMARY_TEXT;
        };
    }

    private static boolean isMappedControlKey(int keyCode) {
        return keyCode == KeyInput.KEY_UP
                || keyCode == KeyInput.KEY_DOWN
                || keyCode == KeyInput.KEY_LEFT
                || keyCode == KeyInput.KEY_RIGHT
                || keyCode == KeyInput.KEY_TAB
                || keyCode == KeyInput.KEY_RETURN
                || keyCode == KeyInput.KEY_ESCAPE
                || keyCode == KeyInput.KEY_BACK;
    }

    private static Path defaultDataDirectory() {
        return Path.of("data").toAbsolutePath().normalize();
    }

    private enum Screen {
        START_MENU,
        DIRECT_CONNECT
    }
}
