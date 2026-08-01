package pl.grzegorz2047.standalonethewalls.client;

import com.jme3.app.SimpleApplication;
import com.jme3.app.state.AppState;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.math.ColorRGBA;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientMessages;
import pl.grzegorz2047.standalonethewalls.client.ui.StartMenuAction;
import pl.grzegorz2047.standalonethewalls.client.ui.StartMenuModel;

/** First real jMonkeyEngine client screen with a renderer-free headless smoke path. */
public final class SunderfrontClient extends SimpleApplication implements ActionListener {
    private static final String INPUT_UP = "sunderfront-menu-up";
    private static final String INPUT_DOWN = "sunderfront-menu-down";
    private static final String INPUT_SELECT = "sunderfront-menu-select";
    private static final String INPUT_EXIT = "sunderfront-menu-exit";

    private static final ColorRGBA BACKGROUND = new ColorRGBA(0.025f, 0.035f, 0.06f, 1f);
    private static final ColorRGBA PRIMARY_TEXT = new ColorRGBA(0.88f, 0.91f, 0.96f, 1f);
    private static final ColorRGBA MUTED_TEXT = new ColorRGBA(0.58f, 0.65f, 0.76f, 1f);
    private static final ColorRGBA SELECTED_TEXT = new ColorRGBA(0.94f, 0.72f, 0.28f, 1f);

    private final ClientMessages messages;
    private final boolean smokeMode;
    private final CompletableFuture<Void> initialized = new CompletableFuture<>();
    private final List<BitmapText> entryTexts = new ArrayList<>();
    private StartMenuModel menu;
    private BitmapText statusText;

    public SunderfrontClient(ClientMessages messages, boolean smokeMode) {
        super(new AppState[0]);
        this.messages = Objects.requireNonNull(messages, "messages");
        this.smokeMode = smokeMode;
    }

    @Override
    public void simpleInitApp() {
        try {
            menu = StartMenuModel.create(messages);
            if (!smokeMode) {
                initializeStartScreen();
            }
            initialized.complete(null);
        } catch (RuntimeException exception) {
            initialized.completeExceptionally(exception);
            stop();
        }
    }

    @Override
    public void onAction(String name, boolean isPressed, float timePerFrame) {
        if (!isPressed || smokeMode) {
            return;
        }
        switch (name) {
            case INPUT_UP -> {
                menu = menu.move(-1);
                refreshMenu();
            }
            case INPUT_DOWN -> {
                menu = menu.move(1);
                refreshMenu();
            }
            case INPUT_SELECT -> activateSelectedEntry();
            case INPUT_EXIT -> stop();
            default -> {
                // InputManager invokes this listener only for registered mappings.
            }
        }
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

    private void initializeStartScreen() {
        viewPort.setBackgroundColor(BACKGROUND);
        inputManager.setCursorVisible(true);
        BitmapFont font = assetManager.loadFont("Interface/Fonts/Default.fnt");

        BitmapText title = createText(font, messages.text("app.title"), 54f, PRIMARY_TEXT);
        centerHorizontally(title, cam.getHeight() - 90f);

        BitmapText subtitle = createText(font, messages.text("app.subtitle"), 22f, MUTED_TEXT);
        centerHorizontally(subtitle, cam.getHeight() - 140f);

        float menuTop = cam.getHeight() - 260f;
        for (int index = 0; index < menu.entries().size(); index++) {
            BitmapText entry = createText(font, "", 30f, PRIMARY_TEXT);
            entry.setLocalTranslation(cam.getWidth() * 0.34f, menuTop - (index * 56f), 0f);
            entryTexts.add(entry);
        }

        statusText = createText(font, "", 18f, MUTED_TEXT);
        centerHorizontally(statusText, 105f);

        BitmapText help = createText(font, messages.text("menu.help"), 17f, MUTED_TEXT);
        centerHorizontally(help, 52f);

        inputManager.addMapping(INPUT_UP, new KeyTrigger(KeyInput.KEY_UP));
        inputManager.addMapping(INPUT_DOWN, new KeyTrigger(KeyInput.KEY_DOWN));
        inputManager.addMapping(INPUT_SELECT, new KeyTrigger(KeyInput.KEY_RETURN));
        inputManager.addMapping(INPUT_EXIT, new KeyTrigger(KeyInput.KEY_ESCAPE));
        inputManager.addListener(this, INPUT_UP, INPUT_DOWN, INPUT_SELECT, INPUT_EXIT);
        refreshMenu();
    }

    private BitmapText createText(BitmapFont font, String text, float size, ColorRGBA color) {
        BitmapText bitmapText = new BitmapText(font);
        bitmapText.setText(text);
        bitmapText.setSize(size);
        bitmapText.setColor(color);
        guiNode.attachChild(bitmapText);
        return bitmapText;
    }

    private void centerHorizontally(BitmapText text, float y) {
        text.setLocalTranslation((cam.getWidth() - text.getLineWidth()) / 2f, y, 0f);
    }

    private void refreshMenu() {
        for (int index = 0; index < menu.entries().size(); index++) {
            boolean selected = index == menu.selectedIndex();
            BitmapText text = entryTexts.get(index);
            text.setText((selected ? "> " : "  ") + menu.entries().get(index).label());
            text.setColor(selected ? SELECTED_TEXT : PRIMARY_TEXT);
        }
    }

    private void activateSelectedEntry() {
        StartMenuAction action = menu.selectedEntry().action();
        if (action == StartMenuAction.EXIT) {
            stop();
            return;
        }
        statusText.setText(messages.text("menu.unavailable"));
        centerHorizontally(statusText, 105f);
    }
}
