package pl.grzegorz2047.standalonethewalls.transport.bctls.realtime;

import java.util.Objects;
import java.util.UUID;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;

/** Server-owned context binding one realtime ticket to an authenticated reliable session. */
public record RealtimeTicketContext(
        ServerId serverId,
        UUID reliableSessionId,
        PlayerId playerId,
        RealtimeChannelBindingDigest channelBindingDigest,
        long roundEpoch) {
    public RealtimeTicketContext {
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(reliableSessionId, "reliableSessionId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(channelBindingDigest, "channelBindingDigest");
        if (reliableSessionId.getMostSignificantBits() == 0L
                && reliableSessionId.getLeastSignificantBits() == 0L) {
            throw new IllegalArgumentException("reliableSessionId must not be zero");
        }
        if (roundEpoch < 0L) {
            throw new IllegalArgumentException("roundEpoch cannot be negative");
        }
    }
}
