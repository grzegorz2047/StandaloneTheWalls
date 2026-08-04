package pl.grzegorz2047.standalonethewalls.transport.bctls.realtime;

/** Injected entropy source so ticket generation is deterministic under tests. */
@FunctionalInterface
public interface RealtimeTicketEntropy {
    byte[] randomBytes(int length);
}
