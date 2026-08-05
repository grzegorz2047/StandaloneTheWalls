package pl.grzegorz2047.standalonethewalls.server.identity.session;

import java.util.UUID;
import java.util.concurrent.CompletionStage;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableChannel;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.SecureChannelBinding;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;

/** Server-internal view that may be created only after cryptographic identity proof succeeds. */
public interface AuthenticatedPlayerSession {
    UUID sessionId();

    ServerId serverId();

    PlayerId playerId();

    SecureChannelBinding channelBinding();

    CanonicalHandle handle();

    ReliableChannel reliableChannel();

    boolean isOpen();

    CompletionStage<Void> closeAsync();
}
