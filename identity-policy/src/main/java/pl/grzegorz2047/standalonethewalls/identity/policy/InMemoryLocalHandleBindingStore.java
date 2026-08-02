package pl.grzegorz2047.standalonethewalls.identity.policy;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Thread-safe ephemeral local binding store for tests and non-persistent servers. */
public final class InMemoryLocalHandleBindingStore implements LocalHandleBindingStore {
    private final ConcurrentMap<CanonicalHandle, PlayerId> bindings = new ConcurrentHashMap<>();

    @Override
    public LocalHandleBindingResult bindOrVerify(CanonicalHandle handle, PlayerId playerId) {
        CanonicalHandle canonicalHandle = Objects.requireNonNull(handle, "handle");
        PlayerId identity = Objects.requireNonNull(playerId, "playerId");
        PlayerId existing = bindings.putIfAbsent(canonicalHandle, identity);
        if (existing == null) {
            return LocalHandleBindingResult.BOUND;
        }
        return existing.equals(identity)
                ? LocalHandleBindingResult.MATCHED
                : LocalHandleBindingResult.CONFLICT;
    }

    public Optional<PlayerId> find(CanonicalHandle handle) {
        return Optional.ofNullable(bindings.get(Objects.requireNonNull(handle, "handle")));
    }

    public int size() {
        return bindings.size();
    }
}
