package pl.grzegorz2047.standalonethewalls.client.ui.directconnect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientLanguage;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientMessages;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectEndpoint;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectProgressListener;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectResult;
import pl.grzegorz2047.standalonethewalls.client.network.FirstUseConfirmation;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;

class DirectConnectUiPointerFocusTest {
    @Test
    void acceptsOnlyTargetsAvailableInCurrentPhase() {
        PendingBackend backend = new PendingBackend();
        DirectConnectUiController controller =
                new DirectConnectUiController(
                        backend,
                        ClientMessages.forLanguage(ClientLanguage.ENGLISH),
                        Runnable::run,
                        ignored -> {},
                        () -> {});
        controller.open();

        assertTrue(controller.focus(DirectConnectUiFocus.HANDLE));
        assertEquals(DirectConnectUiFocus.HANDLE, controller.model().focus());
        assertTrue(controller.focus(DirectConnectUiFocus.PRIMARY_ACTION));
        controller.activate();

        assertEquals(DirectConnectUiPhase.RESOLVING, controller.model().phase());
        assertEquals(DirectConnectUiFocus.SECONDARY_ACTION, controller.model().focus());
        assertFalse(controller.focus(DirectConnectUiFocus.ENDPOINT));
        assertEquals(DirectConnectUiFocus.SECONDARY_ACTION, controller.model().focus());
        assertTrue(controller.focus(DirectConnectUiFocus.SECONDARY_ACTION));
        controller.close();
    }

    private static final class PendingBackend implements DirectConnectUiBackend {
        private final CompletableFuture<DirectConnectResult> result = new CompletableFuture<>();

        @Override
        public DirectConnectUiAttempt connect(
                DirectConnectEndpoint endpoint,
                CanonicalHandle handle,
                DirectConnectProgressListener progressListener) {
            return new DirectConnectUiAttempt() {
                @Override
                public CompletionStage<DirectConnectResult> result() {
                    return result;
                }

                @Override
                public boolean cancel() {
                    return true;
                }
            };
        }

        @Override
        public DirectConnectUiAttempt confirmFirstUse(
                FirstUseConfirmation confirmation, DirectConnectProgressListener progressListener) {
            throw new AssertionError("confirmation is outside this test");
        }

        @Override
        public void discardPendingConfirmation() {}

        @Override
        public void close() {}
    }
}
