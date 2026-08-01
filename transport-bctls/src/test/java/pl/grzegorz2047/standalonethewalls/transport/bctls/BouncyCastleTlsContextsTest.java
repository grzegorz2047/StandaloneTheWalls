package pl.grzegorz2047.standalonethewalls.transport.bctls;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class BouncyCastleTlsContextsTest {
    @Test
    void createsAnIsolatedBcJsseContext() throws TlsTransportException {
        BouncyCastleTlsContexts contexts = new BouncyCastleTlsContexts();

        var context = contexts.create(null, null, new SecureRandom());

        assertThat(context.getProvider().getName()).isEqualTo("BCJSSE");
    }
}
