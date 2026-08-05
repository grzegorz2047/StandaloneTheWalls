package pl.grzegorz2047.standalonethewalls.client.network;

/** Immediate local decision for attempting to request one realtime ticket. */
public enum RealtimeTicketSubmissionStatus {
    SUBMITTED,
    SESSION_CLOSED,
    REQUEST_IN_FLIGHT
}
