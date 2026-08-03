package pl.grzegorz2047.standalonethewalls.client.ui;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientMessages;

/** Immutable, renderer-independent start-menu state. */
public record StartMenuModel(List<StartMenuEntry> entries, int selectedIndex) {
    public StartMenuModel {
        entries = List.copyOf(entries);
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("start menu requires at least one entry");
        }
        if (selectedIndex < 0 || selectedIndex >= entries.size()) {
            throw new IllegalArgumentException("selectedIndex is outside the menu");
        }
    }

    public static StartMenuModel create(ClientMessages messages) {
        Objects.requireNonNull(messages, "messages");
        List<StartMenuEntry> entries =
                Arrays.stream(StartMenuAction.values())
                        .map(action -> new StartMenuEntry(action, messages.text(action.labelKey())))
                        .toList();
        return new StartMenuModel(entries, 0);
    }

    public StartMenuModel move(int delta) {
        int size = entries.size();
        int next = Math.floorMod(selectedIndex + delta, size);
        return new StartMenuModel(entries, next);
    }

    public StartMenuModel select(int index) {
        return index == selectedIndex ? this : new StartMenuModel(entries, index);
    }

    public StartMenuEntry selectedEntry() {
        return entries.get(selectedIndex);
    }
}
