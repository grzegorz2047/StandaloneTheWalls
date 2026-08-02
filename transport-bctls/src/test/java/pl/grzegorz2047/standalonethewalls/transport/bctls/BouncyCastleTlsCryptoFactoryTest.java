package pl.grzegorz2047.standalonethewalls.transport.bctls;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class BouncyCastleTlsCryptoFactoryTest {
    @Test
    void createsLowLevelTlsCryptoWithoutGlobalJsseRegistration() {
        var crypto = BouncyCastleTlsCryptoFactory.create(new SecureRandom());

        assertThat(crypto).isNotNull();
        assertThat(java.security.Security.getProvider("BCJSSE")).isNull();
    }
}
