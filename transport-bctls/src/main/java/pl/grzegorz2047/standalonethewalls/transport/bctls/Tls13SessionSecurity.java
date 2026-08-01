package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.identity.SecureChannelBinding;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;

/** Public security metadata for one completed TLS 1.3 connection. */
public record Tls13SessionSecurity(
        ServerId serverId,
        SecureChannelBinding channelBinding,
        String cipherSuite,
        String applicationProtocol) {
    public Tls13SessionSecurity {
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(channelBinding, "channelBinding");
        Objects.requireNonNull(cipherSuite, "cipherSuite");
        Objects.requireNonNull(applicationProtocol, "applicationProtocol");
    }
}
