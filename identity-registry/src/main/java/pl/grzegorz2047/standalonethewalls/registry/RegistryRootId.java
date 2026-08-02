package pl.grzegorz2047.standalonethewalls.registry;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable identifier derived from one canonical Ed25519 registry-root public key. */
public record RegistryRootId(String value) {
    private static final Pattern FORMAT = Pattern.compile("sfr1_[a-z2-7]{52}");

    public RegistryRootId {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid Sunderfront registry root ID");
        }
    }

    public static RegistryRootId fromPublicKey(byte[] subjectPublicKeyInfo)
            throws RegistrySnapshotException {
        byte[] canonical = RegistryCrypto.decodeEd25519(subjectPublicKeyInfo).getEncoded();
        return new RegistryRootId("sfr1_" + RegistryCrypto.base32(RegistryCrypto.sha256(canonical)));
    }
}
