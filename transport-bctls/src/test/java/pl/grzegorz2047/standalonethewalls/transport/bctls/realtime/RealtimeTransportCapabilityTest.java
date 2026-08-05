package pl.grzegorz2047.standalonethewalls.transport.bctls.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RealtimeTransportCapabilityTest {
    @Test
    void requiresConsistentAvailabilityAndReason() {
        assertThatThrownBy(
                        () ->
                                new RealtimeTransportCapability(
                                        true,
                                        "provider",
                                        "1.0",
                                        RealtimeTransportCapability.Reason.EXPLICITLY_DISABLED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                RealtimeTransportCapability.unavailable(
                                        "provider",
                                        "1.0",
                                        RealtimeTransportCapability.Reason.AVAILABLE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void boundsProviderMetadataAndKeepsTextSecretFree() {
        RealtimeTransportCapability capability =
                RealtimeTransportCapability.unavailable(
                        "org.example:provider",
                        "1.0",
                        RealtimeTransportCapability.Reason.PROVIDER_VERSION_REQUIRES_REVIEW);

        assertThat(capability.available()).isFalse();
        assertThat(capability.toString())
                .contains("PROVIDER_VERSION_REQUIRES_REVIEW")
                .doesNotContain("secret", "psk", "cookie");
        assertThatThrownBy(() -> RealtimeTransportCapability.available("provider\nname", "1.0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RealtimeTransportCapability.available("p".repeat(97), "1.0"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
