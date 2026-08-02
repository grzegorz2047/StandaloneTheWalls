package pl.grzegorz2047.standalonethewalls.transport.bctls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TlsSessionBootstrapCodecTest {
    private static final UUID SESSION_ID = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final String OFFER_HEX =
            "53465342000100010001000011111111222243338444555555555555";
    private static final String ACCEPT_HEX =
            "53465342000100020001000011111111222243338444555555555555";

    @Test
    void matchesThePublicOfferAndAcceptVectors() throws TlsSessionBootstrapException {
        byte[] offer = TlsSessionBootstrapCodec.encodeOffer(SESSION_ID);
        byte[] accept = TlsSessionBootstrapCodec.encodeAccept(SESSION_ID);

        assertThat(offer).hasSize(TlsSessionBootstrapCodec.RECORD_BYTES);
        assertThat(HexFormat.of().formatHex(offer)).isEqualTo(OFFER_HEX);
        assertThat(HexFormat.of().formatHex(accept)).isEqualTo(ACCEPT_HEX);
        assertThat(TlsSessionBootstrapCodec.decodeOffer(offer)).isEqualTo(SESSION_ID);
        assertThat(TlsSessionBootstrapCodec.decodeAccept(accept)).isEqualTo(SESSION_ID);
    }

    @Test
    void rejectsWrongSizeMagicSchemaTypeAndProtocol() {
        assertCode(
                Arrays.copyOf(
                        TlsSessionBootstrapCodec.encodeOffer(SESSION_ID),
                        TlsSessionBootstrapCodec.RECORD_BYTES - 1),
                TlsSessionBootstrapException.Code.INVALID_RECORD_SIZE);
        assertCode(
                mutateInt(TlsSessionBootstrapCodec.encodeOffer(SESSION_ID), 0, 0),
                TlsSessionBootstrapException.Code.INVALID_MAGIC);
        assertCode(
                mutateShort(TlsSessionBootstrapCodec.encodeOffer(SESSION_ID), 4, 2),
                TlsSessionBootstrapException.Code.UNSUPPORTED_SCHEMA);
        assertCode(
                TlsSessionBootstrapCodec.encodeAccept(SESSION_ID),
                TlsSessionBootstrapException.Code.UNEXPECTED_RECORD_TYPE);
        assertCode(
                mutateShort(TlsSessionBootstrapCodec.encodeOffer(SESSION_ID), 8, 2),
                TlsSessionBootstrapException.Code.UNSUPPORTED_PROTOCOL);
    }

    @Test
    void rejectsZeroNonV4AndNonRfcVariantSessionIds() {
        assertCode(
                replaceUuid(TlsSessionBootstrapCodec.encodeOffer(SESSION_ID), new UUID(0L, 0L)),
                TlsSessionBootstrapException.Code.INVALID_SESSION_ID);
        assertCode(
                replaceUuid(
                        TlsSessionBootstrapCodec.encodeOffer(SESSION_ID),
                        UUID.fromString("11111111-2222-3333-8444-555555555555")),
                TlsSessionBootstrapException.Code.INVALID_SESSION_ID);
        assertCode(
                replaceUuid(
                        TlsSessionBootstrapCodec.encodeOffer(SESSION_ID),
                        UUID.fromString("11111111-2222-4333-0444-555555555555")),
                TlsSessionBootstrapException.Code.INVALID_SESSION_ID);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> TlsSessionBootstrapCodec.encodeOffer(new UUID(0L, 0L)));
    }

    @Test
    void boundedErrorsNeverIncludeRecordBytes() {
        byte[] invalid = mutateInt(TlsSessionBootstrapCodec.encodeOffer(SESSION_ID), 0, 0);

        assertThatThrownBy(() -> TlsSessionBootstrapCodec.decodeOffer(invalid))
                .isInstanceOfSatisfying(
                        TlsSessionBootstrapException.class,
                        exception -> {
                            assertThat(exception.code())
                                    .isEqualTo(TlsSessionBootstrapException.Code.INVALID_MAGIC);
                            assertThat(exception.getMessage())
                                    .doesNotContain(HexFormat.of().formatHex(invalid));
                        });
    }

    private static void assertCode(byte[] record, TlsSessionBootstrapException.Code expected) {
        assertThatThrownBy(() -> TlsSessionBootstrapCodec.decodeOffer(record))
                .isInstanceOfSatisfying(
                        TlsSessionBootstrapException.class,
                        exception -> assertThat(exception.code()).isEqualTo(expected));
    }

    private static byte[] mutateShort(byte[] record, int offset, int value) {
        ByteBuffer.wrap(record).order(ByteOrder.BIG_ENDIAN).putShort(offset, (short) value);
        return record;
    }

    private static byte[] mutateInt(byte[] record, int offset, int value) {
        ByteBuffer.wrap(record).order(ByteOrder.BIG_ENDIAN).putInt(offset, value);
        return record;
    }

    private static byte[] replaceUuid(byte[] record, UUID value) {
        ByteBuffer buffer = ByteBuffer.wrap(record).order(ByteOrder.BIG_ENDIAN);
        buffer.putLong(12, value.getMostSignificantBits());
        buffer.putLong(20, value.getLeastSignificantBits());
        return record;
    }
}
