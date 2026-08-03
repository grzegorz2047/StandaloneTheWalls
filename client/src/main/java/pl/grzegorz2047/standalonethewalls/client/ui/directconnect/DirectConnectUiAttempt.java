package pl.grzegorz2047.standalonethewalls.client.ui.directconnect;

import java.util.concurrent.CompletionStage;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectResult;

/** Cancellable asynchronous operation exposed to the Direct Connect UI state machine. */
interface DirectConnectUiAttempt {
    CompletionStage<DirectConnectResult> result();

    boolean cancel();
}
