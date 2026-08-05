package pl.grzegorz2047.standalonethewalls.client.network;

import java.util.Objects;
import java.util.Optional;

/** Immediate bounded result of attempting to submit one realtime ticket request. */
public record RealtimeTicketSubmission(
        RealtimeTicketSubmissionStatus status, Optional<RealtimeTicketHandle> handle) {
    public RealtimeTicketSubmission {
        Objects.requireNonNull(status, "status");
        handle = Objects.requireNonNull(handle, "handle");
        if ((status == RealtimeTicketSubmissionStatus.SUBMITTED) != handle.isPresent()) {
            throw new IllegalArgumentException(
                    "only a submitted realtime request may contain a handle");
        }
    }

    public static RealtimeTicketSubmission submitted(RealtimeTicketHandle handle) {
        return new RealtimeTicketSubmission(
                RealtimeTicketSubmissionStatus.SUBMITTED,
                Optional.of(Objects.requireNonNull(handle, "handle")));
    }

    public static RealtimeTicketSubmission rejected(RealtimeTicketSubmissionStatus status) {
        RealtimeTicketSubmissionStatus rejection = Objects.requireNonNull(status, "status");
        if (rejection == RealtimeTicketSubmissionStatus.SUBMITTED) {
            throw new IllegalArgumentException("submitted status requires a request handle");
        }
        return new RealtimeTicketSubmission(rejection, Optional.empty());
    }
}
