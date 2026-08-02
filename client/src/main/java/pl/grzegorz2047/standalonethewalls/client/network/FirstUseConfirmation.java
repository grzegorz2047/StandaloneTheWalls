package pl.grzegorz2047.standalonethewalls.client.network;

import java.time.Instant;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;

/** Server identity that must be shown to the user before one explicit reconnect. */
public record FirstUseConfirmation(
        DirectConnectEndpoint endpoint,
        ServerId serverId,
        String fingerprint,
        Instant expiresAt,
        DirectConnectConfirmationToken token) {
    public FirstUseConfirmation {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(serverId, "serverId");
        fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(token, "token");
        if (!fingerprint.equals(serverId.value())) {
            throw new IllegalArgumentException("fingerprint must be the complete stable serverId");
        }
    }
}
