package pl.grzegorz2047.standalonethewalls.transport.bctls.realtime;

import java.util.Objects;
import java.util.Optional;

/** Public one-time redemption outcome without distinguishing unknown identities from replays. */
public final class RealtimeTicketRedemption {
    private static final RealtimeTicketRedemption UNKNOWN =
            new RealtimeTicketRedemption(Status.UNKNOWN_OR_REPLAYED, null);
    private static final RealtimeTicketRedemption EXPIRED =
            new RealtimeTicketRedemption(Status.EXPIRED, null);

    private final Status status;
    private final RedeemedRealtimeTicket ticket;

    private RealtimeTicketRedemption(Status status, RedeemedRealtimeTicket ticket) {
        this.status = Objects.requireNonNull(status, "status");
        this.ticket = ticket;
    }

    public static RealtimeTicketRedemption redeemed(RedeemedRealtimeTicket ticket) {
        return new RealtimeTicketRedemption(Status.REDEEMED, Objects.requireNonNull(ticket, "ticket"));
    }

    public static RealtimeTicketRedemption unknownOrReplayed() {
        return UNKNOWN;
    }

    public static RealtimeTicketRedemption expired() {
        return EXPIRED;
    }

    public Status status() {
        return status;
    }

    public Optional<RedeemedRealtimeTicket> ticket() {
        return Optional.ofNullable(ticket);
    }

    @Override
    public String toString() {
        return "RealtimeTicketRedemption[status=" + status + ']';
    }

    public enum Status {
        REDEEMED,
        UNKNOWN_OR_REPLAYED,
        EXPIRED
    }
}
