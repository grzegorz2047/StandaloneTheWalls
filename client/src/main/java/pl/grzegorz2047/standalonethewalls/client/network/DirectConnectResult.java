package pl.grzegorz2047.standalonethewalls.client.network;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionStatus;

/** Exactly one terminal result for a Direct Connect attempt. */
public sealed interface DirectConnectResult
        permits DirectConnectResult.Connected,
                DirectConnectResult.ConfirmationRequired,
                DirectConnectResult.Failed {
    record Connected(ConnectedLobbySession session, PlayerSessionAdmissionStatus admissionStatus)
            implements DirectConnectResult {
        public Connected {
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(admissionStatus, "admissionStatus");
            if (!admissionStatus.isAccepted()) {
                throw new IllegalArgumentException("connected result requires accepted admission");
            }
        }
    }

    record ConfirmationRequired(FirstUseConfirmation confirmation) implements DirectConnectResult {
        public ConfirmationRequired {
            Objects.requireNonNull(confirmation, "confirmation");
        }
    }

    record Failed(DirectConnectFailure failure) implements DirectConnectResult {
        public Failed {
            Objects.requireNonNull(failure, "failure");
        }
    }
}
