package pl.grzegorz2047.standalonethewalls.client.preparation;

import java.lang.ref.WeakReference;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhase;

/** Renderer-thread bridge from authoritative phase presentation to the active verified scene. */
public final class PreparationPhasePresentationBridge {
    private static final ThreadLocal<PresentationState> STATE =
            ThreadLocal.withInitial(PresentationState::new);

    private PreparationPhasePresentationBridge() {
        throw new AssertionError("No instances");
    }

    public static void bind(VerifiedPreparationScene scene) {
        VerifiedPreparationScene verified = Objects.requireNonNull(scene, "scene");
        PresentationState state = STATE.get();
        state.activeScene = new WeakReference<>(verified);
        if (state.openRequested) {
            verified.openCentralBarriers();
        }
    }

    public static void apply(LobbyMatchPhase phase) {
        LobbyMatchPhase authoritativePhase = Objects.requireNonNull(phase, "phase");
        PresentationState state = STATE.get();
        if (authoritativePhase == LobbyMatchPhase.WAITING_FOR_PLAYERS
                || authoritativePhase == LobbyMatchPhase.START_COUNTDOWN
                || authoritativePhase == LobbyMatchPhase.PREPARATION) {
            state.openRequested = false;
            return;
        }
        if (authoritativePhase != LobbyMatchPhase.OPEN_COMBAT) {
            return;
        }
        state.openRequested = true;
        VerifiedPreparationScene scene = state.activeScene.get();
        if (scene != null) {
            scene.openCentralBarriers();
        }
    }

    static void clear() {
        STATE.remove();
    }

    private static final class PresentationState {
        private WeakReference<VerifiedPreparationScene> activeScene = new WeakReference<>(null);
        private boolean openRequested;
    }
}
