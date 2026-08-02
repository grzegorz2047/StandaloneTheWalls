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

class LocalDisplayNameAuthorizationIsolationTest {
    private static final Instant NOW = Instant.parse("2026-08-02T17:00:00Z");
    private static final CanonicalHandle GLOBAL_HANDLE = new CanonicalHandle("global_player");
    private static final CanonicalHandle LOCAL_HANDLE = new CanonicalHandle("local_player");
    private static final PlayerId LOCAL_PLAYER = new PlayerId("sf1_" + "a".repeat(52));
    private static final LocalIdentityAdministratorId ADMINISTRATOR =
            new LocalIdentityAdministratorId("console");
    private static final LocalHandleAdministrationReason REASON =
            new LocalHandleAdministrationReason("Local presentation preference");

    @Test
    void displayNameDoesNotChangeAnyAuthorizationModeOrIdentityBinding() {
        RegistryFixture fixture = registryFixture();
        InMemoryLocalHandleBindingStore bindings = new InMemoryLocalHandleBindingStore();
        assertThat(bindings.bindOrVerify(LOCAL_HANDLE, LOCAL_PLAYER))
                .isEqualTo(LocalHandleBindingResult.BOUND);
        HandleAuthorizationService authorization = new HandleAuthorizationService(bindings);
        InMemoryLocalDisplayNameStore displayNames = new InMemoryLocalDisplayNameStore();

        HandleAuthorizationDecision localBefore =
                authorization.authorize(
                        HandleAuthorizationMode.LOCAL_TOFU,
                        LOCAL_HANDLE,
                        LOCAL_PLAYER,
                        RegistrySnapshotAvailability.absent());
        HandleAuthorizationDecision globalBefore =
                authorization.authorize(
                        HandleAuthorizationMode.GLOBAL_ONLY,
                        GLOBAL_HANDLE,
                        fixture.playerId(),
                        RegistrySnapshotAvailability.fresh(fixture.snapshot()));
        HandleAuthorizationDecision hybridGlobalBefore =
                authorization.authorize(
                        HandleAuthorizationMode.HYBRID,
                        GLOBAL_HANDLE,
                        fixture.playerId(),
                        RegistrySnapshotAvailability.fresh(fixture.snapshot()));
        HandleAuthorizationDecision hybridLocalBefore =
                authorization.authorize(
                        HandleAuthorizationMode.HYBRID,
                        LOCAL_HANDLE,
                        LOCAL_PLAYER,
                        RegistrySnapshotAvailability.fresh(fixture.snapshot()));

        assertThat(
                        displayNames.setDisplayName(
                                fixture.playerId(),
                                LocalDisplayNameExpectation.absent(),
                                new LocalDisplayName("Same visible name"),
                                ADMINISTRATOR,
                                REASON,
                                NOW))
                .isEqualTo(LocalDisplayNameAdministrationResult.APPLIED);
        assertThat(
                        displayNames.setDisplayName(
                                LOCAL_PLAYER,
                                LocalDisplayNameExpectation.absent(),
                                new LocalDisplayName("Same visible name"),
                                ADMINISTRATOR,
                                REASON,
                                NOW.plusSeconds(1)))
                .isEqualTo(LocalDisplayNameAdministrationResult.APPLIED);

        assertThat(
                        authorization.authorize(
                                HandleAuthorizationMode.LOCAL_TOFU,
                                LOCAL_HANDLE,
                                LOCAL_PLAYER,
                                RegistrySnapshotAvailability.absent()))
                .isEqualTo(localBefore);
        assertThat(
                        authorization.authorize(
                                HandleAuthorizationMode.GLOBAL_ONLY,
                                GLOBAL_HANDLE,
                                fixture.playerId(),
                                RegistrySnapshotAvailability.fresh(fixture.snapshot())))
                .isEqualTo(globalBefore);
        assertThat(
                        authorization.authorize(
                                HandleAuthorizationMode.HYBRID,
                                GLOBAL_HANDLE,
                                fixture.playerId(),
                                RegistrySnapshotAvailability.fresh(fixture.snapshot())))
                .isEqualTo(hybridGlobalBefore);
        assertThat(
                        authorization.authorize(
                                HandleAuthorizationMode.HYBRID,
                                LOCAL_HANDLE,
                                LOCAL_PLAYER,
                                RegistrySnapshotAvailability.fresh(fixture.snapshot())))
                .isEqualTo(hybridLocalBefore);

        assertThat(bindings.find(LOCAL_HANDLE)).contains(LOCAL_PLAYER);
        assertThat(fixture.snapshot().find(GLOBAL_HANDLE).orElseThrow().playerId())
                .isEqualTo(fixture.playerId());
        assertThat(displayNames.displayNames())
                .extracting(assignment -> assignment.playerId())
                .containsExactlyInAnyOrder(LOCAL_PLAYER, fixture.playerId());
    }

    private static RegistryFixture registryFixture() {
        try {
            KeyPairGenerator rootGenerator = KeyPairGenerator.getInstance("Ed25519");
            KeyPair root = rootGenerator.generateKeyPair();
            PlayerIdentity player = PlayerIdentity.generate(new SecureRandom());
            RegistrySnapshotPayload payload =
                    new RegistrySnapshotPayload(
                            33L,
                            NOW,
                            RegistryRootId.fromPublicKey(root.getPublic().getEncoded()),
                            List.of(
                                    RegistrySnapshotEntry.create(
                                            GLOBAL_HANDLE,
                                            player.playerId(),
                                            player.publicKeyEncoded(),
                                            RegistryEntryStatus.ACTIVE)));
            byte[] canonicalJson = RegistrySnapshotJsonCodec.encode(payload);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalJson);
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(root.getPrivate());
            signer.update(canonicalJson);
            VerifiedRegistrySnapshot snapshot =
                    new RegistrySnapshotVerifier(Clock.fixed(NOW, ZoneOffset.UTC))
                            .verify(
                                    new RegistrySnapshotArtifact(
                                            canonicalJson, digest, signer.sign()),
                                    RegistryTrustBundle.of(List.of(root.getPublic().getEncoded())),
                                    RegistrySnapshotPolicy.DEFAULT);
            return new RegistryFixture(snapshot, player.playerId());
        } catch (GeneralSecurityException
                | IdentityException
                | RegistrySnapshotException exception) {
            throw new AssertionError("could not build a signed registry fixture", exception);
        }
    }

    private record RegistryFixture(VerifiedRegistrySnapshot snapshot, PlayerId playerId) {}
}
