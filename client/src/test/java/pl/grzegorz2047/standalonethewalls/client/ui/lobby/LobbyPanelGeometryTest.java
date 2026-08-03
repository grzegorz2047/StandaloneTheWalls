package pl.grzegorz2047.standalonethewalls.client.ui.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.client.ui.pointer.UiRect;

class LobbyPanelGeometryTest {
    @Test
    void createsTwoByTwoPanelsForNarrowViewportWithoutOverlap() {
        LobbyPanelGeometry geometry = LobbyPanelGeometry.forViewport(720f, 720f);

        assertEquals(LobbyPanelLayout.TWO_BY_TWO, geometry.layout());
        assertEquals(4, geometry.panels().size());
        assertWithinViewportAndDisjoint(geometry.panels(), 720f, 720f);
        assertEquals(geometry.panels().get(0).bottom(), geometry.panels().get(1).bottom());
        assertTrue(geometry.panels().get(2).bottom() < geometry.panels().get(0).bottom());
    }

    @Test
    void createsFourColumnsForDesktopViewportInStableOrder() {
        LobbyPanelGeometry geometry = LobbyPanelGeometry.forViewport(1920f, 1080f);

        assertEquals(LobbyPanelLayout.FOUR_COLUMNS, geometry.layout());
        assertWithinViewportAndDisjoint(geometry.panels(), 1920f, 1080f);
        for (int index = 1; index < geometry.panels().size(); index++) {
            assertTrue(
                    geometry.panels().get(index).left() > geometry.panels().get(index - 1).left());
            assertEquals(geometry.panels().get(0).bottom(), geometry.panels().get(index).bottom());
        }
    }

    @Test
    void rejectsInvalidOrTooSmallViewports() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LobbyPanelGeometry.forViewport(Float.NaN, 720f));
        assertThrows(
                IllegalArgumentException.class, () -> LobbyPanelGeometry.forViewport(720f, 0f));
        assertThrows(
                IllegalArgumentException.class, () -> LobbyPanelGeometry.forViewport(100f, 200f));
    }

    private static void assertWithinViewportAndDisjoint(
            List<UiRect> panels, float viewportWidth, float viewportHeight) {
        for (int first = 0; first < panels.size(); first++) {
            UiRect panel = panels.get(first);
            assertTrue(panel.left() >= 0f);
            assertTrue(panel.bottom() >= 0f);
            assertTrue(panel.left() + panel.width() <= viewportWidth);
            assertTrue(panel.bottom() + panel.height() <= viewportHeight);
            for (int second = first + 1; second < panels.size(); second++) {
                assertFalse(overlaps(panel, panels.get(second)));
            }
        }
    }

    private static boolean overlaps(UiRect first, UiRect second) {
        return first.left() < second.left() + second.width()
                && first.left() + first.width() > second.left()
                && first.bottom() < second.bottom() + second.height()
                && first.bottom() + first.height() > second.bottom();
    }
}
