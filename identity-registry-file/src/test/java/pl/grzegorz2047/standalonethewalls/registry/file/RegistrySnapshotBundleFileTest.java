package pl.grzegorz2047.standalonethewalls.registry.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerIdentity;
import pl.grzegorz2047.standalonethewalls.registry.RegistryEntryStatus;
import pl.grzegorz2047.standalonethewalls.registry.RegistryRootId;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotArtifact;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotEntry;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotJsonCodec;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotPayload;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotPolicy;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotProviderException;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotVerifier;
import pl.grzegorz2047.standalonethewalls.registry.RegistryTrustBundle;
import pl.grzegorz2047.standalonethewalls.registry.VerifiedRegistrySnapshot;

class RegistrySnapshotBundleFileTest {
    private static final Instant NOW = Instant.parse("2026-08-02T06:00:00Z");
    private static final int HEADER_BYTES = 108;

    @TempDir Path temporaryDirectory;

    @Test
    void verifiedArtifactRoundTripsWithoutChangingDetachedBytes() throws Exception {
        Fixture fixture = fixture(11L);
        RegistrySnapshotBundleFile bundle =
                new RegistrySnapshotBundleFile(temporaryDirectory.resolve("registry-v1.sfrb"));

        bundle.storeVerified(fixture.artifact(), fixture.verified());
        RegistrySnapshotArtifact loaded = bundle.load();

        assertThat(loaded.canonicalJson()).containsExactly(fixture.artifact().canonicalJson());
        assertThat(loaded.digest()).containsExactly(fixture.artifact().digest());
        assertThat(loaded.signature()).containsExactly(fixture.artifact().signature());
        assertThat(Files.size(bundle.path()))
                .isEqualTo(HEADER_BYTES + fixture.artifact().canonicalJson().length);
    }

    @Test
    void newerVerifiedArtifactAtomicallyReplacesThePreviousBundle() throws Exception {
        Fixture first = fixture(12L);
        Fixture second = fixture(13L);
        RegistrySnapshotBundleFile bundle =
                new RegistrySnapshotBundleFile(temporaryDirectory.resolve("registry-v1.sfrb"));
        bundle.storeVerified(first.artifact(), first.verified());

        bundle.storeVerified(second.artifact(), second.verified());

        assertThat(bundle.load().digest()).containsExactly(second.artifact().digest());
        try (var files = Files.list(temporaryDirectory)) {
            assertThat(files.map(Path::getFileName).map(Path::toString).toList())
                    .containsExactly("registry-v1.sfrb");
        }
    }

    @Test
    void mismatchedArtifactIsRejectedBeforeReplacingThePreviousBundle() throws Exception {
        Fixture fixture = fixture(14L);
        RegistrySnapshotBundleFile bundle =
                new RegistrySnapshotBundleFile(temporaryDirectory.resolve("registry-v1.sfrb"));
        bundle.storeVerified(fixture.artifact(), fixture.verified());
        byte[] previous = Files.readAllBytes(bundle.path());
        byte[] changedSignature = fixture.artifact().signature();
        changedSignature[0] ^= 1;
        RegistrySnapshotArtifact mismatched =
                new RegistrySnapshotArtifact(
                        fixture.artifact().canonicalJson(),
                        fixture.artifact().digest(),
                        changedSignature);

        assertThatThrownBy(() -> bundle.storeVerified(mismatched, fixture.verified()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("registry snapshot artifact does not match the verified snapshot");
        assertThat(Files.readAllBytes(bundle.path())).containsExactly(previous);
    }

    @Test
    void readerRejectsCorruptHeadersTruncationAndTrailingData() throws Exception {
        Fixture fixture = fixture(15L);
        Path source = temporaryDirectory.resolve("registry-v1.sfrb");
        RegistrySnapshotBundleFile bundle = new RegistrySnapshotBundleFile(source);
        bundle.storeVerified(fixture.artifact(), fixture.verified());
        byte[] valid = Files.readAllBytes(source);

        assertInvalid(bundle, mutate(valid, 0));
        assertInvalid(bundle, mutate(valid, 4));
        assertInvalid(bundle, mutate(valid, 5));

        byte[] impossibleLength = valid.clone();
        Arrays.fill(impossibleLength, 8, 12, (byte) 0);
        assertInvalid(bundle, impossibleLength);
        assertInvalid(bundle, Arrays.copyOf(valid, valid.length - 1));
        assertInvalid(bundle, Arrays.copyOf(valid, valid.length + 1));
    }

    @Test
    void readerRejectsDirectoriesAndSymbolicLinksWhenSupported() throws Exception {
        RegistrySnapshotBundleFile directoryProvider =
                new RegistrySnapshotBundleFile(temporaryDirectory);
        assertThatThrownBy(directoryProvider::load)
                .isInstanceOf(RegistrySnapshotProviderException.class)
                .hasMessage("registry snapshot bundle is unavailable or invalid");

        Fixture fixture = fixture(16L);
        Path target = temporaryDirectory.resolve("target.sfrb");
        RegistrySnapshotBundleFile targetBundle = new RegistrySnapshotBundleFile(target);
        targetBundle.storeVerified(fixture.artifact(), fixture.verified());
        Path link = temporaryDirectory.resolve("link.sfrb");
        try {
            Files.createSymbolicLink(link, target.getFileName());
        } catch (UnsupportedOperationException | IOException exception) {
            return;
        }

        assertThatThrownBy(new RegistrySnapshotBundleFile(link)::load)
                .isInstanceOf(RegistrySnapshotProviderException.class)
                .hasMessage("registry snapshot bundle is unavailable or invalid");
    }

    @Test
    void configuredFileLimitIsEnforcedBeforeAllocationOrWrite() throws Exception {
        Fixture fixture = fixture(17L);
        RegistrySnapshotBundleFile constrained =
                new RegistrySnapshotBundleFile(temporaryDirectory.resolve("small.sfrb"), 1);

        assertThatThrownBy(() -> constrained.storeVerified(fixture.artifact(), fixture.verified()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("registry snapshot JSON exceeds the configured file limit");
        assertThatThrownBy(() -> new RegistrySnapshotBundleFile(Path.of("bundle"), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new RegistrySnapshotBundleFile(
                                        Path.of("bundle"),
                                        RegistrySnapshotPolicy.ABSOLUTE_MAXIMUM_JSON_BYTES + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertInvalid(RegistrySnapshotBundleFile bundle, byte[] bytes) throws IOException {
        Files.write(bundle.path(), bytes);
        assertThatThrownBy(bundle::load)
                .isInstanceOf(RegistrySnapshotProviderException.class)
                .hasMessage("registry snapshot bundle is unavailable or invalid");
    }

    private static byte[] mutate(byte[] source, int index) {
        byte[] changed = source.clone();
        changed[index] ^= 1;
        return changed;
    }

    private static Fixture fixture(long sequence) throws Exception {
        KeyPairGenerator rootGenerator = KeyPairGenerator.getInstance("Ed25519");
        KeyPair root = rootGenerator.generateKeyPair();
        PlayerIdentity player = PlayerIdentity.generate(new SecureRandom());
        RegistrySnapshotEntry entry =
                RegistrySnapshotEntry.create(
                        new CanonicalHandle("player_one"),
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
        RegistrySnapshotArtifact artifact =
                new RegistrySnapshotArtifact(canonicalJson, digest, signer.sign());
        VerifiedRegistrySnapshot verified =
                new RegistrySnapshotVerifier(Clock.fixed(NOW, ZoneOffset.UTC))
                        .verify(
                                artifact,
                                RegistryTrustBundle.of(
                                        List.of(root.getPublic().getEncoded())),
                                RegistrySnapshotPolicy.DEFAULT);
        return new Fixture(artifact, verified);
    }

    private record Fixture(
            RegistrySnapshotArtifact artifact, VerifiedRegistrySnapshot verified) {}
}
