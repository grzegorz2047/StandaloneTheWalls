package pl.grzegorz2047.standalonethewalls.client.ui.directconnect;

import java.util.Objects;

/** Marshals asynchronous callbacks onto the UI owner thread. */
@FunctionalInterface
public interface UiDispatcher {
    void dispatch(Runnable action);

    static UiDispatcher require(UiDispatcher dispatcher) {
        return Objects.requireNonNull(dispatcher, "dispatcher");
    }
}
