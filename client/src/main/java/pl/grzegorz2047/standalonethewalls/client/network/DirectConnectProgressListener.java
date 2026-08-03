package pl.grzegorz2047.standalonethewalls.client.network;

import java.util.Objects;

/** Observer for bounded progress only; it never receives transport or cryptographic objects. */
@FunctionalInterface
public interface DirectConnectProgressListener {
    DirectConnectProgressListener NONE = ignored -> {};

    void onStage(DirectConnectStage stage);

    static DirectConnectProgressListener require(DirectConnectProgressListener listener) {
        return Objects.requireNonNull(listener, "listener");
    }
}
