package pl.grzegorz2047.standalonethewalls.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientLanguage;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientMessages;

class StartMenuModelTest {
    @Test
    void exposesAllThreeActionsInStableOrderAndLocalizedLabels() {
        StartMenuModel menu =
                StartMenuModel.create(ClientMessages.forLanguage(ClientLanguage.POLISH));

        assertEquals(
                List.of(StartMenuAction.PLAY, StartMenuAction.SETTINGS, StartMenuAction.EXIT),
                menu.entries().stream().map(StartMenuEntry::action).toList());
        assertEquals(
                List.of("Graj", "Ustawienia", "Koniec"),
                menu.entries().stream().map(StartMenuEntry::label).toList());
        assertEquals(StartMenuAction.PLAY, menu.selectedEntry().action());
    }

    @Test
    void wrapsSelectionInBothDirectionsWithoutMutatingTheOriginal() {
        StartMenuModel original =
                StartMenuModel.create(ClientMessages.forLanguage(ClientLanguage.ENGLISH));

        StartMenuModel previous = original.move(-1);
        StartMenuModel next = original.move(1);

        assertEquals(0, original.selectedIndex());
        assertEquals(StartMenuAction.EXIT, previous.selectedEntry().action());
        assertEquals(StartMenuAction.SETTINGS, next.selectedEntry().action());
    }

    @Test
    void selectsExactPointerIndexAndRejectsOutsideIndex() {
        StartMenuModel original =
                StartMenuModel.create(ClientMessages.forLanguage(ClientLanguage.ENGLISH));

        StartMenuModel selected = original.select(2);

        assertEquals(0, original.selectedIndex());
        assertEquals(StartMenuAction.EXIT, selected.selectedEntry().action());
        assertSame(selected, selected.select(2));
        assertThrows(IllegalArgumentException.class, () -> original.select(-1));
        assertThrows(
                IllegalArgumentException.class, () -> original.select(original.entries().size()));
    }
}
