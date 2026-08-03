package pl.grzegorz2047.standalonethewalls.client.network;

import java.time.Instant;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerFingerprint;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;

/** Public server identity that requires one explicit user confirmation. */
public record FirstUseConfirmation(
        DirectConnectEndpoint endpoint,
        ServerId serverId,
        ServerFingerprint fingerprint,
        Instant expiresAt,
        DirectConnectConfirmationToken token) {
    public FirstUseConfirmation {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(token, "token");
    }
}
