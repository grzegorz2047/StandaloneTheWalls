package pl.grzegorz2047.standalonethewalls.protocol.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PlayerSessionAdmissionCodecTest {
    @Test
    void roundTripsEveryStableStatus() throws PlayerSessionAdmissionException {
        for (PlayerSessionAdmissionStatus status : PlayerSessionAdmissionStatus.values()) {
            byte[] encoded = PlayerSessionAdmissionCodec.encode(status);

            assertThat(encoded).hasSize(PlayerSessionAdmissionCodec.PAYLOAD_BYTES);
            assertThat(PlayerSessionAdmissionCodec.decode(encoded)).isEqualTo(status);
            assertThat(PlayerSessionAdmissionStatus.fromWireId(status.wireId())).contains(status);
        }
    }

    @Test
    void rejectsWrongSizeUnknownSchemaAndUnknownStatus() {
        assertThatThrownBy(() -> PlayerSessionAdmissionCodec.decode(new byte[] {1}))
                .isInstanceOf(PlayerSessionAdmissionException.class)
                .extracting("code")
                .isEqualTo(PlayerSessionAdmissionException.Code.INVALID_SIZE);
        assertThatThrownBy(() -> PlayerSessionAdmissionCodec.decode(new byte[] {2, 1}))
                .isInstanceOf(PlayerSessionAdmissionException.class)
                .extracting("code")
                .isEqualTo(PlayerSessionAdmissionException.Code.UNSUPPORTED_SCHEMA);
        assertThatThrownBy(() -> PlayerSessionAdmissionCodec.decode(new byte[] {1, 127}))
                .isInstanceOf(PlayerSessionAdmissionException.class)
                .extracting("code")
                .isEqualTo(PlayerSessionAdmissionException.Code.INVALID_STATUS);
    }
}
