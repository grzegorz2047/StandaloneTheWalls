package pl.grzegorz2047.standalonethewalls.client.performance;

import com.jme3.renderer.RendererException;
import com.jme3.scene.Node;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Prepares a preferred renderer scene and retries exactly once with a conservative fallback. */
final class GraphicsRendererSceneFallback {
    private GraphicsRendererSceneFallback() {
        throw new AssertionError("No instances");
    }

    static Result prepare(
            Supplier<Node> preferredScene,
            Supplier<Node> fallbackScene,
            Consumer<Node> preloader) {
        Objects.requireNonNull(preferredScene, "preferredScene");
        Objects.requireNonNull(fallbackScene, "fallbackScene");
        Objects.requireNonNull(preloader, "preloader");

        Node preferred = Objects.requireNonNull(preferredScene.get(), "preferred scene");
        try {
            preloader.accept(preferred);
            return new Result(preferred, false);
        } catch (RendererException preferredFailure) {
            Node fallback = Objects.requireNonNull(fallbackScene.get(), "fallback scene");
            try {
                preloader.accept(fallback);
            } catch (RendererException fallbackFailure) {
                fallbackFailure.addSuppressed(preferredFailure);
                throw fallbackFailure;
            }
            return new Result(fallback, true);
        }
    }

    record Result(Node scene, boolean fallbackUsed) {
        Result {
            Objects.requireNonNull(scene, "scene");
        }
    }
}
