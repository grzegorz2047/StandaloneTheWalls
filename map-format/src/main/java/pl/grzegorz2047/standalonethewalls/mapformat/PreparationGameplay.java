package pl.grzegorz2047.standalonethewalls.mapformat;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Strict typed gameplay metadata required to enter the preparation scene. */
public record PreparationGameplay(
        int schemaVersion, List<PreparationRegion> regions, List<PreparationMapSpawn> spawns) {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAXIMUM_SPAWNS = 40;

    public PreparationGameplay {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported preparation gameplay schema");
        }
        regions = List.copyOf(Objects.requireNonNull(regions, "regions"));
        spawns = List.copyOf(Objects.requireNonNull(spawns, "spawns"));
        if (regions.size() != 2 && regions.size() != 4) {
            throw new IllegalArgumentException(
                    "preparation gameplay requires exactly 2 or 4 regions");
        }
        if (spawns.size() < regions.size() || spawns.size() > MAXIMUM_SPAWNS) {
            throw new IllegalArgumentException(
                    "preparation spawn count is outside the supported range");
        }

        Map<PreparationTeam, PreparationRegion> regionsByTeam =
                new EnumMap<>(PreparationTeam.class);
        for (PreparationRegion region : regions) {
            PreparationRegion candidate = Objects.requireNonNull(region, "region");
            if (regionsByTeam.putIfAbsent(candidate.team(), candidate) != null) {
                throw new IllegalArgumentException("preparation region teams must be unique");
            }
        }
        for (int left = 0; left < regions.size(); left++) {
            for (int right = left + 1; right < regions.size(); right++) {
                if (regions.get(left).overlapsVolume(regions.get(right))) {
                    throw new IllegalArgumentException(
                            "preparation regions cannot overlap in volume");
                }
            }
        }

        Set<Integer> spawnIndices = new HashSet<>();
        Set<PreparationTeam> teamsWithSpawns = new HashSet<>();
        for (PreparationMapSpawn spawn : spawns) {
            PreparationMapSpawn candidate = Objects.requireNonNull(spawn, "spawn");
            if (!spawnIndices.add(candidate.index())) {
                throw new IllegalArgumentException("preparation spawn indices must be unique");
            }
            PreparationRegion region = regionsByTeam.get(candidate.team());
            if (region == null) {
                throw new IllegalArgumentException("preparation spawn team has no declared region");
            }
            if (!region.contains(candidate.position())) {
                throw new IllegalArgumentException(
                        "preparation spawn must be inside its team region");
            }
            teamsWithSpawns.add(candidate.team());
        }
        if (!teamsWithSpawns.equals(regionsByTeam.keySet())) {
            throw new IllegalArgumentException("every preparation region must contain a spawn");
        }
    }
}
