package pl.grzegorz2047.standalonethewalls.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerIdentity;

class RegistrySnapshotServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-02T05:00:00Z");

    @Test
    void identicalArtifactsFromDifferentProvidersProduceOneActiveState()
            throws GeneralSecurityException,
                    IdentityException,
                    RegistrySnapshotException,
                    RegistrySnapshotProviderException {
        KeyPair root = RegistryTestFixtures.rootKeyPair();
        PlayerIdentity player = RegistryTestFixtures.playerIdentity();
        RegistrySnapshotArtifact artifact =
                RegistryTestFixtures.sign(
                        RegistryTestFixtures.payload(
                                root, 7L, NOW, "player_one", player, RegistryEntryStatus.ACTIVE),
                        root);
        AtomicRegistrySnapshotStore store = new AtomicRegistrySnapshotStore();
        RegistrySnapshotService service = service(root, store, RegistrySnapshotPolicy.DEFAULT);
        RegistrySnapshotProvider localMirror = () -> artifact;
        RegistrySnapshotProvider secondMirror =
                () ->
                        new RegistrySnapshotArtifact(
                                artifact.canonicalJson(), artifact.digest(), artifact.signature());

        assertThat(service.refresh(localMirror)).isEqualTo(RegistryActivationResult.ACTIVATED);
        byte[] activeDigest = store.active().orElseThrow().digest();
        assertThat(service.refresh(secondMirror)).isEqualTo(RegistryActivationResult.UNCHANGED);
        assertThat(store.active().orElseThrow().digest()).containsExactly(activeDigest);
    }

    @Test
    void invalidRefreshPreservesLastVerifiedSnapshot()
            throws GeneralSecurityException,
                    IdentityException,
                    RegistrySnapshotException,
                    RegistrySnapshotProviderException {
        KeyPair root = RegistryTestFixtures.rootKeyPair();
        PlayerIdentity player = RegistryTestFixtures.playerIdentity();
        RegistrySnapshotArtifact valid =
                RegistryTestFixtures.sign(
                        RegistryTestFixtures.payload(
                                root, 8L, NOW, "player_one", player, RegistryEntryStatus.ACTIVE),
                        root);
        byte[] damagedSignature = valid.signature();
        damagedSignature[0] ^= 1;
        RegistrySnapshotArtifact invalid =
                new RegistrySnapshotArtifact(
                        valid.canonicalJson(), valid.digest(), damagedSignature);
        AtomicRegistrySnapshotStore store = new AtomicRegistrySnapshotStore();
        RegistrySnapshotService service = service(root, store, RegistrySnapshotPolicy.DEFAULT);
        service.refresh(() -> valid);
        VerifiedRegistrySnapshot active = store.active().orElseThrow();

        assertThatThrownBy(() -> service.refresh(() -> invalid))
                .isInstanceOfSatisfying(
                        RegistrySnapshotException.class,
                        failure ->
                                assertThat(failure.code())
                                        .isEqualTo(
                                                RegistrySnapshotException.Code.INVALID_SIGNATURE));
        assertThat(store.active()).containsSame(active);
    }

    @Test
    void providerFailureDoesNotReachVerifierOrChangeActiveState()
            throws GeneralSecurityException,
                    IdentityException,
                    RegistrySnapshotException,
                    RegistrySnapshotProviderException {
        KeyPair root = RegistryTestFixtures.rootKeyPair();
        PlayerIdentity player = RegistryTestFixtures.playerIdentity();
        RegistrySnapshotArtifact valid =
                RegistryTestFixtures.sign(
                        RegistryTestFixtures.payload(
                                root, 9L, NOW, "player_one", player, RegistryEntryStatus.ACTIVE),
                        root);
        AtomicRegistrySnapshotStore store = new AtomicRegistrySnapshotStore();
        RegistrySnapshotService service = service(root, store, RegistrySnapshotPolicy.DEFAULT);
        service.refresh(() -> valid);
        VerifiedRegistrySnapshot active = store.active().orElseThrow();

        assertThatThrownBy(
                        () ->
                                service.refresh(
                                        () -> {
                                            throw new RegistrySnapshotProviderException(
                                                    "mirror unavailable");
                                        }))
                .isInstanceOf(RegistrySnapshotProviderException.class);
        assertThat(store.active()).containsSame(active);

        RegistrySnapshotAvailability stale =
                store.availability(
                        Clock.fixed(NOW.plus(Duration.ofDays(31)), ZoneOffset.UTC),
                        RegistrySnapshotPolicy.DEFAULT);
        assertThat(stale.state()).isEqualTo(RegistrySnapshotAvailability.State.STALE);
        assertThat(stale.snapshot()).containsSame(active);
    }

    private static RegistrySnapshotService service(
            KeyPair root, AtomicRegistrySnapshotStore store, RegistrySnapshotPolicy policy)
            throws RegistrySnapshotException {
        return new RegistrySnapshotService(
                new RegistrySnapshotVerifier(Clock.fixed(NOW, ZoneOffset.UTC)),
                RegistryTrustBundle.of(List.of(root.getPublic().getEncoded())),
                policy,
                store);
    }
}
