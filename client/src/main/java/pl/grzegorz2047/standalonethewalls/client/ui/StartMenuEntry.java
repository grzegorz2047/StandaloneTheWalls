package pl.grzegorz2047.standalonethewalls.client.ui;

import java.util.Objects;

/** One localized menu entry. */
public record StartMenuEntry(StartMenuAction action, String label) {
    public StartMenuEntry {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(label, "label");
        if (label.isBlank()) {
            throw new IllegalArgumentException("menu label cannot be blank");
        }
    }
}
