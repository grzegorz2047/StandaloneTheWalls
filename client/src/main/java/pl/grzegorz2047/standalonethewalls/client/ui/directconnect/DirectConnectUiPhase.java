package pl.grzegorz2047.standalonethewalls.client.ui.directconnect;

/** Immutable presentation phases for the Direct Connect vertical slice. */
public enum DirectConnectUiPhase {
    FORM,
    RESOLVING,
    CONNECTING,
    SECURING_TRANSPORT,
    AUTHENTICATING,
    WAITING_ADMISSION,
    JOINING_LOBBY,
    CONFIRMING_IDENTITY,
    SECURITY_ALERT,
    ADMISSION_REJECTED,
    FAILED,
    CONNECTED,
    DISCONNECTED
}
