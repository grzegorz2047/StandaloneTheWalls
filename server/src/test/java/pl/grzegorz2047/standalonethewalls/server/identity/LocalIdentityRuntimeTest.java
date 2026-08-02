package pl.grzegorz2047.standalonethewalls.server.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.grzegorz2047.standalonethewalls.identity.policy.HandleAuthorizationMode;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAdministrationReason;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalIdentityAdministratorId;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalPlayerBanAdministrationResult;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.registry.RegistryRootId;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotArtifact;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotAvailability;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotException;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotJsonCodec;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotPayload;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotPolicy;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotProviderException;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotVerifier;
import pl.grzegorz2047.standalonethewalls.registry.RegistryTrustBundle;
import pl.grzegorz2047.standalonethewalls.registry.VerifiedRegistrySnapshot;
import pl.grzegorz2047.standalonethewalls.registry.file.RegistrySnapshotBundleFile;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationCommand;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationPermission;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationPrincipal;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationResponse;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.RegistryAdministrationResultCode;

class LocalIdentityRuntimeTest {
    private static final Instant NOW = Instant.parse("2026-08-02T12:45:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final CanonicalHandle HANDLE = new CanonicalHandle("player_one");
    private static final PlayerId PLAYER = new PlayerId("sf1_" + "a".repeat(52));
    private static final LocalIdentityAdministratorId ADMINISTRATOR =
            new LocalIdentityAdministratorId("console");
    private static final LocalHandleAdministrationReason REASON =
            new LocalHandleAdministrationReason("Confirmed local abuse");

    @TempDir Path temporaryDirectory;

    @Test
    void configurationRejectsOneFileForDatabaseAndRegistryBundle() {
        Path shared = temporaryDirectory.resolve("identity.data");

        assertThatThrownBy(
                        () ->
                                new LocalIdentityRuntimeConfiguration(
                                        shared, shared, HandleAuthorizationMode.LOCAL_TOFU))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingBundleAllowsExplicitLocalTofuAndBindingSurvivesRestart()
            throws NoSuchAlgorithmException, RegistrySnapshotException {
        KeyPair root = root();
        LocalIdentityRuntimeConfiguration configuration =
                configuration("local", HandleAuthorizationMode.LOCAL_TOFU);

        LocalIdentityRuntime first =
                LocalIdentityRuntime.open(
                        configuration, trustBundle(root), RegistrySnapshotPolicy.DEFAULT, CLOCK);

        assertThat(first.startupRegistryResult().code())
                .isEqualTo(RegistryAdministrationResultCode.PROVIDER_FAILURE);
        assertThat(first.registryAvailability().state())
                .isEqualTo(RegistrySnapshotAvailability.State.ABSENT);
        assertThat(first.admit(HANDLE, PLAYER))
                .isEqualTo(SessionIdentityAdmissionDecision.LOCAL_FIRST_USE_ACCEPTED);

        LocalIdentityRuntime restarted =
                LocalIdentityRuntime.open(
                        configuration, trustBundle(root), RegistrySnapshotPolicy.DEFAULT, CLOCK);

        assertThat(restarted.admit(HANDLE, PLAYER))
                .isEqualTo(SessionIdentityAdmissionDecision.LOCAL_RETURNING_ACCEPTED);
    }

    @Test
    void missingBundleKeepsGlobalAndHybridModesFailClosed()
            throws NoSuchAlgorithmException, RegistrySnapshotException {
        KeyPair root = root();
        RegistryTrustBundle trustBundle = trustBundle(root);

        LocalIdentityRuntime global =
                LocalIdentityRuntime.open(
                        configuration("global", HandleAuthorizationMode.GLOBAL_ONLY),
                        trustBundle,
                        RegistrySnapshotPolicy.DEFAULT,
                        CLOCK);
        LocalIdentityRuntime hybrid =
                LocalIdentityRuntime.open(
                        configuration("hybrid", HandleAuthorizationMode.HYBRID),
                        trustBundle,
                        RegistrySnapshotPolicy.DEFAULT,
                        CLOCK);

        assertThat(global.admit(HANDLE, PLAYER))
                .isEqualTo(SessionIdentityAdmissionDecision.REGISTRY_UNAVAILABLE);
        assertThat(hybrid.admit(HANDLE, PLAYER))
                .isEqualTo(SessionIdentityAdmissionDecision.REGISTRY_UNAVAILABLE);
    }

    @Test
    void validBundleIsActivatedDuringRuntimeOpen()
            throws NoSuchAlgorithmException,
                    RegistrySnapshotException,
                    InvalidKeyException,
                    SignatureException,
                    RegistrySnapshotProviderException {
        KeyPair root = root();
        LocalIdentityRuntimeConfiguration configuration =
                configuration("valid", HandleAuthorizationMode.GLOBAL_ONLY);
        storeBundle(configuration.registryBundlePath(), root, 7L, NOW);

        LocalIdentityRuntime runtime =
                LocalIdentityRuntime.open(
                        configuration, trustBundle(root), RegistrySnapshotPolicy.DEFAULT, CLOCK);

        assertThat(runtime.startupRegistryResult().code())
                .isEqualTo(RegistryAdministrationResultCode.ACTIVATED);
        assertThat(runtime.registryAvailability().state())
                .isEqualTo(RegistrySnapshotAvailability.State.FRESH);
        assertThat(runtime.registryAvailability().requireSnapshot().sequence()).isEqualTo(7L);
        assertThat(runtime.admit(HANDLE, PLAYER))
                .isEqualTo(SessionIdentityAdmissionDecision.UNKNOWN_GLOBAL_HANDLE);
    }

    @Test
    void banCommandBlocksAdmissionAndSurvivesRuntimeRestart()
            throws NoSuchAlgorithmException, RegistrySnapshotException {
        KeyPair root = root();
        LocalIdentityRuntimeConfiguration configuration =
                configuration("ban", HandleAuthorizationMode.LOCAL_TOFU);
        IdentityAdministrationPrincipal principal =
                new IdentityAdministrationPrincipal(
                        ADMINISTRATOR, Set.of(IdentityAdministrationPermission.MANAGE_PLAYER_BANS));

        LocalIdentityRuntime first =
                LocalIdentityRuntime.open(
                        configuration, trustBundle(root), RegistrySnapshotPolicy.DEFAULT, CLOCK);
        IdentityAdministrationResponse response =
                first.execute(
                        new IdentityAdministrationCommand.BanPlayer(PLAYER, REASON), principal);

        assertThat(response)
                .isEqualTo(
                        new IdentityAdministrationResponse.BanMutation(
                                LocalPlayerBanAdministrationResult.BANNED));
        assertThat(first.admit(HANDLE, PLAYER))
                .isEqualTo(SessionIdentityAdmissionDecision.PLAYER_BANNED);

        LocalIdentityRuntime restarted =
                LocalIdentityRuntime.open(
                        configuration, trustBundle(root), RegistrySnapshotPolicy.DEFAULT, CLOCK);
        assertThat(restarted.admit(HANDLE, PLAYER))
                .isEqualTo(SessionIdentityAdmissionDecision.PLAYER_BANNED);
    }

    @Test
    void authorizedReloadUpdatesTheSameRegistryStoreUsedByAdmission()
            throws NoSuchAlgorithmException,
                    RegistrySnapshotException,
                    InvalidKeyException,
                    SignatureException,
                    RegistrySnapshotProviderException {
        KeyPair root = root();
        LocalIdentityRuntimeConfiguration configuration =
                configuration("reload", HandleAuthorizationMode.GLOBAL_ONLY);
        storeBundle(configuration.registryBundlePath(), root, 1L, NOW.minusSeconds(1));
        LocalIdentityRuntime runtime =
                LocalIdentityRuntime.open(
                        configuration, trustBundle(root), RegistrySnapshotPolicy.DEFAULT, CLOCK);
        assertThat(runtime.registryAvailability().requireSnapshot().sequence()).isEqualTo(1L);

        storeBundle(configuration.registryBundlePath(), root, 2L, NOW);
        IdentityAdministrationResponse response =
                runtime.execute(
                        new IdentityAdministrationCommand.ReloadRegistry(),
                        new IdentityAdministrationPrincipal(
                                ADMINISTRATOR,
                                Set.of(IdentityAdministrationPermission.MANAGE_REGISTRY)));

        assertThat(response).isInstanceOf(IdentityAdministrationResponse.RegistryOperation.class);
        IdentityAdministrationResponse.RegistryOperation operation =
                (IdentityAdministrationResponse.RegistryOperation) response;
        assertThat(operation.result().code()).isEqualTo(RegistryAdministrationResultCode.ACTIVATED);
        assertThat(runtime.registryAvailability().requireSnapshot().sequence()).isEqualTo(2L);
        assertThat(runtime.admit(HANDLE, PLAYER))
                .isEqualTo(SessionIdentityAdmissionDecision.UNKNOWN_GLOBAL_HANDLE);
    }

    @Test
    void wellFormedBundleWithInvalidSignatureDoesNotCreateActiveSnapshot()
            throws NoSuchAlgorithmException,
                    RegistrySnapshotException,
                    InvalidKeyException,
                    SignatureException,
                    RegistrySnapshotProviderException,
                    IOException {
        KeyPair root = root();
        LocalIdentityRuntimeConfiguration configuration =
                configuration("invalid", HandleAuthorizationMode.GLOBAL_ONLY);
        storeBundle(configuration.registryBundlePath(), root, 3L, NOW);
        byte[] bundle = Files.readAllBytes(configuration.registryBundlePath());
        int signatureOffset = 4 + 1 + 3 + Integer.BYTES + RegistrySnapshotArtifact.DIGEST_BYTES;
        bundle[signatureOffset] ^= 1;
        Files.write(configuration.registryBundlePath(), bundle);

        LocalIdentityRuntime runtime =
                LocalIdentityRuntime.open(
                        configuration, trustBundle(root), RegistrySnapshotPolicy.DEFAULT, CLOCK);

        assertThat(runtime.startupRegistryResult().code())
                .isEqualTo(RegistryAdministrationResultCode.SNAPSHOT_REJECTED);
        assertThat(runtime.registryAvailability().state())
                .isEqualTo(RegistrySnapshotAvailability.State.ABSENT);
        assertThat(runtime.admit(HANDLE, PLAYER))
                .isEqualTo(SessionIdentityAdmissionDecision.REGISTRY_UNAVAILABLE);
    }

    private LocalIdentityRuntimeConfiguration configuration(
            String name, HandleAuthorizationMode mode) {
        return new LocalIdentityRuntimeConfiguration(
                temporaryDirectory.resolve(name + ".sqlite"),
                temporaryDirectory.resolve(name + ".sfrb"),
                mode);
    }

    private static RegistryTrustBundle trustBundle(KeyPair root) throws RegistrySnapshotException {
        return RegistryTrustBundle.of(List.of(root.getPublic().getEncoded()));
    }

    private static KeyPair root() throws NoSuchAlgorithmException {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static void storeBundle(Path path, KeyPair root, long sequence, Instant generatedAt)
            throws RegistrySnapshotException,
                    NoSuchAlgorithmException,
                    InvalidKeyException,
                    SignatureException,
                    RegistrySnapshotProviderException {
        RegistrySnapshotArtifact artifact = artifact(root, sequence, generatedAt);
        RegistryTrustBundle trustBundle = trustBundle(root);
        VerifiedRegistrySnapshot verified =
                new RegistrySnapshotVerifier(CLOCK)
                        .verify(artifact, trustBundle, RegistrySnapshotPolicy.DEFAULT);
        new RegistrySnapshotBundleFile(path).storeVerified(artifact, verified);
    }

    private static RegistrySnapshotArtifact artifact(
            KeyPair root, long sequence, Instant generatedAt)
            throws RegistrySnapshotException,
                    NoSuchAlgorithmException,
                    InvalidKeyException,
                    SignatureException {
        RegistrySnapshotPayload payload =
                new RegistrySnapshotPayload(
                        sequence,
                        generatedAt,
                        RegistryRootId.fromPublicKey(root.getPublic().getEncoded()),
                        List.of());
        byte[] canonicalJson = RegistrySnapshotJsonCodec.encode(payload);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalJson);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(root.getPrivate());
        signer.update(canonicalJson);
        return new RegistrySnapshotArtifact(canonicalJson, digest, signer.sign());
    }
}
