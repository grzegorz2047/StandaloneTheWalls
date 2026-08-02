package pl.grzegorz2047.standalonethewalls.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RegistrySnapshotValuesTest {
    @Test
    void artifactDefensivelyCopiesAllDetachedBytes() {
        byte[] json = new byte[] {'{', '}'};
        byte[] digest = new byte[RegistrySnapshotArtifact.DIGEST_BYTES];
        byte[] signature = new byte[RegistrySnapshotArtifact.SIGNATURE_BYTES];
        digest[0] = 1;
        signature[0] = 2;
        RegistrySnapshotArtifact artifact = new RegistrySnapshotArtifact(json, digest, signature);

        json[0] = 0;
        digest[0] = 0;
        signature[0] = 0;
        byte[] returnedJson = artifact.canonicalJson();
        byte[] returnedDigest = artifact.digest();
        byte[] returnedSignature = artifact.signature();
        returnedJson[0] = 0;
        returnedDigest[0] = 0;
        returnedSignature[0] = 0;

        assertThat(artifact.canonicalJson()).containsExactly('{', '}');
        assertThat(artifact.digest()[0]).isEqualTo((byte) 1);
        assertThat(artifact.signature()[0]).isEqualTo((byte) 2);
        assertThat(artifact.toString()).doesNotContain("[1").doesNotContain("[2");
    }

    @Test
    void artifactRejectsEmptyJsonAndWrongDetachedLengths() {
        assertThatThrownBy(
                        () ->
                                new RegistrySnapshotArtifact(
                                        new byte[0],
                                        new byte[RegistrySnapshotArtifact.DIGEST_BYTES],
                                        new byte[RegistrySnapshotArtifact.SIGNATURE_BYTES]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new RegistrySnapshotArtifact(
                                        new byte[] {'{', '}'},
                                        new byte[31],
                                        new byte[RegistrySnapshotArtifact.SIGNATURE_BYTES]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new RegistrySnapshotArtifact(
                                        new byte[] {'{', '}'},
                                        new byte[RegistrySnapshotArtifact.DIGEST_BYTES],
                                        new byte[63]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void policyAcceptsDocumentedLimitsAndRejectsUnsafeConfiguration() {
        RegistrySnapshotPolicy boundary =
                new RegistrySnapshotPolicy(
                        0L,
                        Duration.ofDays(365),
                        Duration.ofHours(24),
                        RegistrySnapshotPolicy.ABSOLUTE_MAXIMUM_JSON_BYTES,
                        RegistrySnapshotPolicy.ABSOLUTE_MAXIMUM_ENTRIES);
        assertThat(boundary.maximumEntries())
                .isEqualTo(RegistrySnapshotPolicy.ABSOLUTE_MAXIMUM_ENTRIES);

        assertThatThrownBy(
                        () ->
                                new RegistrySnapshotPolicy(
                                        -1L,
                                        Duration.ZERO,
                                        Duration.ZERO,
                                        1,
                                        0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new RegistrySnapshotPolicy(
                                        0L,
                                        Duration.ofDays(366),
                                        Duration.ZERO,
                                        1,
                                        0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new RegistrySnapshotPolicy(
                                        0L,
                                        Duration.ZERO,
                                        Duration.ofHours(25),
                                        1,
                                        0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new RegistrySnapshotPolicy(
                                        0L,
                                        Duration.ZERO,
                                        Duration.ZERO,
                                        RegistrySnapshotPolicy.ABSOLUTE_MAXIMUM_JSON_BYTES + 1,
                                        0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new RegistrySnapshotPolicy(
                                        0L,
                                        Duration.ZERO,
                                        Duration.ZERO,
                                        1,
                                        RegistrySnapshotPolicy.ABSOLUTE_MAXIMUM_ENTRIES + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
