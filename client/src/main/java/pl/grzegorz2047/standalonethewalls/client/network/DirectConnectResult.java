package pl.grzegorz2047.standalonethewalls.client.network;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionStatus;

/** Exactly one terminal result for a Direct Connect attempt. */
public sealed interface DirectConnectResult
        permits DirectConnectResult.Connected,
                DirectConnectResult.ConfirmationRequired,
                DirectConnectResult.Failed {

    /** One-shot ownership transfer for a connected mutable lobby session. */
    final class Connected implements DirectConnectResult {
        private final AtomicReference<ConnectedLobbySession> session;
        private final PlayerSessionAdmissionStatus admissionStatus;

        public Connected(
                ConnectedLobbySession session, PlayerSessionAdmissionStatus admissionStatus) {
            this.session = new AtomicReference<>(Objects.requireNonNull(session, "session"));
            this.admissionStatus = Objects.requireNonNull(admissionStatus, "admissionStatus");
            if (!admissionStatus.isAccepted()) {
                throw new IllegalArgumentException("connected result requires accepted admission");
            }
        }

        public ConnectedLobbySession takeSession() {
            ConnectedLobbySession transferred = session.getAndSet(null);
            if (transferred == null) {
                throw new IllegalStateException("connected lobby session was already transferred");
            }
            return transferred;
        }

        public PlayerSessionAdmissionStatus admissionStatus() {
            return admissionStatus;
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
