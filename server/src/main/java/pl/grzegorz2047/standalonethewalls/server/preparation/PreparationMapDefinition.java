package pl.grzegorz2047.standalonethewalls.server.preparation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;
import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationSupportBox;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationSupportMap;

/** Immutable server-owned preparation map identity, regions, supports, and spawn candidates. */
public record PreparationMapDefinition(
        String mapId,
        byte[] mapSha256,
        List<PreparationSpawnPoint> spawnPoints,
        Map<TeamId, PreparationRegionBounds> regions,
        PreparationSupportMap supportMap) {
    public static final int MAXIMUM_MAP_ID_BYTES = 64;
    public static final int SHA_256_BYTES = 32;
    private static final int LEGACY_FIXTURE_PADDING_MILLIMETRES = 100_000;
    private static final double SUPPORT_TOLERANCE_METRES = 0.001d;

    public PreparationMapDefinition(
            String mapId, byte[] mapSha256, List<PreparationSpawnPoint> spawnPoints) {
        this(
                mapId,
                mapSha256,
                spawnPoints,
                deriveFixtureRegions(spawnPoints),
                deriveFixtureSupports(spawnPoints, deriveFixtureRegions(spawnPoints)));
    }

    public PreparationMapDefinition(
            String mapId,
            byte[] mapSha256,
            List<PreparationSpawnPoint> spawnPoints,
            Map<TeamId, PreparationRegionBounds> regions) {
        this(mapId, mapSha256, spawnPoints, regions, deriveFixtureSupports(spawnPoints, regions));
    }

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

        EnumMap<TeamId, PreparationRegionBounds> copiedRegions = new EnumMap<>(TeamId.class);
        copiedRegions.putAll(Objects.requireNonNull(regions, "regions"));
        if (copiedRegions.isEmpty()) {
            throw new IllegalArgumentException("preparation map must contain team regions");
        }
        for (Map.Entry<TeamId, PreparationRegionBounds> entry : copiedRegions.entrySet()) {
            TeamId team = Objects.requireNonNull(entry.getKey(), "region team");
            PreparationRegionBounds region = Objects.requireNonNull(entry.getValue(), "region");
            if (region.team() != team) {
                throw new IllegalArgumentException(
                        "preparation region key does not match its team");
            }
        }
        supportMap = Objects.requireNonNull(supportMap, "supportMap");
        for (PreparationSpawnPoint spawnPoint : copiedSpawns) {
            PreparationRegionBounds region = copiedRegions.get(spawnPoint.team());
            if (region == null
                    || !region.contains(
                            toMillimetres(spawnPoint.x()),
                            toMillimetres(spawnPoint.y()),
                            toMillimetres(spawnPoint.z()))) {
                throw new IllegalArgumentException(
                        "preparation spawn is outside its authoritative team region");
            }
            OptionalDouble support =
                    supportMap.highestPlayerCenterAtOrBelow(
                            spawnPoint.x(),
                            spawnPoint.z(),
                            spawnPoint.y() + SUPPORT_TOLERANCE_METRES);
            if (support.isEmpty()
                    || Math.abs(support.orElseThrow() - spawnPoint.y())
                            > SUPPORT_TOLERANCE_METRES) {
                throw new IllegalArgumentException(
                        "preparation spawn is not supported by the authoritative collision map");
            }
        }
        regions = Map.copyOf(copiedRegions);
    }

    @Override
    public byte[] mapSha256() {
        return mapSha256.clone();
    }

    public PreparationRegionBounds region(TeamId team) {
        PreparationRegionBounds region = regions.get(Objects.requireNonNull(team, "team"));
        if (region == null) {
            throw new IllegalArgumentException("preparation map has no region for the team");
        }
        return region;
    }

    private static Map<TeamId, PreparationRegionBounds> deriveFixtureRegions(
            List<PreparationSpawnPoint> spawnPoints) {
        List<PreparationSpawnPoint> spawns =
                List.copyOf(Objects.requireNonNull(spawnPoints, "spawnPoints"));
        EnumMap<TeamId, Extent> extents = new EnumMap<>(TeamId.class);
        for (PreparationSpawnPoint spawn : spawns) {
            PreparationSpawnPoint candidate = Objects.requireNonNull(spawn, "spawnPoint");
            extents.computeIfAbsent(candidate.team(), ignored -> new Extent())
                    .include(
                            toMillimetres(candidate.x()),
                            toMillimetres(candidate.y()),
                            toMillimetres(candidate.z()));
        }
        EnumMap<TeamId, PreparationRegionBounds> derived = new EnumMap<>(TeamId.class);
        for (Map.Entry<TeamId, Extent> entry : extents.entrySet()) {
            Extent extent = entry.getValue();
            derived.put(
                    entry.getKey(),
                    new PreparationRegionBounds(
                            entry.getKey(),
                            subtractPadding(extent.minimumX),
                            subtractPadding(extent.minimumY),
                            subtractPadding(extent.minimumZ),
                            addPadding(extent.maximumX),
                            addPadding(extent.maximumY),
                            addPadding(extent.maximumZ)));
        }
        return Map.copyOf(derived);
    }

    private static PreparationSupportMap deriveFixtureSupports(
            List<PreparationSpawnPoint> spawnPoints,
            Map<TeamId, PreparationRegionBounds> regions) {
        List<PreparationSpawnPoint> spawns =
                List.copyOf(Objects.requireNonNull(spawnPoints, "spawnPoints"));
        Map<TeamId, PreparationRegionBounds> boundedRegions =
                Map.copyOf(Objects.requireNonNull(regions, "regions"));
        EnumMap<TeamId, Double> centerYByTeam = new EnumMap<>(TeamId.class);
        for (PreparationSpawnPoint spawn : spawns) {
            PreparationSpawnPoint candidate = Objects.requireNonNull(spawn, "spawnPoint");
            centerYByTeam.merge(
                    candidate.team(),
                    candidate.y(),
                    (first, next) -> {
                        if (Double.compare(first, next) != 0) {
                            throw new IllegalArgumentException(
                                    "legacy fixture spawns for one team must share a support height");
                        }
                        return first;
                    });
        }
        List<PreparationSupportBox> supports = new ArrayList<>(centerYByTeam.size());
        boolean groundNamed = false;
        for (Map.Entry<TeamId, Double> entry : centerYByTeam.entrySet()) {
            PreparationRegionBounds region = boundedRegions.get(entry.getKey());
            if (region == null) {
                throw new IllegalArgumentException("legacy fixture spawn team has no region");
            }
            double surfaceY =
                    entry.getValue() - PreparationSupportMap.PLAYER_CENTER_OFFSET_METRES;
            String name =
                    groundNamed ? entry.getKey().name() + "FixtureSupportCollision" : "GroundCollision";
            groundNamed = true;
            supports.add(
                    new PreparationSupportBox(
                            name,
                            new MapVector3(
                                    region.minimumXMillimetres() / 1_000.0d,
                                    surfaceY - 1.0d,
                                    region.minimumZMillimetres() / 1_000.0d),
                            new MapVector3(
                                    region.maximumXMillimetres() / 1_000.0d,
                                    surfaceY,
                                    region.maximumZMillimetres() / 1_000.0d)));
        }
        return new PreparationSupportMap(supports);
    }

    private static int toMillimetres(double metres) {
        if (!Double.isFinite(metres)) {
            throw new IllegalArgumentException("preparation coordinate must be finite");
        }
        long value = Math.round(metres * 1_000.0d);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("preparation coordinate exceeds fixed-point range");
        }
        return (int) value;
    }

    private static int subtractPadding(int value) {
        return Math.subtractExact(value, LEGACY_FIXTURE_PADDING_MILLIMETRES);
    }

    private static int addPadding(int value) {
        return Math.addExact(value, LEGACY_FIXTURE_PADDING_MILLIMETRES);
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

    private static final class Extent {
        private int minimumX = Integer.MAX_VALUE;
        private int minimumY = Integer.MAX_VALUE;
        private int minimumZ = Integer.MAX_VALUE;
        private int maximumX = Integer.MIN_VALUE;
        private int maximumY = Integer.MIN_VALUE;
        private int maximumZ = Integer.MIN_VALUE;

        private void include(int x, int y, int z) {
            minimumX = Math.min(minimumX, x);
            minimumY = Math.min(minimumY, y);
            minimumZ = Math.min(minimumZ, z);
            maximumX = Math.max(maximumX, x);
            maximumY = Math.max(maximumY, y);
            maximumZ = Math.max(maximumZ, z);
        }
    }
}
