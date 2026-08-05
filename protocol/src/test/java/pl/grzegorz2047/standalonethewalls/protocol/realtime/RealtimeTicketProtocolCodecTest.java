package pl.grzegorz2047.standalonethewalls.protocol.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class RealtimeTicketProtocolCodecTest {
    private static final Instant EXPIRATION = Instant.parse("2026-08-05T08:00:00Z");

    @Test
    void requestUsesExactBigEndianVector() throws RealtimeTicketProtocolException {
        RealtimeTicketRequest request = new RealtimeTicketRequest(1L, 1);

        byte[] encoded = RealtimeTicketProtocolCodec.encodeRequest(request);

        assertThat(HexFormat.of().formatHex(encoded)).isEqualTo("01010000000000000001");
        assertThat(RealtimeTicketProtocolCodec.decodeRequest(encoded)).isEqualTo(request);
    }

    @Test
    void issuedAndRejectedResultsRoundTripWithExactSizes() throws RealtimeTicketProtocolException {
        byte[] identity = filled(ClientRealtimeTicket.IDENTITY_BYTES, 2);
        byte[] preSharedKey = filled(ClientRealtimeTicket.PRE_SHARED_KEY_BYTES, 3);

        byte[] issued =
                RealtimeTicketProtocolCodec.encodeIssued(7L, 1, identity, preSharedKey, EXPIRATION);
        byte[] rejected =
                RealtimeTicketProtocolCodec.encodeRejected(
                        8L, 9, RealtimeTicketRejection.UNSUPPORTED_PROFILE);

        assertThat(issued).hasSize(RealtimeTicketProtocolCodec.ISSUED_RESULT_BYTES);
        assertThat(rejected).hasSize(RealtimeTicketProtocolCodec.REJECTED_RESULT_BYTES);
        try (RealtimeTicketResult decoded = RealtimeTicketProtocolCodec.decodeResult(issued)) {
            assertThat(decoded.status()).isEqualTo(RealtimeTicketResultStatus.ISSUED);
            ClientRealtimeTicket ticket = decoded.ticket().orElseThrow();
            assertThat(ticket.requestId()).isEqualTo(7L);
            assertThat(ticket.profileVersion()).isEqualTo(1);
            assertThat(ticket.copyIdentity()).containsOnly(2);
            assertThat(ticket.copyPreSharedKey()).containsOnly(3);
            assertThat(ticket.expiresAt()).isEqualTo(EXPIRATION);
            assertThat(decoded.toString()).contains("redacted").doesNotContain("03030303");
        }
        try (RealtimeTicketResult decoded = RealtimeTicketProtocolCodec.decodeResult(rejected)) {
            assertThat(decoded.status()).isEqualTo(RealtimeTicketResultStatus.REJECTED);
            assertThat(decoded.requestId()).isEqualTo(8L);
            assertThat(decoded.profileVersion()).isEqualTo(9);
            assertThat(decoded.rejection()).contains(RealtimeTicketRejection.UNSUPPORTED_PROFILE);
            assertThat(decoded.ticket()).isEmpty();
        }
    }

    @Test
    void decoderRejectsTruncationTrailingBytesUnknownValuesAndInvalidFields() {
        byte[] validRequest =
                RealtimeTicketProtocolCodec.encodeRequest(new RealtimeTicketRequest(1L, 1));
        byte[] validRejected =
                RealtimeTicketProtocolCodec.encodeRejected(
                        2L, 1, RealtimeTicketRejection.TEMPORARILY_UNAVAILABLE);

        assertCode(
                Arrays.copyOf(validRequest, validRequest.length - 1),
                RealtimeTicketProtocolException.Code.INVALID_SIZE,
                true);
        assertCode(
                Arrays.copyOf(validRequest, validRequest.length + 1),
                RealtimeTicketProtocolException.Code.INVALID_SIZE,
                true);
        byte[] unknownSchema = validRequest.clone();
        unknownSchema[0] = 2;
        assertCode(unknownSchema, RealtimeTicketProtocolException.Code.UNSUPPORTED_SCHEMA, true);
        byte[] zeroProfile = validRequest.clone();
        zeroProfile[1] = 0;
        assertCode(zeroProfile, RealtimeTicketProtocolException.Code.INVALID_PROFILE, true);
        byte[] zeroRequest = validRequest.clone();
        Arrays.fill(zeroRequest, 2, zeroRequest.length, (byte) 0);
        assertCode(zeroRequest, RealtimeTicketProtocolException.Code.INVALID_REQUEST_ID, true);

        byte[] unknownStatus = validRejected.clone();
        unknownStatus[1] = 99;
        assertCode(unknownStatus, RealtimeTicketProtocolException.Code.INVALID_STATUS, false);
        byte[] unknownRejection = validRejected.clone();
        unknownRejection[3] = 99;
        assertCode(unknownRejection, RealtimeTicketProtocolException.Code.INVALID_REJECTION, false);
    }

    @Test
    void clientTicketDefensivelyCopiesAndDestroysOnlyTheSecret() {
        byte[] identity = filled(ClientRealtimeTicket.IDENTITY_BYTES, 4);
        byte[] preSharedKey = filled(ClientRealtimeTicket.PRE_SHARED_KEY_BYTES, 5);
        ClientRealtimeTicket ticket =
                new ClientRealtimeTicket(3L, 1, identity, preSharedKey, EXPIRATION);
        identity[0] = 99;
        preSharedKey[0] = 99;
        byte[] copy = ticket.copyPreSharedKey();
        copy[0] = 99;

        assertThat(ticket.copyIdentity()).containsOnly(4);
        assertThat(ticket.copyPreSharedKey()).containsOnly(5);
        assertThat(ticket.toString()).contains("redacted").doesNotContain("05050505");

        ticket.close();
        ticket.close();

        assertThat(ticket.isDestroyed()).isTrue();
        assertThat(ticket.copyIdentity()).containsOnly(4);
        assertThatIllegalStateException().isThrownBy(ticket::copyPreSharedKey);
    }

    private static void assertCode(
            byte[] payload, RealtimeTicketProtocolException.Code code, boolean request) {
        assertThatThrownBy(
                        () -> {
                            if (request) {
                                RealtimeTicketProtocolCodec.decodeRequest(payload);
                            } else {
                                RealtimeTicketProtocolCodec.decodeResult(payload);
                            }
                        })
                .isInstanceOf(RealtimeTicketProtocolException.class)
                .extracting(exception -> ((RealtimeTicketProtocolException) exception).code())
                .isEqualTo(code);
    }

    private static byte[] filled(int length, int value) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }
}
