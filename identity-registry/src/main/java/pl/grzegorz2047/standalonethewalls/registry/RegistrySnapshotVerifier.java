package pl.grzegorz2047.standalonethewalls.registry;

import java.security.MessageDigest;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Offline verifier for exact canonical JSON, detached digest and trusted-root signature. */
public final class RegistrySnapshotVerifier {
    private final Clock clock;

    public RegistrySnapshotVerifier(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public VerifiedRegistrySnapshot verify(
            RegistrySnapshotArtifact artifact,
            RegistryTrustBundle trustBundle,
            RegistrySnapshotPolicy policy)
            throws RegistrySnapshotException {
        RegistrySnapshotArtifact candidate = Objects.requireNonNull(artifact, "artifact");
        RegistryTrustBundle trust = Objects.requireNonNull(trustBundle, "trustBundle");
        RegistrySnapshotPolicy acceptance = Objects.requireNonNull(policy, "policy");
        byte[] canonicalJson = candidate.canonicalJson();
        if (canonicalJson.length > acceptance.maximumJsonBytes()) {
            throw new RegistrySnapshotException(
                    RegistrySnapshotException.Code.INVALID_ARTIFACT_SIZE,
                    "registry snapshot exceeds the configured byte limit");
        }

        byte[] computedDigest = RegistryCrypto.sha256(canonicalJson);
        if (!MessageDigest.isEqual(computedDigest, candidate.digest())) {
            throw new RegistrySnapshotException(
                    RegistrySnapshotException.Code.DIGEST_MISMATCH,
                    "registry snapshot digest does not match its canonical JSON");
        }

        RegistrySnapshotPayload payload =
                RegistrySnapshotJsonCodec.decodeCanonical(canonicalJson, acceptance);
        PublicKey rootKey =
                trust.find(payload.rootKeyId())
                        .orElseThrow(
                                () ->
                                        new RegistrySnapshotException(
                                                RegistrySnapshotException.Code.UNKNOWN_ROOT_KEY,
                                                "registry snapshot root is not trusted"));
        if (!RegistryCrypto.verify(rootKey, canonicalJson, candidate.signature())) {
            throw new RegistrySnapshotException(
                    RegistrySnapshotException.Code.INVALID_SIGNATURE,
                    "registry snapshot root signature is invalid");
        }
        requirePolicy(payload, acceptance, clock.instant());
        return new VerifiedRegistrySnapshot(payload, computedDigest);
    }

    private static void requirePolicy(
            RegistrySnapshotPayload payload, RegistrySnapshotPolicy policy, Instant now)
            throws RegistrySnapshotException {
        if (payload.sequence() < policy.minimumSequence()) {
            throw new RegistrySnapshotException(
                    RegistrySnapshotException.Code.BELOW_MINIMUM_SEQUENCE,
                    "registry snapshot sequence is below the configured minimum");
        }
        if (payload.generatedAt().isAfter(now.plus(policy.maximumFutureSkew()))) {
            throw new RegistrySnapshotException(
                    RegistrySnapshotException.Code.SNAPSHOT_FROM_FUTURE,
                    "registry snapshot was generated too far in the future");
        }
        if (payload.generatedAt().isBefore(now.minus(policy.maximumAge()))) {
            throw new RegistrySnapshotException(
                    RegistrySnapshotException.Code.SNAPSHOT_TOO_OLD,
                    "registry snapshot is older than the configured maximum age");
        }
    }
}
