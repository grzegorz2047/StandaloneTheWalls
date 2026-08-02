package pl.grzegorz2047.standalonethewalls.transport.bctls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class IdentityExchangeConfigTest {
    @Test
    void exposesSafeDefaults() {
        assertThat(IdentityExchangeConfig.DEFAULT.stepTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(IdentityExchangeConfig.DEFAULT.overallTimeout())
                .isEqualTo(Duration.ofSeconds(15));
        assertThat(IdentityExchangeConfig.DEFAULT.closeTimeout()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void acceptsDocumentedBoundaryValues() {
        IdentityExchangeConfig minimum =
                new IdentityExchangeConfig(
                        Duration.ofMillis(1), Duration.ofMillis(1), Duration.ofMillis(1));
        IdentityExchangeConfig maximum =
                new IdentityExchangeConfig(
                        Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofSeconds(30));

        assertThat(minimum.stepTimeout()).isEqualTo(Duration.ofMillis(1));
        assertThat(maximum.overallTimeout()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void rejectsUnsafeDurationsAndOverallShorterThanStep() {
        assertThatThrownBy(
                        () ->
                                new IdentityExchangeConfig(
                                        Duration.ZERO,
                                        Duration.ofSeconds(1),
                                        Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new IdentityExchangeConfig(
                                        Duration.ofNanos(1),
                                        Duration.ofSeconds(1),
                                        Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new IdentityExchangeConfig(
                                        Duration.ofSeconds(31),
                                        Duration.ofMinutes(1),
                                        Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new IdentityExchangeConfig(
                                        Duration.ofSeconds(2),
                                        Duration.ofSeconds(1),
                                        Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new IdentityExchangeConfig(
                                        Duration.ofSeconds(1),
                                        Duration.ofMinutes(1).plusMillis(1),
                                        Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
