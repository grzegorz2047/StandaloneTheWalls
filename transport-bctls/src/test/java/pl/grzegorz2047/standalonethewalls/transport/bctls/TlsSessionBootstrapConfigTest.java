package pl.grzegorz2047.standalonethewalls.transport.bctls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TlsSessionBootstrapConfigTest {
    @Test
    void acceptsBoundedMillisecondTimeouts() {
        TlsSessionBootstrapConfig minimum =
                new TlsSessionBootstrapConfig(Duration.ofMillis(1));
        TlsSessionBootstrapConfig maximum =
                new TlsSessionBootstrapConfig(Duration.ofSeconds(30));

        assertThat(minimum.timeoutMillis()).isEqualTo(1);
        assertThat(maximum.timeoutMillis()).isEqualTo(30_000);
        assertThat(TlsSessionBootstrapConfig.DEFAULT.timeoutMillis()).isEqualTo(5_000);
    }

    @Test
    void rejectsZeroSubMillisecondNegativeAndOverMaximumTimeouts() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TlsSessionBootstrapConfig(Duration.ZERO));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TlsSessionBootstrapConfig(Duration.ofNanos(1)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TlsSessionBootstrapConfig(Duration.ofMillis(-1)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TlsSessionBootstrapConfig(Duration.ofSeconds(31)));
    }
}
