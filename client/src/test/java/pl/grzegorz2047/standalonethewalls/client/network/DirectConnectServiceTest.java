package pl.grzegorz2047.standalonethewalls.client.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.grzegorz2047.standalonethewalls.client.identity.ClientIdentityStorage;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;

class DirectConnectServiceTest {
    private static final Duration WAIT = Duration.ofSeconds(5);

    @TempDir Path temporaryDirectory;

    @Test
    void rejectsAnOverlappingAttemptAndCancelsTheOwnedOperationOffRendererThread()
            throws Exception {
        CountDownLatch resolverEntered = new CountDownLatch(1);
        CountDownLatch releaseResolver = new CountDownLatch(1);
        AtomicReference<String> resolverThread = new AtomicReference<>();
        DirectConnectConfiguration configuration =
                new DirectConnectConfiguration(
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(3),
                        Duration.ofMinutes(1));
        DirectConnectService service =
                new DirectConnectService(
                        new ClientIdentityStorage(temporaryDirectory.resolve("client-data")),
                        configuration,
                        Clock.systemUTC(),
                        new SecureRandom(),
                        host -> {
                            resolverThread.set(Thread.currentThread().getName());
                            resolverEntered.countDown();
                            releaseResolver.await();
                            return new InetAddress[] {InetAddress.getLoopbackAddress()};
                        });
        DirectConnectEndpoint endpoint = DirectConnectEndpoint.parse("localhost:27420");
        CanonicalHandle handle = new CanonicalHandle("player_one");
        AtomicReference<DirectConnectAttempt> firstReference = new AtomicReference<>();
        Thread renderer =
                Thread.ofPlatform()
                        .name("jME3 Main")
                        .start(() -> firstReference.set(service.connect(endpoint, handle)));
        renderer.join(WAIT.toMillis());
        assertTrue(resolverEntered.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));
        DirectConnectAttempt first = firstReference.get();

        DirectConnectResult second =
                service.connect(endpoint, handle)
                        .result()
                        .toCompletableFuture()
                        .get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        assertEquals(
                DirectConnectFailureCode.ALREADY_CONNECTING,
                ((DirectConnectResult.Failed) second).failure().code());
        assertNotEquals(renderer.getName(), resolverThread.get());

        assertTrue(first.cancel());
        DirectConnectResult cancelled =
                first.result()
                        .toCompletableFuture()
                        .get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        assertEquals(
                DirectConnectFailureCode.CANCELLED,
                ((DirectConnectResult.Failed) cancelled).failure().code());

        releaseResolver.countDown();
        service.close();
    }
}
