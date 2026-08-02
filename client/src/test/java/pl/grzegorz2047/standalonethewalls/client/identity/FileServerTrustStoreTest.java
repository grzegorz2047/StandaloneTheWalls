package pl.grzegorz2047.standalonethewalls.client.identity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerReference;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustDecision;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustRecord;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustService;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustStoreException;

class FileServerTrustStoreTest {
    @TempDir Path temporaryDirectory;

    @Test
    void persistsFirstUseDetectsChangedIdentityAndSupportsExplicitReplacement()
            throws IdentityException,
                    NoSuchAlgorithmException,
                    ServerTrustStoreException,
                    IOException {
        Path path = temporaryDirectory.resolve("profile/server-trust.sftr");
        ServerReference reference = new ServerReference("127.0.0.1:27420");
        ServerId firstId = generateServerId();
        ServerId replacementId = generateServerId();
        ServerTrustService firstService = new ServerTrustService(new FileServerTrustStore(path));

        assertEquals(
                ServerTrustDecision.Status.FIRST_USE_REQUIRES_CONFIRMATION,
                firstService.inspect(reference, firstId, Optional.empty()).status());
        ServerTrustRecord first =
                firstService.confirmFirstUse(
                        reference, firstId, Optional.empty(), "confirmed on local network");

        ServerTrustService restarted = new ServerTrustService(new FileServerTrustStore(path));
        assertEquals(
                ServerTrustDecision.Status.TRUSTED,
                restarted.inspect(reference, firstId, Optional.empty()).status());
        assertEquals(
                ServerTrustDecision.Status.CHANGED_IDENTITY,
                restarted.inspect(reference, replacementId, Optional.empty()).status());

        ServerTrustRecord replacement =
                restarted.replace(first, replacementId, "operator approved key rotation");
        assertEquals(ServerTrustRecord.Source.EXPLICIT_REPLACEMENT, replacement.source());
        assertEquals(
                ServerTrustDecision.Status.TRUSTED,
                new ServerTrustService(new FileServerTrustStore(path))
                        .inspect(reference, replacementId, Optional.empty())
                        .status());
        assertOwnerOnlyWhenPosix(path);
    }

    @Test
    void concurrentFirstUseCompareAndSetAllowsExactlyOneWinner()
            throws IdentityException,
                    NoSuchAlgorithmException,
                    InterruptedException,
                    ExecutionException,
                    ServerTrustStoreException {
        Path path = temporaryDirectory.resolve("concurrent/server-trust.sftr");
        ServerReference reference = new ServerReference("localhost:27420");
        ServerTrustRecord first =
                new ServerTrustRecord(
                        reference,
                        generateServerId(),
                        ServerTrustRecord.Source.TOFU,
                        "first contender");
        ServerTrustRecord second =
                new ServerTrustRecord(
                        reference,
                        generateServerId(),
                        ServerTrustRecord.Source.TOFU,
                        "second contender");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        boolean firstWon;
        boolean secondWon;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> firstResult =
                    executor.submit(() -> saveAfterBarrier(path, first, ready, start));
            Future<Boolean> secondResult =
                    executor.submit(() -> saveAfterBarrier(path, second, ready, start));
            ready.await();
            start.countDown();
            firstWon = firstResult.get();
            secondWon = secondResult.get();
        }

        assertTrue(firstWon ^ secondWon);
        ServerTrustRecord persisted = new FileServerTrustStore(path).find(reference).orElseThrow();
        assertTrue(persisted.equals(first) || persisted.equals(second));
    }

    @Test
    void failedCompareAndSetPreservesTheLastKnownGoodRecord()
            throws IdentityException, NoSuchAlgorithmException, ServerTrustStoreException {
        Path path = temporaryDirectory.resolve("cas/server-trust.sftr");
        FileServerTrustStore store = new FileServerTrustStore(path);
        ServerReference reference = new ServerReference("server.example:27420");
        ServerTrustRecord current =
                new ServerTrustRecord(
                        reference,
                        generateServerId(),
                        ServerTrustRecord.Source.TOFU,
                        "initial confirmation");
        assertTrue(store.saveIfAbsent(current));
        ServerTrustRecord staleExpected =
                new ServerTrustRecord(
                        reference,
                        current.serverId(),
                        ServerTrustRecord.Source.TOFU,
                        "different expected record");
        ServerTrustRecord replacement =
                new ServerTrustRecord(
                        reference,
                        generateServerId(),
                        ServerTrustRecord.Source.EXPLICIT_REPLACEMENT,
                        "replacement attempt");

        assertFalse(store.replace(staleExpected, replacement));
        assertEquals(current, store.find(reference).orElseThrow());
    }

    @Test
    void rejectsTrailingBytesWithoutRewritingTheStore()
            throws IdentityException,
                    NoSuchAlgorithmException,
                    ServerTrustStoreException,
                    IOException {
        Path path = temporaryDirectory.resolve("trailing/server-trust.sftr");
        FileServerTrustStore store = new FileServerTrustStore(path);
        ServerReference reference = new ServerReference("127.0.0.1:27420");
        assertTrue(
                store.saveIfAbsent(
                        new ServerTrustRecord(
                                reference,
                                generateServerId(),
                                ServerTrustRecord.Source.TOFU,
                                "confirmed locally")));
        Files.write(path, new byte[] {1}, StandardOpenOption.APPEND);
        byte[] corrupted = Files.readAllBytes(path);

        assertThrows(ServerTrustStoreException.class, () -> store.find(reference));

        assertArrayEquals(corrupted, Files.readAllBytes(path));
    }

    @Test
    void rejectsSymbolicLinkTrustFilesWhenSupported() throws IOException {
        Path target = temporaryDirectory.resolve("actual-trust");
        Files.write(target, new byte[] {1});
        Path link = temporaryDirectory.resolve("linked-trust.sftr");
        try {
            Files.createSymbolicLink(link, target.getFileName());
        } catch (UnsupportedOperationException | SecurityException | IOException exception) {
            Assumptions.assumeTrue(
                    false, "symbolic links are not available: " + exception.getClass());
        }

        assertThrows(
                ServerTrustStoreException.class,
                () -> new FileServerTrustStore(link).find(new ServerReference("localhost:27420")));
    }

    private static boolean saveAfterBarrier(
            Path path, ServerTrustRecord record, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException, ServerTrustStoreException {
        ready.countDown();
        start.await();
        return new FileServerTrustStore(path).saveIfAbsent(record);
    }

    private static ServerId generateServerId() throws NoSuchAlgorithmException, IdentityException {
        return ServerId.fromPublicKey(
                KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded());
    }

    private static void assertOwnerOnlyWhenPosix(Path path) throws IOException {
        PosixFileAttributeView view =
                Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (view != null) {
            assertEquals(
                    EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(path));
        }
    }
}
