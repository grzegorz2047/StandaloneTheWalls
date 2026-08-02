package pl.grzegorz2047.standalonethewalls.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerIdentity;

class AtomicRegistrySnapshotStoreTest {
    @Test
    void activatesHigherSequencesAndTreatsIdenticalReloadAsIdempotent()
            throws GeneralSecurityException, IdentityException, RegistrySnapshotException {
        KeyPair root = RegistryTestFixtures.rootKeyPair();
        PlayerIdentity player = RegistryTestFixtures.playerIdentity();
        VerifiedRegistrySnapshot first = verified(root, player, 10L, (byte) 1);
        VerifiedRegistrySnapshot same = verified(root, player, 10L, (byte) 1);
        VerifiedRegistrySnapshot newer = verified(root, player, 11L, (byte) 2);
        AtomicRegistrySnapshotStore store = new AtomicRegistrySnapshotStore();

        assertThat(store.activate(first)).isEqualTo(RegistryActivationResult.ACTIVATED);
        assertThat(store.activate(same)).isEqualTo(RegistryActivationResult.UNCHANGED);
        assertThat(store.activate(newer)).isEqualTo(RegistryActivationResult.ACTIVATED);
        assertThat(store.active()).containsSame(newer);
    }

    @Test
    void rejectsRollbackAndEquivocationWithoutChangingActiveSnapshot()
            throws GeneralSecurityException, IdentityException, RegistrySnapshotException {
        KeyPair root = RegistryTestFixtures.rootKeyPair();
        PlayerIdentity player = RegistryTestFixtures.playerIdentity();
        VerifiedRegistrySnapshot active = verified(root, player, 20L, (byte) 4);
        VerifiedRegistrySnapshot rollback = verified(root, player, 19L, (byte) 3);
        VerifiedRegistrySnapshot equivocation = verified(root, player, 20L, (byte) 5);
        AtomicRegistrySnapshotStore store = new AtomicRegistrySnapshotStore();
        store.activate(active);

        assertCode(store, rollback, RegistrySnapshotException.Code.ROLLBACK);
        assertThat(store.active()).containsSame(active);
        assertCode(store, equivocation, RegistrySnapshotException.Code.EQUIVOCATION);
        assertThat(store.active()).containsSame(active);
    }

    private static VerifiedRegistrySnapshot verified(
            KeyPair root, PlayerIdentity player, long sequence, byte digestByte)
            throws RegistrySnapshotException {
        RegistrySnapshotPayload payload =
                RegistryTestFixtures.payload(
                        root,
                        sequence,
                        Instant.parse("2026-08-02T00:00:00Z"),
                        "player_one",
                        player,
                        RegistryEntryStatus.ACTIVE);
        byte[] digest = new byte[RegistrySnapshotArtifact.DIGEST_BYTES];
        digest[0] = digestByte;
        return new VerifiedRegistrySnapshot(payload, digest);
    }

    private static void assertCode(
            AtomicRegistrySnapshotStore store,
            VerifiedRegistrySnapshot candidate,
            RegistrySnapshotException.Code expected) {
        assertThatThrownBy(() -> store.activate(candidate))
                .isInstanceOfSatisfying(
                        RegistrySnapshotException.class,
                        failure -> assertThat(failure.code()).isEqualTo(expected));
    }
}
