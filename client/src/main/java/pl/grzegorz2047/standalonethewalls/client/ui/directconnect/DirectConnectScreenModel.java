package pl.grzegorz2047.standalonethewalls.client.ui.directconnect;

import java.util.Objects;
import java.util.Optional;

/** Complete immutable render input for the Direct Connect screen. */
public record DirectConnectScreenModel(
        DirectConnectUiPhase phase,
        DirectConnectUiFocus focus,
        String endpointText,
        String handleText,
        String title,
        String status,
        String detail,
        String primaryAction,
        String secondaryAction,
        boolean primaryEnabled,
        boolean secondaryEnabled,
        Optional<String> fingerprint,
        Optional<ConnectedLobbyScreenModel> connectedLobby) {
    public DirectConnectScreenModel {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(focus, "focus");
        endpointText = Objects.requireNonNull(endpointText, "endpointText");
        handleText = Objects.requireNonNull(handleText, "handleText");
        title = Objects.requireNonNull(title, "title");
        status = Objects.requireNonNull(status, "status");
        detail = Objects.requireNonNull(detail, "detail");
        primaryAction = Objects.requireNonNull(primaryAction, "primaryAction");
        secondaryAction = Objects.requireNonNull(secondaryAction, "secondaryAction");
        fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        connectedLobby = Objects.requireNonNull(connectedLobby, "connectedLobby");
        if (fingerprint.isPresent() && phase != DirectConnectUiPhase.CONFIRMING_IDENTITY) {
            throw new IllegalArgumentException(
                    "fingerprint is allowed only while confirming server identity");
        }
        if (connectedLobby.isPresent() != (phase == DirectConnectUiPhase.CONNECTED)) {
            throw new IllegalArgumentException(
                    "structured lobby state is required exactly while connected");
        }
    }

    public boolean editingEnabled() {
        return phase == DirectConnectUiPhase.FORM;
    }

    public boolean operationInProgress() {
        return switch (phase) {
            case RESOLVING,
                    CONNECTING,
                    SECURING_TRANSPORT,
                    AUTHENTICATING,
                    WAITING_ADMISSION,
                    JOINING_LOBBY ->
                    true;
            default -> false;
        };
    }
}
