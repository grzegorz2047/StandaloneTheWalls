package pl.grzegorz2047.standalonethewalls.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProtocolCodecTest {
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000123");

    @Test
    void roundTripsAnEnvelopeWithoutExposingMutablePayloadState() throws ProtocolException {
        byte[] source = {1, 2, 3};
        ProtocolEnvelope original = envelope(MessageType.CLIENT_HELLO, 7L, source);
        source[0] = 99;

        ProtocolEnvelope decoded = ProtocolCodec.decode(ProtocolCodec.encode(original));
        byte[] returned = decoded.payload();
        returned[1] = 99;

        assertThat(decoded).isEqualTo(original);
        assertThat(decoded.payload()).containsExactly(1, 2, 3);
        assertThat(decoded.toString()).contains("payloadBytes=3").doesNotContain("1, 2, 3");
    }

    @Test
    void rejectsUnknownVersionsTypesFlagsAndNegativeSequences() {
        assertDecodeCode(
                mutateShort(validBytes(), 4, 2), ProtocolException.Code.UNSUPPORTED_VERSION);
        assertDecodeCode(
                mutateShort(validBytes(), 8, 999), ProtocolException.Code.UNKNOWN_MESSAGE_TYPE);
        assertDecodeCode(mutateShort(validBytes(), 10, 1), ProtocolException.Code.INVALID_FLAGS);
        assertDecodeCode(
                mutateLong(validBytes(), 28, -1L), ProtocolException.Code.INVALID_SEQUENCE);
    }

    @Test
    void rejectsInvalidMagicTruncationAndTrailingBytes() {
        assertDecodeCode(mutateInt(validBytes(), 0, 0), ProtocolException.Code.INVALID_MAGIC);
        assertDecodeCode(
                Arrays.copyOf(validBytes(), ProtocolCodec.HEADER_BYTES - 1),
                ProtocolException.Code.TRUNCATED_MESSAGE);
        assertDecodeCode(
                Arrays.copyOf(validBytes(), validBytes().length + 1),
                ProtocolException.Code.TRAILING_BYTES);
    }

    @Test
    void rejectsNegativeOversizedAndIncompletePayloadDeclarations() {
        assertDecodeCode(mutateInt(validBytes(), 36, -1), ProtocolException.Code.INVALID_LENGTH);
        assertDecodeCode(
                mutateInt(validBytes(), 36, MessageType.CLIENT_HELLO.maximumPayloadBytes() + 1),
                ProtocolException.Code.INVALID_LENGTH);
        assertDecodeCode(mutateInt(validBytes(), 36, 4), ProtocolException.Code.TRUNCATED_MESSAGE);
    }

    @Test
    void rejectsEnvelopeConstructionOutsideItsInvariants() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> envelope(MessageType.PING, -1L, new byte[0]));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                envelope(
                                        MessageType.PING,
                                        0L,
                                        new byte[MessageType.PING.maximumPayloadBytes() + 1]));
    }

    @Test
    void everyTruncatedPrefixFailsWithABoundedProtocolException() {
        byte[] encoded = validBytes();

        for (int length = 0; length < encoded.length; length++) {
            byte[] prefix = Arrays.copyOf(encoded, length);
            assertThatThrownBy(() -> ProtocolCodec.decode(prefix))
                    .isInstanceOf(ProtocolException.class);
        }
    }

    private static byte[] validBytes() {
        return ProtocolCodec.encode(envelope(MessageType.CLIENT_HELLO, 7L, new byte[] {1, 2, 3}));
    }

    private static ProtocolEnvelope envelope(MessageType type, long sequence, byte[] payload) {
        return new ProtocolEnvelope(ProtocolVersion.CURRENT, type, SESSION_ID, sequence, payload);
    }

    private static byte[] mutateShort(byte[] bytes, int offset, int value) {
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putShort(offset, (short) value);
        return bytes;
    }

    private static byte[] mutateInt(byte[] bytes, int offset, int value) {
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putInt(offset, value);
        return bytes;
    }

    private static byte[] mutateLong(byte[] bytes, int offset, long value) {
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putLong(offset, value);
        return bytes;
    }

    private static void assertDecodeCode(byte[] encoded, ProtocolException.Code expectedCode) {
        assertThatThrownBy(() -> ProtocolCodec.decode(encoded))
                .isInstanceOfSatisfying(
                        ProtocolException.class,
                        exception -> assertThat(exception.code()).isEqualTo(expectedCode));
    }
}
