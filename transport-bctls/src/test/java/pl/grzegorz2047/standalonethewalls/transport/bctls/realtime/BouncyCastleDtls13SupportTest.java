package pl.grzegorz2047.standalonethewalls.transport.bctls.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BouncyCastleDtls13SupportTest {
    @Test
    void pinnedProviderFailsClosedUntilItsDtls13ServerPathIsReviewed() {
        RealtimeTransportCapability capability = BouncyCastleDtls13Support.current();

        assertThat(capability.available()).isFalse();
        assertThat(capability.providerId())
                .isEqualTo("org.bouncycastle:bcprov-jdk18on+org.bouncycastle:bctls-jdk18on");
        assertThat(capability.providerVersion())
                .isEqualTo(BouncyCastleDtls13Support.REVIEWED_PROVIDER_VERSION);
        assertThat(capability.reason())
                .isEqualTo(RealtimeTransportCapability.Reason.DTLS_1_3_NOT_IMPLEMENTED);
        assertThat(capability.toString()).doesNotContain("psk", "cookie");
    }

    @Test
    void unreviewedProviderVersionCannotEnableRealtimeTransport() {
        RealtimeTransportCapability capability = BouncyCastleDtls13Support.evaluate("1.85");

        assertThat(capability.available()).isFalse();
        assertThat(capability.providerVersion()).isEqualTo("1.85");
        assertThat(capability.reason())
                .isEqualTo(RealtimeTransportCapability.Reason.PROVIDER_VERSION_REQUIRES_REVIEW);
    }
}
