package pl.grzegorz2047.standalonethewalls.client.network;

/** Public non-terminal progress stages suitable for immutable UI state. */
public enum DirectConnectStage {
    RESOLVING,
    CONNECTING,
    SECURING_TRANSPORT,
    AUTHENTICATING,
    WAITING_ADMISSION,
    JOINING_LOBBY
}
