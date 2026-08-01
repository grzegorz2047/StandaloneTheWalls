package pl.grzegorz2047.standalonethewalls.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class ProtocolVersionTest {
    @Test
    void supportsOnlyTheFrozenCurrentVersion() {
        assertThat(ProtocolVersion.CURRENT.isSupported()).isTrue();
        assertThat(new ProtocolVersion(1, 1).isSupported()).isFalse();
        assertThat(new ProtocolVersion(2, 0).isSupported()).isFalse();
    }

    @Test
    void acceptsUnsignedShortBoundariesAndRejectsValuesOutsideThem() {
        assertThat(new ProtocolVersion(0, 0)).isEqualTo(new ProtocolVersion(0, 0));
        assertThat(new ProtocolVersion(0xFFFF, 0xFFFF))
                .isEqualTo(new ProtocolVersion(0xFFFF, 0xFFFF));
        assertThatIllegalArgumentException().isThrownBy(() -> new ProtocolVersion(-1, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new ProtocolVersion(0x10000, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new ProtocolVersion(0, -1));
        assertThatIllegalArgumentException().isThrownBy(() -> new ProtocolVersion(0, 0x10000));
    }
}
