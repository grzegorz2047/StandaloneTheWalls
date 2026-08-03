package pl.grzegorz2047.standalonethewalls.client.ui.pointer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class UiPointerRouterTest {
    private static final UiTargetId FIRST = new UiTargetId("menu.first");
    private static final UiTargetId SECOND = new UiTargetId("menu.second");

    @Test
    void routesOnlyMatchingPrimaryButtonPressAndRelease() {
        UiPointerRouter router = new UiPointerRouter();
        router.replaceHitMap(
                new UiHitMap(
                        List.of(
                                UiHitTarget.enabled(FIRST, new UiRect(0f, 0f, 40f, 40f)),
                                UiHitTarget.enabled(SECOND, new UiRect(40f, 0f, 40f, 40f)))));

        assertEquals(FIRST, router.hover(20f, 20f).orElseThrow());
        assertTrue(router.button(1, true, 20f, 20f).isEmpty());
        assertTrue(router.button(1, false, 20f, 20f).isEmpty());
        assertTrue(router.pressedTarget().isEmpty());

        assertTrue(router.button(UiPointerRouter.PRIMARY_BUTTON, true, 20f, 20f).isEmpty());
        assertEquals(FIRST, router.pressedTarget().orElseThrow());
        assertEquals(
                FIRST,
                router.button(UiPointerRouter.PRIMARY_BUTTON, false, 20f, 20f).orElseThrow());
        assertTrue(router.button(UiPointerRouter.PRIMARY_BUTTON, false, 20f, 20f).isEmpty());

        router.button(UiPointerRouter.PRIMARY_BUTTON, true, 20f, 20f);
        assertTrue(router.button(UiPointerRouter.PRIMARY_BUTTON, false, 60f, 20f).isEmpty());
    }

    @Test
    void replacingGeometryCancelsPressAndRemovesOldHitboxes() {
        UiPointerRouter router = new UiPointerRouter();
        router.replaceHitMap(
                new UiHitMap(List.of(UiHitTarget.enabled(FIRST, new UiRect(0f, 0f, 40f, 40f)))));
        router.button(UiPointerRouter.PRIMARY_BUTTON, true, 20f, 20f);

        router.replaceHitMap(
                new UiHitMap(
                        List.of(UiHitTarget.enabled(SECOND, new UiRect(100f, 100f, 40f, 40f)))));

        assertTrue(router.pressedTarget().isEmpty());
        assertTrue(router.hover(20f, 20f).isEmpty());
        assertEquals(SECOND, router.hover(120f, 120f).orElseThrow());
        assertTrue(router.button(UiPointerRouter.PRIMARY_BUTTON, false, 120f, 120f).isEmpty());
    }
}
