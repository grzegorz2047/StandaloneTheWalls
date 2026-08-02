package pl.grzegorz2047.standalonethewalls.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerIdentity;

class AtomicRegistrySnapshotStoreTest {
    private static final Instant GENERATED_AT = Instant.parse("2026-08-02T00:00:00Z");

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

    @Test
    void commitCompletesBeforePublicationAndFailurePreservesPreviousActive()
            throws GeneralSecurityException,
                    IdentityException,
                    RegistrySnapshotException,
                    RegistrySnapshotProviderException {
        KeyPair root = RegistryTestFixtures.rootKeyPair();
        PlayerIdentity player = RegistryTestFixtures.playerIdentity();
        VerifiedRegistrySnapshot first = verified(root, player, 40L, (byte) 7);
        VerifiedRegistrySnapshot second = verified(root, player, 41L, (byte) 8);
        VerifiedRegistrySnapshot third = verified(root, player, 42L, (byte) 9);
        AtomicRegistrySnapshotStore store = new AtomicRegistrySnapshotStore();
        store.activate(first);
        AtomicInteger commits = new AtomicInteger();

        RegistryActivationResult result =
                store.activateAfterCommit(
                        second,
                        () -> {
                            assertThat(store.active()).containsSame(first);
                            commits.incrementAndGet();
                        });

        assertThat(result).isEqualTo(RegistryActivationResult.ACTIVATED);
        assertThat(commits).hasValue(1);
        assertThat(store.active()).containsSame(second);

        assertThatThrownBy(
                        () ->
                                store.activateAfterCommit(
                                        third,
                                        () -> {
                                            assertThat(store.active()).containsSame(second);
                                            commits.incrementAndGet();
                                            throw new RegistrySnapshotProviderException(
                                                    "cache commit failed");
                                        }))
                .isInstanceOf(RegistrySnapshotProviderException.class)
                .hasMessage("cache commit failed");
        assertThat(commits).hasValue(2);
        assertThat(store.active()).containsSame(second);
    }

    @Test
    void unchangedRollbackAndEquivocationNeverInvokeCommitHook()
            throws GeneralSecurityException,
                    IdentityException,
                    RegistrySnapshotException,
                    RegistrySnapshotProviderException {
        KeyPair root = RegistryTestFixtures.rootKeyPair();
        PlayerIdentity player = RegistryTestFixtures.playerIdentity();
        VerifiedRegistrySnapshot active = verified(root, player, 50L, (byte) 10);
        VerifiedRegistrySnapshot same = verified(root, player, 50L, (byte) 10);
        VerifiedRegistrySnapshot rollback = verified(root, player, 49L, (byte) 9);
        VerifiedRegistrySnapshot equivocation = verified(root, player, 50L, (byte) 11);
        AtomicRegistrySnapshotStore store = new AtomicRegistrySnapshotStore();
        store.activate(active);
        AtomicInteger commits = new AtomicInteger();
        RegistryActivationCommit commit = commits::incrementAndGet;

        assertThat(store.activateAfterCommit(same, commit))
                .isEqualTo(RegistryActivationResult.UNCHANGED);
        assertThatThrownBy(() -> store.activateAfterCommit(rollback, commit))
                .isInstanceOfSatisfying(
                        RegistrySnapshotException.class,
                        failure ->
                                assertThat(failure.code())
                                        .isEqualTo(RegistrySnapshotException.Code.ROLLBACK));
        assertThatThrownBy(() -> store.activateAfterCommit(equivocation, commit))
                .isInstanceOfSatisfying(
                        RegistrySnapshotException.class,
                        failure ->
                                assertThat(failure.code())
                                        .isEqualTo(RegistrySnapshotException.Code.EQUIVOCATION));

        assertThat(commits).hasValue(0);
        assertThat(store.active()).containsSame(active);
    }

    @Test
    void reportsAbsentFreshBoundaryAndStaleWithoutDroppingTheSnapshot()
            throws GeneralSecurityException, IdentityException, RegistrySnapshotException {
        KeyPair root = RegistryTestFixtures.rootKeyPair();
        PlayerIdentity player = RegistryTestFixtures.playerIdentity();
        VerifiedRegistrySnapshot active = verified(root, player, 30L, (byte) 6);
        AtomicRegistrySnapshotStore store = new AtomicRegistrySnapshotStore();
        RegistrySnapshotPolicy policy =
                new RegistrySnapshotPolicy(0L, Duration.ofHours(2), Duration.ZERO, 1024, 1);

        RegistrySnapshotAvailability absent =
                store.availability(Clock.fixed(GENERATED_AT, ZoneOffset.UTC), policy);
        assertThat(absent.state()).isEqualTo(RegistrySnapshotAvailability.State.ABSENT);
        assertThat(absent.snapshot()).isEmpty();

        store.activate(active);
        RegistrySnapshotAvailability boundary =
                store.availability(
                        Clock.fixed(GENERATED_AT.plus(Duration.ofHours(2)), ZoneOffset.UTC),
                        policy);
        assertThat(boundary.state()).isEqualTo(RegistrySnapshotAvailability.State.FRESH);
        assertThat(boundary.snapshot()).containsSame(active);

        RegistrySnapshotAvailability stale =
                store.availability(
                        Clock.fixed(
                                GENERATED_AT.plus(Duration.ofHours(2)).plusNanos(1),
                                ZoneOffset.UTC),
                        policy);
        assertThat(stale.state()).isEqualTo(RegistrySnapshotAvailability.State.STALE);
        assertThat(stale.snapshot()).containsSame(active);
        assertThat(store.active()).containsSame(active);
    }

    private static VerifiedRegistrySnapshot verified(
            KeyPair root, PlayerIdentity player, long sequence, byte digestByte)
            throws RegistrySnapshotException {
        RegistrySnapshotPayload payload =
                RegistryTestFixtures.payload(
                        root,
                        sequence,
                        GENERATED_AT,
                        "player_one",
                        player,
                        RegistryEntryStatus.ACTIVE);
        byte[] digest = new byte[RegistrySnapshotArtifact.DIGEST_BYTES];
        digest[0] = digestByte;
        byte[] signature = new byte[RegistrySnapshotArtifact.SIGNATURE_BYTES];
        return new VerifiedRegistrySnapshot(payload, digest, signature);
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
