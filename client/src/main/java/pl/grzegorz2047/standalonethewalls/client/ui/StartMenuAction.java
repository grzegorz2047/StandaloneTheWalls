package pl.grzegorz2047.standalonethewalls.client.ui;

/** First-screen actions. Their later workflows are implemented by separate issues. */
public enum StartMenuAction {
    PLAY("menu.play"),
    SETTINGS("menu.settings"),
    EXIT("menu.exit");

    private final String labelKey;

    StartMenuAction(String labelKey) {
        this.labelKey = labelKey;
    }

    public String labelKey() {
        return labelKey;
    }
}
