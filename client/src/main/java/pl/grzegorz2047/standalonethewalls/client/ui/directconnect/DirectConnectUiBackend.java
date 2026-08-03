package pl.grzegorz2047.standalonethewalls.client.ui.directconnect;

import java.util.concurrent.CompletionStage;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectEndpoint;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectProgressListener;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectResult;
import pl.grzegorz2047.standalonethewalls.client.network.FirstUseConfirmation;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;

/** Minimal asynchronous boundary consumed by the renderer-independent UI state machine. */
interface DirectConnectUiBackend extends AutoCloseable {
    DirectConnectUiAttempt connect(
            DirectConnectEndpoint endpoint,
            CanonicalHandle handle,
            DirectConnectProgressListener progressListener);

    DirectConnectUiAttempt confirmFirstUse(
            FirstUseConfirmation confirmation, DirectConnectProgressListener progressListener);

    void discardPendingConfirmation();

    @Override
    void close();
}

interface DirectConnectUiAttempt {
    CompletionStage<DirectConnectResult> result();

    boolean cancel();
}
