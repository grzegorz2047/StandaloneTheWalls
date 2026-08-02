package pl.grzegorz2047.standalonethewalls.assets;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Exact lock of all runtime asset packs required by one application revision. */
public record AssetPackLock(int schema, List<AssetPackReference> packs) {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAXIMUM_PACKS = 128;

    public AssetPackLock {
        if (schema != CURRENT_SCHEMA) {
            throw new IllegalArgumentException("unsupported asset lock schema");
        }
        packs = List.copyOf(Objects.requireNonNull(packs, "packs"));
        if (packs.size() > MAXIMUM_PACKS) {
            throw new IllegalArgumentException("asset lock contains too many packs");
        }
        Set<String> ids = new HashSet<>();
        String previous = null;
        for (AssetPackReference pack : packs) {
            AssetPackReference current = Objects.requireNonNull(pack, "pack");
            if (!ids.add(current.id())) {
                throw new IllegalArgumentException("asset lock contains a duplicate pack id");
            }
            if (previous != null && previous.compareTo(current.id()) >= 0) {
                throw new IllegalArgumentException("asset lock packs must be sorted by id");
            }
            previous = current.id();
        }
    }

    public Optional<AssetPackReference> find(String id) {
        Objects.requireNonNull(id, "id");
        return packs.stream().filter(pack -> pack.id().equals(id)).findFirst();
    }
}
