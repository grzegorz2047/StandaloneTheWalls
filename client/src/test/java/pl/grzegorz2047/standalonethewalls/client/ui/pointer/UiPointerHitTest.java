package pl.grzegorz2047.standalonethewalls.client.ui.pointer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class UiPointerHitTest {
    private static final UiTargetId LOWER = new UiTargetId("menu.play");
    private static final UiTargetId UPPER = new UiTargetId("menu.settings");

    @Test
    void rectangleUsesInclusiveLowerAndExclusiveUpperBounds() {
        UiRect rectangle = new UiRect(10f, 20f, 30f, 40f);

        assertTrue(rectangle.contains(10f, 20f));
        assertTrue(rectangle.contains(39.999f, 59.999f));
        assertFalse(rectangle.contains(40f, 30f));
        assertFalse(rectangle.contains(20f, 60f));
        assertFalse(rectangle.contains(Float.NaN, 20f));
        assertFalse(rectangle.contains(10f, Float.POSITIVE_INFINITY));
    }

    @Test
    void rectangleAndTargetIdentifiersRejectNonCanonicalValues() {
        assertThrows(IllegalArgumentException.class, () -> new UiRect(0f, 0f, 0f, 1f));
        assertThrows(
                IllegalArgumentException.class,
                () -> new UiRect(Float.NaN, 0f, 1f, 1f));
        assertThrows(IllegalArgumentException.class, () -> new UiTargetId("Menu Play"));
        assertThrows(IllegalArgumentException.class, () -> new UiTargetId(""));
    }

    @Test
    void lastEnabledMatchingTargetWinsAndDisabledTargetIsIgnored() {
        UiRect shared = new UiRect(0f, 0f, 100f, 100f);
        UiHitMap layered =
                new UiHitMap(
                        List.of(
                                UiHitTarget.enabled(LOWER, shared),
                                UiHitTarget.enabled(UPPER, shared)));
        UiHitMap disabledTop =
                new UiHitMap(
                        List.of(
                                UiHitTarget.enabled(LOWER, shared),
                                new UiHitTarget(UPPER, shared, false)));

        assertEquals(UPPER, layered.targetAt(50f, 50f).orElseThrow().id());
        assertEquals(LOWER, disabledTop.targetAt(50f, 50f).orElseThrow().id());
        assertTrue(layered.targetAt(100f, 50f).isEmpty());
    }

    @Test
    void hitMapRejectsDuplicateIdentifiersAndCopiesInput() {
        UiHitTarget target = UiHitTarget.enabled(LOWER, new UiRect(0f, 0f, 10f, 10f));

        assertThrows(
                IllegalArgumentException.class,
                () -> new UiHitMap(List.of(target, target)));
        UiHitMap map = new UiHitMap(List.of(target));
        assertThrows(UnsupportedOperationException.class, () -> map.targets().clear());
    }

    @Test
    void pointerGestureActivatesOnlyMatchingPressAndRelease() {
        UiHitMap map =
                new UiHitMap(
                        List.of(
                                UiHitTarget.enabled(LOWER, new UiRect(0f, 0f, 50f, 50f)),
                                UiHitTarget.enabled(UPPER, new UiRect(50f, 0f, 50f, 50f))));
        UiPointerGesture gesture = new UiPointerGesture();

        assertEquals(LOWER, gesture.press(map, 25f, 25f).orElseThrow());
        assertEquals(LOWER, gesture.pressedTarget().orElseThrow());
        assertEquals(LOWER, gesture.release(map, 25f, 25f).orElseThrow());
        assertTrue(gesture.pressedTarget().isEmpty());
        assertTrue(gesture.release(map, 25f, 25f).isEmpty());

        gesture.press(map, 25f, 25f);
        assertTrue(gesture.release(map, 75f, 25f).isEmpty());
        assertTrue(gesture.pressedTarget().isEmpty());

        gesture.press(map, 25f, 25f);
        assertTrue(gesture.release(map, 150f, 25f).isEmpty());

        gesture.press(map, 75f, 25f);
        gesture.cancel();
        assertTrue(gesture.release(map, 75f, 25f).isEmpty());
    }
}
