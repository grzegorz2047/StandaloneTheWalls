package pl.grzegorz2047.standalonethewalls.protocol;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** Immutable decoded protocol message. */
public final class ProtocolEnvelope {
    private final ProtocolVersion version;
    private final MessageType messageType;
    private final UUID sessionId;
    private final long sequence;
    private final byte[] payload;

    public ProtocolEnvelope(
            ProtocolVersion version,
            MessageType messageType,
            UUID sessionId,
            long sequence,
            byte[] payload) {
        this.version = Objects.requireNonNull(version, "version");
        this.messageType = Objects.requireNonNull(messageType, "messageType");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        if (sequence < 0L) {
            throw new IllegalArgumentException("sequence cannot be negative");
        }
        this.sequence = sequence;
        this.payload = Objects.requireNonNull(payload, "payload").clone();
        if (payload.length > ProtocolCodec.MAXIMUM_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("payload exceeds global maximum");
        }
        if (payload.length > messageType.maximumPayloadBytes()) {
            throw new IllegalArgumentException("payload exceeds message-type maximum");
        }
    }

    public ProtocolVersion version() {
        return version;
    }

    public MessageType messageType() {
        return messageType;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public long sequence() {
        return sequence;
    }

    public byte[] payload() {
        return payload.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProtocolEnvelope that)) {
            return false;
        }
        return sequence == that.sequence
                && version.equals(that.version)
                && messageType == that.messageType
                && sessionId.equals(that.sessionId)
                && Arrays.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(version, messageType, sessionId, sequence);
        return 31 * result + Arrays.hashCode(payload);
    }

    @Override
    public String toString() {
        return "ProtocolEnvelope[version="
                + version
                + ", messageType="
                + messageType
                + ", sessionId="
                + sessionId
                + ", sequence="
                + sequence
                + ", payloadBytes="
                + payload.length
                + ']';
    }
}
