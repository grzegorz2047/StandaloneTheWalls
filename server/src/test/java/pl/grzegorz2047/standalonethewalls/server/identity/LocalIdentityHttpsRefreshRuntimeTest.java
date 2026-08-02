package pl.grzegorz2047.standalonethewalls.server.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.grzegorz2047.standalonethewalls.identity.policy.HandleAuthorizationMode;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalIdentityAdministratorId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerIdentity;
import pl.grzegorz2047.standalonethewalls.registry.RegistryEntryStatus;
import pl.grzegorz2047.standalonethewalls.registry.RegistryRootId;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotArtifact;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotEntry;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotException;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotJsonCodec;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotPayload;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotPolicy;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotProvider;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotProviderException;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotVerifier;
import pl.grzegorz2047.standalonethewalls.registry.RegistryTrustBundle;
import pl.grzegorz2047.standalonethewalls.registry.VerifiedRegistrySnapshot;
import pl.grzegorz2047.standalonethewalls.registry.file.RegistrySnapshotBundleFile;
import pl.grzegorz2047.standalonethewalls.registry.http.RegistrySnapshotHttpsConfiguration;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationCommand;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationPermission;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationPrincipal;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationResponse;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.RegistryAdministrationResultCode;
import pl.grzegorz2047.standalonethewalls.server.config.identity.RegistryRefreshConfiguration;

class LocalIdentityHttpsRefreshRuntimeTest {
    private static final Instant NOW = Instant.parse("2026-08-02T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final CanonicalHandle HANDLE = new CanonicalHandle("player_one");
    private static final IdentityAdministrationPrincipal REGISTRY_ADMIN =
            new IdentityAdministrationPrincipal(
                    new LocalIdentityAdministratorId("console"),
                    Set.of(IdentityAdministrationPermission.MANAGE_REGISTRY));

    @TempDir Path temporaryDirectory;

    @Test
    void httpsStartupUsesOnlyLocalBundleAndDoesNotLoadRemote()
            throws GeneralSecurityException, IdentityException, RegistrySnapshotException {
        Fixture fixture = new Fixture();
        AtomicInteger remoteLoads = new AtomicInteger();
        RegistrySnapshotProvider remote =
                () -> {
                    remoteLoads.incrementAndGet();
                    throw new RegistrySnapshotProviderException("remote unavailable");
                };

        LocalIdentityRuntime runtime = open(fixture, "offline-start", remote);

        assertThat(remoteLoads).hasValue(0);
        assertThat(runtime.startupRegistryResult().code())
                .isEqualTo(RegistryAdministrationResultCode.PROVIDER_FAILURE);
        assertThat(runtime.registryAvailability().state().name()).isEqualTo("ABSENT");
    }

    @Test
    void remoteVerifyDoesNotActivateOrWriteLocalBundle()
            throws GeneralSecurityException, IdentityException, RegistrySnapshotException {
        Fixture fixture = new Fixture();
        RegistrySnapshotArtifact artifact = fixture.artifact(1L);
        AtomicInteger remoteLoads = new AtomicInteger();
        LocalIdentityRuntime runtime =
                open(
                        fixture,
                        "verify-only",
                        () -> {
                            remoteLoads.incrementAndGet();
                            return artifact;
                        });

        IdentityAdministrationResponse response =
                runtime.execute(new IdentityAdministrationCommand.VerifySnapshot(), REGISTRY_ADMIN);

        assertThat(remoteLoads).hasValue(1);
        assertThat(response)
                .isInstanceOfSatisfying(
                        IdentityAdministrationResponse.RegistryOperation.class,
                        operation ->
                                assertThat(operation.result().code())
                                        .isEqualTo(RegistryAdministrationResultCode.VERIFIED));
        assertThat(runtime.registryAvailability().state().name()).isEqualTo("ABSENT");
        assertThat(Files.exists(runtime.configuration().registryBundlePath())).isFalse();
    }

    @Test
    void remoteReloadCachesThenPublishesIntoAdmissionStore()
            throws GeneralSecurityException,
                    IdentityException,
                    RegistrySnapshotException,
                    RegistrySnapshotProviderException {
        Fixture fixture = new Fixture();
        RegistrySnapshotArtifact artifact = fixture.artifact(2L);
        LocalIdentityRuntime runtime = open(fixture, "remote-reload", () -> artifact);

        IdentityAdministrationResponse response =
                runtime.execute(new IdentityAdministrationCommand.ReloadRegistry(), REGISTRY_ADMIN);

        assertThat(response)
                .isInstanceOfSatisfying(
                        IdentityAdministrationResponse.RegistryOperation.class,
                        operation ->
                                assertThat(operation.result().code())
                                        .isEqualTo(RegistryAdministrationResultCode.ACTIVATED));
        assertThat(runtime.registryAvailability().requireSnapshot().sequence()).isEqualTo(2L);
        assertThat(runtime.admit(HANDLE, fixture.player.playerId()))
                .isEqualTo(SessionIdentityAdmissionDecision.GLOBAL_ACCEPTED);
        RegistrySnapshotArtifact cached =
                new RegistrySnapshotBundleFile(runtime.configuration().registryBundlePath()).load();
        assertArtifact(cached, artifact);
    }

    @Test
    void remoteFailurePreservesOfflineStartupActiveAndBundle()
            throws GeneralSecurityException,
                    IdentityException,
                    RegistrySnapshotException,
                    RegistrySnapshotProviderException,
                    java.io.IOException {
        Fixture fixture = new Fixture();
        LocalIdentityRuntimeConfiguration configuration =
                configuration("preserve", HandleAuthorizationMode.GLOBAL_ONLY);
        RegistrySnapshotArtifact local = fixture.artifact(3L);
        storeBundle(configuration.registryBundlePath(), fixture, local);
        byte[] before = Files.readAllBytes(configuration.registryBundlePath());
        AtomicInteger remoteLoads = new AtomicInteger();
        LocalIdentityRuntime runtime =
                LocalIdentityRuntime.open(
                        configuration,
                        fixture.trustBundle(),
                        RegistrySnapshotPolicy.DEFAULT,
                        httpsRefresh(),
                        CLOCK,
                        ignored ->
                                () -> {
                                    remoteLoads.incrementAndGet();
                                    throw new RegistrySnapshotProviderException(
                                            "remote unavailable");
                                });

        assertThat(remoteLoads).hasValue(0);
        assertThat(runtime.startupRegistryResult().code())
                .isEqualTo(RegistryAdministrationResultCode.ACTIVATED);
        IdentityAdministrationResponse response =
                runtime.execute(new IdentityAdministrationCommand.ReloadRegistry(), REGISTRY_ADMIN);

        assertThat(remoteLoads).hasValue(1);
        assertThat(response)
                .isInstanceOfSatisfying(
                        IdentityAdministrationResponse.RegistryOperation.class,
                        operation ->
                                assertThat(operation.result().code())
                                        .isEqualTo(
                                                RegistryAdministrationResultCode.PROVIDER_FAILURE));
        assertThat(runtime.registryAvailability().requireSnapshot().sequence()).isEqualTo(3L);
        assertThat(Files.readAllBytes(configuration.registryBundlePath())).containsExactly(before);
    }

    private LocalIdentityRuntime open(Fixture fixture, String name, RegistrySnapshotProvider remote)
            throws RegistrySnapshotException {
        return LocalIdentityRuntime.open(
                configuration(name, HandleAuthorizationMode.GLOBAL_ONLY),
                fixture.trustBundle(),
                RegistrySnapshotPolicy.DEFAULT,
                httpsRefresh(),
                CLOCK,
                ignored -> remote);
    }

    private LocalIdentityRuntimeConfiguration configuration(
            String name, HandleAuthorizationMode mode) {
        return new LocalIdentityRuntimeConfiguration(
                temporaryDirectory.resolve(name + ".sqlite"),
                temporaryDirectory.resolve(name + ".sfrb"),
                mode);
    }

    private static RegistryRefreshConfiguration httpsRefresh() {
        return new RegistryRefreshConfiguration.Https(
                new RegistrySnapshotHttpsConfiguration(
                        URI.create("https://registry.example/releases/v7/registry.json"),
                        URI.create("https://registry.example/releases/v7/registry.sha256"),
                        URI.create("https://registry.example/releases/v7/registry.sig"),
                        4096));
    }

    private static void storeBundle(Path path, Fixture fixture, RegistrySnapshotArtifact artifact)
            throws RegistrySnapshotException, RegistrySnapshotProviderException {
        VerifiedRegistrySnapshot verified =
                new RegistrySnapshotVerifier(CLOCK)
                        .verify(artifact, fixture.trustBundle(), RegistrySnapshotPolicy.DEFAULT);
        new RegistrySnapshotBundleFile(path).storeVerified(artifact, verified);
    }

    private static void assertArtifact(
            RegistrySnapshotArtifact actual, RegistrySnapshotArtifact expected) {
        assertThat(actual.canonicalJson()).containsExactly(expected.canonicalJson());
        assertThat(actual.digest()).containsExactly(expected.digest());
        assertThat(actual.signature()).containsExactly(expected.signature());
    }

    private static final class Fixture {
        private final KeyPair root;
        private final PlayerIdentity player;

        private Fixture() throws GeneralSecurityException, IdentityException {
            root = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            player = PlayerIdentity.generate(new SecureRandom());
        }

        private RegistryTrustBundle trustBundle() throws RegistrySnapshotException {
            return RegistryTrustBundle.of(List.of(root.getPublic().getEncoded()));
        }

        private RegistrySnapshotArtifact artifact(long sequence)
                throws RegistrySnapshotException, GeneralSecurityException {
            RegistrySnapshotEntry entry =
                    RegistrySnapshotEntry.create(
                            HANDLE,
                            player.playerId(),
                            player.publicKeyEncoded(),
                            RegistryEntryStatus.ACTIVE);
            RegistrySnapshotPayload payload =
                    new RegistrySnapshotPayload(
                            sequence,
                            NOW,
                            RegistryRootId.fromPublicKey(root.getPublic().getEncoded()),
                            List.of(entry));
            byte[] canonicalJson = RegistrySnapshotJsonCodec.encode(payload);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalJson);
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(root.getPrivate());
            signer.update(canonicalJson);
            return new RegistrySnapshotArtifact(canonicalJson, digest, signer.sign());
        }
    }
}
