package pl.grzegorz2047.standalonethewalls.protocol.realtime;

/** Client request for one server-provisioned realtime transport credential. */
public record RealtimeTicketRequest(long requestId, int profileVersion) {
    public RealtimeTicketRequest {
        if (requestId < 1L) {
            throw new IllegalArgumentException("requestId must be positive");
        }
        if (profileVersion < 1 || profileVersion > 0xFF) {
            throw new IllegalArgumentException("profileVersion must fit a positive unsigned byte");
        }
    }
}
