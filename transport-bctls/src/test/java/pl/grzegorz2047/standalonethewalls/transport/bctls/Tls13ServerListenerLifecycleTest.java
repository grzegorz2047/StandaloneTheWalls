package pl.grzegorz2047.standalonethewalls.transport.bctls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.Test;

class Tls13ServerListenerLifecycleTest {
    private static final Provider CRYPTO_PROVIDER = new BouncyCastleProvider();

    @Test
    void closeBeforeStartReleasesOwnedResourcesAndPreventsLaterStart()
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    TlsTransportException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException {
        TestCertificateMaterial material = TestCertificateMaterial.create(CRYPTO_PROVIDER, 41L);
        Tls13ServerCredentials credentials =
                Tls13ServerCredentials.create(
                        material.keyPair().getPrivate(), List.of(material.certificate()));
        Tls13ServerListener listener =
                new Tls13ServerListener(
                        new Tls13ServerListenerConfig(
                                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                                4,
                                1,
                                1,
                                Duration.ofSeconds(1),
                                Duration.ofSeconds(1)),
                        credentials,
                        connection -> {
                            throw new AssertionError("no connection can be accepted before start");
                        },
                        event -> {
                            throw new AssertionError("clean pre-start close must not emit an event");
                        });

        listener.closeAsync().toCompletableFuture().get(5L, TimeUnit.SECONDS);

        assertThat(listener.isTerminated()).isTrue();
        assertThat(listener.failure()).isEmpty();
        assertThatIllegalStateException().isThrownBy(listener::start);
        listener.close();
    }
}
