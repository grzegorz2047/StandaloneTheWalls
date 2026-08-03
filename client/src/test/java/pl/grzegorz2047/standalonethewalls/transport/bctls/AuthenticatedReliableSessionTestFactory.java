package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.util.Objects;
import java.util.UUID;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableChannel;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.SecureChannelBinding;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;

/** Test-only construction bridge for the transport-owned authenticated session type. */
public final class AuthenticatedReliableSessionTestFactory {
    private static final UUID SESSION_ID =
            UUID.fromString("12345678-1234-4234-8234-1234567890ab");
    private static final ServerId SERVER_ID = new ServerId("sfs1_" + "b".repeat(52));

    private AuthenticatedReliableSessionTestFactory() {
        throw new AssertionError("No instances");
    }

    public static AuthenticatedReliableSession create(
            ReliableChannel channel, PlayerId playerId, CanonicalHandle handle) {
        Objects.requireNonNull(channel, "channel");
        Tls13SessionSecurity security =
                new Tls13SessionSecurity(
                        SERVER_ID,
                        new SecureChannelBinding(new byte[SecureChannelBinding.BYTES]),
                        "TLS_AES_128_GCM_SHA256",
                        "sunderfront/1");
        BootstrappedReliableSession bootstrapped =
                new BootstrappedReliableSession(SESSION_ID, security, channel);
        return new AuthenticatedReliableSession(bootstrapped, playerId, handle);
    }
}
