package pl.grzegorz2047.standalonethewalls.protocol.realtime;

import java.util.Objects;
import java.util.Optional;

/** Exact provisioning result with mutually exclusive issued and rejected variants. */
public final class RealtimeTicketResult implements AutoCloseable {
    private final long requestId;
    private final int profileVersion;
    private final RealtimeTicketResultStatus status;
    private final ClientRealtimeTicket ticket;
    private final RealtimeTicketRejection rejection;

    private RealtimeTicketResult(
            long requestId,
            int profileVersion,
            RealtimeTicketResultStatus status,
            ClientRealtimeTicket ticket,
            RealtimeTicketRejection rejection) {
        if (requestId < 1L) {
            throw new IllegalArgumentException("requestId must be positive");
        }
        if (profileVersion < 1 || profileVersion > 0xFF) {
            throw new IllegalArgumentException("profileVersion must fit a positive unsigned byte");
        }
        this.requestId = requestId;
        this.profileVersion = profileVersion;
        this.status = Objects.requireNonNull(status, "status");
        this.ticket = ticket;
        this.rejection = rejection;
        boolean issued = status == RealtimeTicketResultStatus.ISSUED;
        if ((issued && (ticket == null || rejection != null))
                || (!issued && (ticket != null || rejection == null))) {
            throw new IllegalArgumentException("result variant fields are inconsistent");
        }
    }

    public static RealtimeTicketResult issued(ClientRealtimeTicket ticket) {
        ClientRealtimeTicket value = Objects.requireNonNull(ticket, "ticket");
        return new RealtimeTicketResult(
                value.requestId(),
                value.profileVersion(),
                RealtimeTicketResultStatus.ISSUED,
                value,
                null);
    }

    public static RealtimeTicketResult rejected(
            long requestId, int profileVersion, RealtimeTicketRejection rejection) {
        return new RealtimeTicketResult(
                requestId,
                profileVersion,
                RealtimeTicketResultStatus.REJECTED,
                null,
                Objects.requireNonNull(rejection, "rejection"));
    }

    public long requestId() {
        return requestId;
    }

    public int profileVersion() {
        return profileVersion;
    }

    public RealtimeTicketResultStatus status() {
        return status;
    }

    public Optional<ClientRealtimeTicket> ticket() {
        return Optional.ofNullable(ticket);
    }

    public Optional<RealtimeTicketRejection> rejection() {
        return Optional.ofNullable(rejection);
    }

    @Override
    public void close() {
        if (ticket != null) {
            ticket.close();
        }
    }

    @Override
    public String toString() {
        return "RealtimeTicketResult[requestId="
                + requestId
                + ", profileVersion="
                + profileVersion
                + ", status="
                + status
                + ", ticket="
                + (ticket == null ? "none" : "redacted")
                + ", rejection="
                + rejection
                + ']';
    }
}
