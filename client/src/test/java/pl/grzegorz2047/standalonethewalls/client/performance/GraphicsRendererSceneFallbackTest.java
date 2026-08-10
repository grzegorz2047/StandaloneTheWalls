package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jme3.renderer.RendererException;
import com.jme3.scene.Node;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GraphicsRendererSceneFallbackTest {
    @Test
    void healthyPreferredSceneDoesNotBuildFallback() {
        Node preferred = new Node("preferred");
        AtomicInteger fallbackBuilds = new AtomicInteger();
        AtomicInteger preloadAttempts = new AtomicInteger();

        GraphicsRendererSceneFallback.Result result =
                GraphicsRendererSceneFallback.prepare(
                        () -> preferred,
                        () -> {
                            fallbackBuilds.incrementAndGet();
                            return new Node("fallback");
                        },
                        scene -> preloadAttempts.incrementAndGet());

        assertThat(result.scene()).isSameAs(preferred);
        assertThat(result.fallbackUsed()).isFalse();
        assertThat(fallbackBuilds).hasValue(0);
        assertThat(preloadAttempts).hasValue(1);
    }

    @Test
    void preferredRendererFailureRetriesFallbackExactlyOnce() {
        Node fallback = new Node("fallback");
        AtomicInteger preloadAttempts = new AtomicInteger();

        GraphicsRendererSceneFallback.Result result =
                GraphicsRendererSceneFallback.prepare(
                        () -> new Node("preferred"),
                        () -> fallback,
                        scene -> {
                            if (preloadAttempts.getAndIncrement() == 0) {
                                throw new RendererException("preferred shader failed");
                            }
                        });

        assertThat(result.scene()).isSameAs(fallback);
        assertThat(result.fallbackUsed()).isTrue();
        assertThat(preloadAttempts).hasValue(2);
    }

    @Test
    void fallbackRendererFailurePropagatesWithoutAnotherRetry() {
        AtomicInteger preloadAttempts = new AtomicInteger();

        assertThatThrownBy(
                        () ->
                                GraphicsRendererSceneFallback.prepare(
                                        () -> new Node("preferred"),
                                        () -> new Node("fallback"),
                                        scene -> {
                                            preloadAttempts.incrementAndGet();
                                            throw new RendererException("shader failed");
                                        }))
                .isInstanceOf(RendererException.class)
                .satisfies(
                        failure ->
                                assertThat(failure.getSuppressed())
                                        .hasSize(1)
                                        .allMatch(RendererException.class::isInstance));
        assertThat(preloadAttempts).hasValue(2);
    }
}
