package pl.grzegorz2047.standalonethewalls.client.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerIdentity;

class FilePlayerIdentityStoreTest {
    @TempDir Path temporaryDirectory;

    @Test
    void persistsOneIdentityAcrossRestartsWithRestrictivePosixPermissions()
            throws IdentityException, IOException {
        Path path = temporaryDirectory.resolve("profile/player-identity.sfki");
        PlayerIdentity first =
                PlayerIdentity.loadOrCreate(
                        new FilePlayerIdentityStore(path), new SecureRandom());
        PlayerIdentity second =
                PlayerIdentity.loadOrCreate(
                        new FilePlayerIdentityStore(path), new SecureRandom());

        assertEquals(first.playerId(), second.playerId());
        assertEquals(first.fingerprint(), second.fingerprint());
        assertOwnerOnlyWhenPosix(path);
    }

    @Test
    void concurrentFirstUseConvergesOnOnePersistedIdentity()
            throws InterruptedException, ExecutionException {
        Path path = temporaryDirectory.resolve("concurrent/player-identity.sfki");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<PlayerIdentity> first =
                    executor.submit(() -> loadAfterBarrier(path, ready, start));
            Future<PlayerIdentity> second =
                    executor.submit(() -> loadAfterBarrier(path, ready, start));
            ready.await();
            start.countDown();

            PlayerIdentity firstIdentity = first.get();
            PlayerIdentity secondIdentity = second.get();
            PlayerIdentity persisted =
                    PlayerIdentity.loadOrCreate(
                            new FilePlayerIdentityStore(path), new SecureRandom());

            assertEquals(firstIdentity.playerId(), secondIdentity.playerId());
            assertEquals(firstIdentity.playerId(), persisted.playerId());
        }
    }

    @Test
    void rejectsMismatchedStoredKeyPairWithoutReplacingIt()
            throws IdentityException, IOException, NoSuchAlgorithmException {
        Path path = temporaryDirectory.resolve("mismatch/player-identity.sfki");
        FilePlayerIdentityStore store = new FilePlayerIdentityStore(path);
        PlayerIdentity original = PlayerIdentity.loadOrCreate(store, new SecureRandom());
        byte[] content = Files.readAllBytes(path);
        int privateLength = ByteBuffer.wrap(content, 8, Integer.BYTES).getInt();
        int publicLength = ByteBuffer.wrap(content, 12, Integer.BYTES).getInt();
        KeyPair replacement = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] replacementPublic = replacement.getPublic().getEncoded();
        assertEquals(publicLength, replacementPublic.length);
        System.arraycopy(
                replacementPublic,
                0,
                content,
                16 + privateLength,
                replacementPublic.length);
        Files.write(path, content);

        IdentityException exception = assertThrows(IdentityException.class, store::load);

        assertEquals(IdentityException.Code.KEY_STORE_INVALID, exception.code());
        assertEquals(original.playerId(), original.playerId());
    }

    @Test
    void rejectsTrailingBytesAndDoesNotGenerateAReplacement()
            throws IdentityException, IOException {
        Path path = temporaryDirectory.resolve("trailing/player-identity.sfki");
        FilePlayerIdentityStore store = new FilePlayerIdentityStore(path);
        PlayerIdentity original = PlayerIdentity.loadOrCreate(store, new SecureRandom());
        long validSize = Files.size(path);
        Files.write(path, new byte[] {1}, StandardOpenOption.APPEND);

        IdentityException exception =
                assertThrows(
                        IdentityException.class,
                        () -> PlayerIdentity.loadOrCreate(store, new SecureRandom()));

        assertEquals(IdentityException.Code.KEY_STORE_INVALID, exception.code());
        assertEquals(validSize + 1L, Files.size(path));
        assertEquals(original.playerId(), original.playerId());
    }

    @Test
    void rejectsSymbolicLinkIdentityFilesWhenSupported() throws IOException {
        Path target = temporaryDirectory.resolve("actual-identity");
        Files.write(target, new byte[] {1});
        Path link = temporaryDirectory.resolve("linked-identity.sfki");
        try {
            Files.createSymbolicLink(link, target.getFileName());
        } catch (UnsupportedOperationException | SecurityException | IOException exception) {
            Assumptions.assumeTrue(false, "symbolic links are not available: " + exception.getClass());
        }

        IdentityException exception =
                assertThrows(IdentityException.class, () -> new FilePlayerIdentityStore(link).load());

        assertEquals(IdentityException.Code.KEY_STORE_READ_FAILED, exception.code());
    }

    private static PlayerIdentity loadAfterBarrier(
            Path path, CountDownLatch ready, CountDownLatch start)
            throws IdentityException, InterruptedException {
        ready.countDown();
        start.await();
        return PlayerIdentity.loadOrCreate(new FilePlayerIdentityStore(path), new SecureRandom());
    }

    private static void assertOwnerOnlyWhenPosix(Path path) throws IOException {
        PosixFileAttributeView view =
                Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (view != null) {
            assertEquals(
                    EnumSet.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(path));
        }
    }
}
