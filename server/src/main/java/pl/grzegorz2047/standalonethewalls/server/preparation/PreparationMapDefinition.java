package pl.grzegorz2047.standalonethewalls.server.preparation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable server-owned preparation map identity and its exclusive spawn candidates. */
public record PreparationMapDefinition(
        String mapId, byte[] mapSha256, List<PreparationSpawnPoint> spawnPoints) {
    public static final int MAXIMUM_MAP_ID_BYTES = 64;
    public static final int SHA_256_BYTES = 32;

    public PreparationMapDefinition {
        mapId = requireCanonicalMapId(mapId);
        Objects.requireNonNull(mapSha256, "mapSha256");
        if (mapSha256.length != SHA_256_BYTES) {
            throw new IllegalArgumentException("map digest must contain exactly 32 bytes");
        }
        mapSha256 = mapSha256.clone();

        List<PreparationSpawnPoint> copiedSpawns =
                List.copyOf(Objects.requireNonNull(spawnPoints, "spawnPoints"));
        if (copiedSpawns.isEmpty()) {
            throw new IllegalArgumentException("preparation map must contain at least one spawn");
        }
        Set<Integer> indices = new HashSet<>();
        for (PreparationSpawnPoint spawnPoint : copiedSpawns) {
            PreparationSpawnPoint candidate = Objects.requireNonNull(spawnPoint, "spawnPoint");
            if (!indices.add(candidate.index())) {
                throw new IllegalArgumentException("preparation spawn indices must be unique");
            }
        }
        spawnPoints = copiedSpawns;
    }

    @Override
    public byte[] mapSha256() {
        return mapSha256.clone();
    }

    private static String requireCanonicalMapId(String value) {
        String identifier = Objects.requireNonNull(value, "mapId");
        if (identifier.isEmpty() || identifier.length() > MAXIMUM_MAP_ID_BYTES) {
            throw new IllegalArgumentException("map id length is outside the supported range");
        }
        for (int index = 0; index < identifier.length(); index++) {
            char character = identifier.charAt(index);
            if (character < 0x21 || character > 0x7e) {
                throw new IllegalArgumentException("map id must use visible canonical ASCII");
            }
        }
        return identifier;
    }
}
