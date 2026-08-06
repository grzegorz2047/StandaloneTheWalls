package pl.grzegorz2047.standalonethewalls.client.preparation;

import java.lang.ref.WeakReference;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhase;

/** Renderer-thread bridge from authoritative phase presentation to the active verified scene. */
public final class PreparationPhasePresentationBridge {
    private static final ThreadLocal<WeakReference<VerifiedPreparationScene>> ACTIVE_SCENE =
            new ThreadLocal<>();

    private PreparationPhasePresentationBridge() {
        throw new AssertionError("No instances");
    }

    public static void bind(VerifiedPreparationScene scene) {
        ACTIVE_SCENE.set(new WeakReference<>(Objects.requireNonNull(scene, "scene")));
    }

    public static void apply(LobbyMatchPhase phase) {
        LobbyMatchPhase authoritativePhase = Objects.requireNonNull(phase, "phase");
        if (authoritativePhase != LobbyMatchPhase.OPEN_COMBAT) {
            return;
        }
        WeakReference<VerifiedPreparationScene> reference = ACTIVE_SCENE.get();
        if (reference == null) {
            return;
        }
        VerifiedPreparationScene scene = reference.get();
        if (scene == null) {
            ACTIVE_SCENE.remove();
            return;
        }
        scene.openCentralBarriers();
    }

    static void clear() {
        ACTIVE_SCENE.remove();
    }
}
