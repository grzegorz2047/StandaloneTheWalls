package pl.grzegorz2047.standalonethewalls.identity.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerIdentity;
import pl.grzegorz2047.standalonethewalls.registry.RegistryEntryStatus;
import pl.grzegorz2047.standalonethewalls.registry.RegistryRootId;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotArtifact;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotAvailability;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotEntry;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotException;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotJsonCodec;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotPayload;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotPolicy;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotVerifier;
import pl.grzegorz2047.standalonethewalls.registry.RegistryTrustBundle;
import pl.grzegorz2047.standalonethewalls.registry.VerifiedRegistrySnapshot;

class HandleAuthorizationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-02T07:00:00Z");
    private static final CanonicalHandle ACTIVE_HANDLE = new CanonicalHandle("global_active");
    private static final CanonicalHandle REVOKED_HANDLE = new CanonicalHandle("global_revoked");
    private static final CanonicalHandle LOCAL_HANDLE = new CanonicalHandle("local_player");
    private static final PlayerId LOCAL_PLAYER = new PlayerId("sf1_" + "a".repeat(52));
    private static final PlayerId OTHER_PLAYER = new PlayerId("sf1_" + "b".repeat(52));

    @Test
    void localTofuWorksWithoutARegistrySnapshot() {
        InMemoryLocalHandleBindingStore store = new InMemoryLocalHandleBindingStore();
        HandleAuthorizationService service = new HandleAuthorizationService(store);

        assertThat(
                        service.authorize(
                                HandleAuthorizationMode.LOCAL_TOFU,
                                LOCAL_HANDLE,
                                LOCAL_PLAYER,
                                Optional.empty()))
                .isEqualTo(HandleAuthorizationDecision.LOCAL_FIRST_USE_ACCEPTED);
        assertThat(
                        service.authorize(
                                HandleAuthorizationMode.LOCAL_TOFU,
                                LOCAL_HANDLE,
                                LOCAL_PLAYER,
                                Optional.empty()))
                .isEqualTo(HandleAuthorizationDecision.LOCAL_RETURNING_ACCEPTED);
        assertThat(
                        service.authorize(
                                HandleAuthorizationMode.LOCAL_TOFU,
                                LOCAL_HANDLE,
                                OTHER_PLAYER,
                                Optional.empty()))
                .isEqualTo(HandleAuthorizationDecision.LOCAL_BINDING_CONFLICT);
        assertThat(HandleAuthorizationDecision.LOCAL_RETURNING_ACCEPTED.verificationLevel())
                .contains(HandleVerificationLevel.LOCAL_UNVERIFIED);
    }

    @Test
    void globalOnlyReturnsDistinctFailClosedDecisions() {
        RegistryFixture fixture = registryFixture();
        HandleAuthorizationService service =
                new HandleAuthorizationService(
                        (handle, playerId) -> {
                            throw new AssertionError("GLOBAL_ONLY must not access local bindings");
                        });

        assertThat(
                        service.authorize(
                                HandleAuthorizationMode.GLOBAL_ONLY,
                                ACTIVE_HANDLE,
                                fixture.activePlayerId(),
                                Optional.empty()))
                .isEqualTo(HandleAuthorizationDecision.REGISTRY_UNAVAILABLE);
        assertThat(
                        service.authorize(
                                HandleAuthorizationMode.GLOBAL_ONLY,
                                LOCAL_HANDLE,
                                LOCAL_PLAYER,
                                Optional.of(fixture.snapshot())))
                .isEqualTo(HandleAuthorizationDecision.UNKNOWN_GLOBAL_HANDLE);
        assertThat(
                        service.authorize(
                                HandleAuthorizationMode.GLOBAL_ONLY,
                                REVOKED_HANDLE,
                                fixture.revokedPlayerId(),
                                Optional.of(fixture.snapshot())))
                .isEqualTo(HandleAuthorizationDecision.REVOKED_GLOBAL_HANDLE);
        assertThat(
                        service.authorize(
                                HandleAuthorizationMode.GLOBAL_ONLY,
                                ACTIVE_HANDLE,
                                OTHER_PLAYER,
                                Optional.of(fixture.snapshot())))
                .isEqualTo(HandleAuthorizationDecision.GLOBAL_PLAYER_MISMATCH);
        assertThat(
                        service.authorize(
                                HandleAuthorizationMode.GLOBAL_ONLY,
                                ACTIVE_HANDLE,
                                fixture.activePlayerId(),
                                Optional.of(fixture.snapshot())))
                .isEqualTo(HandleAuthorizationDecision.GLOBAL_ACCEPTED);
        assertThat(HandleAuthorizationDecision.GLOBAL_ACCEPTED.verificationLevel())
                .contains(HandleVerificationLevel.GLOBAL_VERIFIED);
        assertThat(HandleAuthorizationDecision.REGISTRY_UNAVAILABLE.verificationLevel()).isEmpty();
    }

    @Test
    void hybridRequiresSnapshotAndReservesEveryKnownGlobalHandle() {
        RegistryFixture fixture = registryFixture();
        InMemoryLocalHandleBindingStore store = new InMemoryLocalHandleBindingStore();
        HandleAuthorizationService service = new HandleAuthorizationService(store);

        assertThat(
                        service.authorize(
                                HandleAuthorizationMode.HYBRID,
                                LOCAL_HANDLE,
                                LOCAL_PLAYER,
                                Optional.empty()))
                .isEqualTo(HandleAuthorizationDecision.REGISTRY_UNAVAILABLE);
        assertThat(store.size()).isZero();

        assertThat(store.bindOrVerify(ACTIVE_HANDLE, OTHER_PLAYER))
                .isEqualTo(LocalHandleBindingResult.BOUND);
        assertThat(
                        service.authorize(
                                HandleAuthorizationMode.HYBRID,
                                ACTIVE_HANDLE,
                                fixture.activePlayerId(),
                                Optional.of(fixture.snapshot())))
                .isEqualTo(HandleAuthorizationDecision.GLOBAL_ACCEPTED);
        assertThat(
                        service.authorize(
                                HandleAuthorizationMode.HYBRID,
                                ACTIVE_HANDLE,
                                OTHER_PLAYER,
                                Optional.of(fixture.snapshot())))
                .isEqualTo(HandleAuthorizationDecision.GLOBAL_PLAYER_MISMATCH);
        assertThat(
                        service.authorize(
                                HandleAuthorizationMode.HYBRID,
                                REVOKED_HANDLE,
                                fixture.revokedPlayerId(),
                                Optional.of(fixture.snapshot())))
                .isEqualTo(HandleAuthorizationDecision.REVOKED_GLOBAL_HANDLE);

        assertThat(
                        service.authorize(
                                HandleAuthorizationMode.HYBRID,
                                LOCAL_HANDLE,
                                LOCAL_PLAYER,
                                Optional.of(fixture.snapshot())))
                .isEqualTo(HandleAuthorizationDecision.LOCAL_FIRST_USE_ACCEPTED);
        assertThat(
                        service.authorize(
                                HandleAuthorizationMode.HYBRID,
                                LOCAL_HANDLE,
                                LOCAL_PLAYER,
                                Optional.of(fixture.snapshot())))
                .isEqualTo(HandleAuthorizationDecision.LOCAL_RETURNING_ACCEPTED);
        assertThat(
                        service.authorize(
                                HandleAuthorizationMode.HYBRID,
                                LOCAL_HANDLE,
                                OTHER_PLAYER,
                                Optional.of(fixture.snapshot())))
                .isEqualTo(HandleAuthorizationDecision.LOCAL_BINDING_CONFLICT);
    }

    @Test
    void staleSnapshotOnlyReservesKnownHandlesForHybrid() {
        RegistryFixture fixture = registryFixture();
        RegistrySnapshotAvailability stale = RegistrySnapshotAvailability.stale(fixture.snapshot());
        InMemoryLocalHandleBindingStore store = new InMemoryLocalHandleBindingStore();
        HandleAuthorizationService service = new HandleAuthorizationService(store);
        assertThat(store.bindOrVerify(ACTIVE_HANDLE, OTHER_PLAYER))
                .isEqualTo(LocalHandleBindingResult.BOUND);

        assertThat(
                        service.authorize(
                                HandleAuthorizationMode.GLOBAL_ONLY,
                                ACTIVE_HANDLE,
                                fixture.activePlayerId(),
                                stale))
                .isEqualTo(HandleAuthorizationDecision.REGISTRY_STALE);
        assertThat(
                        service.authorize(
                                HandleAuthorizationMode.HYBRID,
                                ACTIVE_HANDLE,
                                fixture.activePlayerId(),
                                stale))
                .isEqualTo(HandleAuthorizationDecision.REGISTRY_STALE);
        assertThat(
                        service.authorize(
                                HandleAuthorizationMode.HYBRID,
                                REVOKED_HANDLE,
                                fixture.revokedPlayerId(),
                                stale))
                .isEqualTo(HandleAuthorizationDecision.REGISTRY_STALE);
        assertThat(store.find(ACTIVE_HANDLE)).contains(OTHER_PLAYER);

        assertThat(
                        service.authorize(
                                HandleAuthorizationMode.HYBRID,
                                LOCAL_HANDLE,
                                LOCAL_PLAYER,
                                stale))
                .isEqualTo(HandleAuthorizationDecision.LOCAL_FIRST_USE_ACCEPTED);
        assertThat(store.find(LOCAL_HANDLE)).contains(LOCAL_PLAYER);
        assertThat(HandleAuthorizationDecision.REGISTRY_STALE.verificationLevel()).isEmpty();
    }

    private static RegistryFixture registryFixture() {
        try {
            KeyPairGenerator rootGenerator = KeyPairGenerator.getInstance("Ed25519");
            KeyPair root = rootGenerator.generateKeyPair();
            PlayerIdentity activePlayer = PlayerIdentity.generate(new SecureRandom());
            PlayerIdentity revokedPlayer = PlayerIdentity.generate(new SecureRandom());
            RegistrySnapshotPayload payload =
                    new RegistrySnapshotPayload(
                            21L,
                            NOW,
                            RegistryRootId.fromPublicKey(root.getPublic().getEncoded()),
                            List.of(
                                    RegistrySnapshotEntry.create(
                                            ACTIVE_HANDLE,
                                            activePlayer.playerId(),
                                            activePlayer.publicKeyEncoded(),
                                            RegistryEntryStatus.ACTIVE),
                                    RegistrySnapshotEntry.create(
                                            REVOKED_HANDLE,
                                            revokedPlayer.playerId(),
                                            revokedPlayer.publicKeyEncoded(),
                                            RegistryEntryStatus.REVOKED)));
            byte[] canonicalJson = RegistrySnapshotJsonCodec.encode(payload);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalJson);
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(root.getPrivate());
            signer.update(canonicalJson);
            RegistrySnapshotArtifact artifact =
                    new RegistrySnapshotArtifact(canonicalJson, digest, signer.sign());
            VerifiedRegistrySnapshot snapshot =
                    new RegistrySnapshotVerifier(Clock.fixed(NOW, ZoneOffset.UTC))
                            .verify(
                                    artifact,
                                    RegistryTrustBundle.of(List.of(root.getPublic().getEncoded())),
                                    RegistrySnapshotPolicy.DEFAULT);
            return new RegistryFixture(snapshot, activePlayer.playerId(), revokedPlayer.playerId());
        } catch (GeneralSecurityException
                | IdentityException
                | RegistrySnapshotException exception) {
            throw new AssertionError("could not build a signed registry fixture", exception);
        }
    }

    private record RegistryFixture(
            VerifiedRegistrySnapshot snapshot, PlayerId activePlayerId, PlayerId revokedPlayerId) {}
}
