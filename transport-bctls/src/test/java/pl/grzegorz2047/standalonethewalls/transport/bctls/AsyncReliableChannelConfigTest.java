package pl.grzegorz2047.standalonethewalls.transport.bctls;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AsyncReliableChannelConfigTest {
    @Test
    void rejectsUnsafeAdmissionAndCloseLimits() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AsyncReliableChannelConfig(0, 1L, Duration.ofSeconds(1)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AsyncReliableChannelConfig(1, 0L, Duration.ofSeconds(1)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AsyncReliableChannelConfig(1, 1L, Duration.ZERO));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new AsyncReliableChannelConfig(
                                        1, 1L, Duration.ofSeconds(31)));
    }
}
