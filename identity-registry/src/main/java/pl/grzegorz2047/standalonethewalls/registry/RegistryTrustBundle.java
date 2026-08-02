package pl.grzegorz2047.standalonethewalls.registry;

import java.security.PublicKey;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Explicit locally configured registry-root keys indexed only by derived IDs. */
public final class RegistryTrustBundle {
    private final Map<RegistryRootId, PublicKey> roots;

    private RegistryTrustBundle(Map<RegistryRootId, PublicKey> roots) {
        this.roots = Map.copyOf(roots);
    }

    public static RegistryTrustBundle of(Collection<byte[]> rootPublicKeys)
            throws RegistrySnapshotException {
        Objects.requireNonNull(rootPublicKeys, "rootPublicKeys");
        if (rootPublicKeys.isEmpty() || rootPublicKeys.size() > 64) {
            throw new IllegalArgumentException("registry trust bundle must contain 1 to 64 roots");
        }
        Map<RegistryRootId, PublicKey> decoded = new LinkedHashMap<>();
        for (byte[] encoded : rootPublicKeys) {
            PublicKey key = RegistryCrypto.decodeEd25519(encoded);
            RegistryRootId id = RegistryRootId.fromPublicKey(key.getEncoded());
            if (decoded.putIfAbsent(id, key) != null) {
                throw new IllegalArgumentException(
                        "registry trust bundle contains a duplicate root");
            }
        }
        return new RegistryTrustBundle(decoded);
    }

    public Optional<PublicKey> find(RegistryRootId rootId) {
        return Optional.ofNullable(roots.get(Objects.requireNonNull(rootId, "rootId")));
    }

    public int size() {
        return roots.size();
    }

    @Override
    public String toString() {
        return "RegistryTrustBundle[rootCount=" + roots.size() + ']';
    }
}
