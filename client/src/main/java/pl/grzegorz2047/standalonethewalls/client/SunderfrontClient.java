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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientMessages;
import pl.grzegorz2047.standalonethewalls.client.identity.ClientIdentityStorage;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectService;
import pl.grzegorz2047.standalonethewalls.client.ui.StartMenuAction;
import pl.grzegorz2047.standalonethewalls.client.ui.StartMenuModel;
import pl.grzegorz2047.standalonethewalls.client.ui.directconnect.DirectConnectScreenModel;
import pl.grzegorz2047.standalonethewalls.client.ui.directconnect.DirectConnectUiController;
import pl.grzegorz2047.standalonethewalls.client.ui.directconnect.DirectConnectUiFocus;
import pl.grzegorz2047.standalonethewalls.client.ui.directconnect.DirectConnectUiPhase;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;

/** Keyboard-first jMonkeyEngine shell for the start menu and production Direct Connect flow. */
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

    private static final ColorRGBA BACKGROUND = new ColorRGBA(0.025f, 0.035f, 0.06f, 1f);
    private static final ColorRGBA PRIMARY_TEXT = new ColorRGBA(0.88f, 0.91f, 0.96f, 1f);
    private static final ColorRGBA MUTED_TEXT = new ColorRGBA(0.58f, 0.65f, 0.76f, 1f);
    private static final ColorRGBA SELECTED_TEXT = new ColorRGBA(0.94f, 0.72f, 0.28f, 1f);
    private static final ColorRGBA SUCCESS_TEXT = new ColorRGBA(0.35f, 0.86f, 0.55f, 1f);
    private static final ColorRGBA WARNING_TEXT = new ColorRGBA(0.98f, 0.66f, 0.22f, 1f);
    private static final ColorRGBA ERROR_TEXT = new ColorRGBA(0.96f, 0.34f, 0.31f, 1f);

    private final ClientMessages messages;
    private final boolean smokeMode;
    private final Path dataDirectory;
    private final CompletableFuture<Void> initialized = new CompletableFuture<>();

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
    public void onMouseMotionEvent(MouseMotionEvent event) {}

    @Override
    public void onMouseButtonEvent(MouseButtonEvent event) {}

    @Override
    public void onTouchEvent(TouchEvent event) {}

    @Override
    public void destroy() {
        shuttingDown = true;
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
        guiNode.detachAllChildren();
        if (screen == Screen.START_MENU) {
            renderStartMenu();
        } else if (directConnectModel != null) {
            renderDirectConnect(directConnectModel);
        }
    }

    private void renderStartMenu() {
        addCenteredText(messages.text("app.title"), 54f, PRIMARY_TEXT, cam.getHeight() - 90f);
        addCenteredText(messages.text("app.subtitle"), 22f, MUTED_TEXT, cam.getHeight() - 140f);

        float menuTop = cam.getHeight() - 260f;
        for (int index = 0; index < menu.entries().size(); index++) {
            boolean selected = index == menu.selectedIndex();
            String label = (selected ? "> " : "  ") + menu.entries().get(index).label();
            addText(
                    label,
                    30f,
                    selected ? SELECTED_TEXT : PRIMARY_TEXT,
                    cam.getWidth() * 0.34f,
                    menuTop - (index * 56f));
        }
        if (!menuStatus.isBlank()) {
            addCenteredText(menuStatus, 18f, WARNING_TEXT, 105f);
        }
        addCenteredText(messages.text("menu.help"), 17f, MUTED_TEXT, 52f);
    }

    private void renderDirectConnect(DirectConnectScreenModel model) {
        float width = cam.getWidth();
        float height = cam.getHeight();
        float left = Math.max(42f, width * 0.12f);

        addCenteredText(model.title(), 40f, PRIMARY_TEXT, height - 65f);
        addCenteredText(model.status(), 22f, statusColor(model.phase()), height - 112f);

        addText(
                fieldLine(
                        messages.text("direct.field.endpoint"),
                        model.endpointText(),
                        model.focus() == DirectConnectUiFocus.ENDPOINT,
                        model.editingEnabled()),
                22f,
                model.focus() == DirectConnectUiFocus.ENDPOINT ? SELECTED_TEXT : PRIMARY_TEXT,
                left,
                height - 180f);
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

        if (!model.members().isEmpty()) {
            int columns = width >= 1050f ? 3 : 2;
            int rows = (model.members().size() + columns - 1) / columns;
            float available = Math.max(120f, height - 390f);
            float memberSize = Math.max(12f, Math.min(17f, available / Math.max(1, rows)));
            addText(
                    formatMembers(model.members(), model.handleText(), columns),
                    memberSize,
                    PRIMARY_TEXT,
                    left,
                    Math.min(detailY - 18f, height - 340f));
        }

        String actions =
                actionLabel(
                                model.primaryAction(),
                                model.focus() == DirectConnectUiFocus.PRIMARY_ACTION,
                                model.primaryEnabled())
                        + "      "
                        + actionLabel(
                                model.secondaryAction(),
                                model.focus() == DirectConnectUiFocus.SECONDARY_ACTION,
                                model.secondaryEnabled());
        addCenteredText(actions, 22f, SELECTED_TEXT, 78f);
        addCenteredText(messages.text("direct.help"), 15f, MUTED_TEXT, 34f);
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

    private static String formatMembers(List<LobbyMember> members, String ownHandle, int columns) {
        int rows = (members.size() + columns - 1) / columns;
        StringBuilder result = new StringBuilder();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int index = row + (column * rows);
                String cell = "";
                if (index < members.size()) {
                    String handle = members.get(index).handle().value();
                    cell = (handle.equals(ownHandle) ? "* " : "  ") + handle;
                }
                result.append(padRight(cell, 29));
            }
            if (row + 1 < rows) {
                result.append('\n');
            }
        }
        return result.toString();
    }

    private static String padRight(String value, int length) {
        if (value.length() >= length) {
            return value;
        }
        return value + " ".repeat(length - value.length());
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
        String userHome = System.getProperty("user.home");
        Path home =
                userHome == null || userHome.isBlank()
                        ? Path.of(".").toAbsolutePath().normalize()
                        : Path.of(userHome);
        return home.resolve(".sunderfront");
    }

    private enum Screen {
        START_MENU,
        DIRECT_CONNECT
    }
}
