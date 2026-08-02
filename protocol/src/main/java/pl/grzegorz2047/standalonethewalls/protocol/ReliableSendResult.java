package pl.grzegorz2047.standalonethewalls.protocol;

/** Sequence assigned atomically by a reliable transport implementation. */
public record ReliableSendResult(long sequence) {
    public ReliableSendResult {
        if (sequence < 0L) {
            throw new IllegalArgumentException("sequence cannot be negative");
        }
    }
}
