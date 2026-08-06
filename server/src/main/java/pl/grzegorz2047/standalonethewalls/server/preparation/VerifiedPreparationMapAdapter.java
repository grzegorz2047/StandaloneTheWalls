package pl.grzegorz2047.standalonethewalls.server.preparation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;
import pl.grzegorz2047.standalonethewalls.mapformat.Glb2ContainerDecoder;
import pl.grzegorz2047.standalonethewalls.mapformat.Glb2Document;
import pl.grzegorz2047.standalonethewalls.mapformat.Glb2Exception;
import pl.grzegorz2047.standalonethewalls.mapformat.Glb2PreparationObstacleDecoder;
import pl.grzegorz2047.standalonethewalls.mapformat.Glb2PreparationSupportDecoder;
import pl.grzegorz2047.standalonethewalls.mapformat.MapManifest;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationGameplay;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationMapSpawn;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationObstacleException;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationObstacleMap;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationRegion;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationSupportException;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationSupportMap;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationTeam;
import pl.grzegorz2047.standalonethewalls.mapformat.VerifiedMapBundle;

/** Converts only a complete verified map bundle into the server-owned preparation definition. */
public final class VerifiedPreparationMapAdapter {
    private VerifiedPreparationMapAdapter() {
        throw new AssertionError("No instances");
    }

    public static PreparationMapDefinition adapt(VerifiedMapBundle bundle)
            throws VerifiedPreparationMapException {
        VerifiedMapBundle verified = Objects.requireNonNull(bundle, "bundle");
        VerifiedCollision collision = validateGlbMembers(verified);

        MapManifest manifest = verified.manifest();
        PreparationGameplay gameplay = verified.gameplay();
        validateCapacity(manifest, gameplay);

        List<PreparationSpawnPoint> spawnPoints = new ArrayList<>(gameplay.spawns().size());
        gameplay.spawns().stream()
                .sorted(Comparator.comparingInt(PreparationMapSpawn::index))
                .forEach(
                        spawn ->
                                spawnPoints.add(
                                        new PreparationSpawnPoint(
                                                spawn.index(),
                                                toTeamId(spawn.team()),
                                                spawn.position().x(),
                                                spawn.position().y(),
                                                spawn.position().z(),
                                                spawn.yawDegrees())));
        EnumMap<TeamId, PreparationRegionBounds> regions = new EnumMap<>(TeamId.class);
        for (PreparationRegion region : gameplay.regions()) {
            TeamId team = toTeamId(region.team());
            if (regions.put(team, PreparationRegionBounds.from(team, region)) != null) {
                throw failure(
                        VerifiedPreparationMapException.Code.MANIFEST_GAMEPLAY_MISMATCH,
                        "preparation gameplay contains duplicate team regions");
            }
        }
        try {
            return new PreparationMapDefinition(
                    manifest.id(),
                    decodeDigest(verified.archiveSha256().value()),
                    spawnPoints,
                    regions,
                    collision.supportMap(),
                    collision.obstacleMap());
        } catch (IllegalArgumentException exception) {
            throw new VerifiedPreparationMapException(
                    VerifiedPreparationMapException.Code.INVALID_COLLISION,
                    "verified preparation collision layout does not cover authoritative spawns",
                    exception);
        }
    }

    private static VerifiedCollision validateGlbMembers(VerifiedMapBundle bundle)
            throws VerifiedPreparationMapException {
        try {
            Glb2ContainerDecoder.decode(bundle.member("scene.glb"), bundle.manifest().limits());
        } catch (Glb2Exception exception) {
            throw new VerifiedPreparationMapException(
                    VerifiedPreparationMapException.Code.INVALID_SCENE,
                    "verified preparation scene GLB is invalid",
                    exception);
        }
        try {
            Glb2Document collision =
                    Glb2ContainerDecoder.decode(
                            bundle.member("collision.glb"), bundle.manifest().limits());
            return new VerifiedCollision(
                    Glb2PreparationSupportDecoder.decode(collision),
                    Glb2PreparationObstacleDecoder.decode(collision));
        } catch (Glb2Exception | PreparationSupportException | PreparationObstacleException exception) {
            throw new VerifiedPreparationMapException(
                    VerifiedPreparationMapException.Code.INVALID_COLLISION,
                    "verified preparation collision GLB or semantic metadata is invalid",
                    exception);
        }
    }

    private static void validateCapacity(MapManifest manifest, PreparationGameplay gameplay)
            throws VerifiedPreparationMapException {
        Set<PreparationTeam> regionTeams = new HashSet<>();
        for (PreparationRegion region : gameplay.regions()) {
            regionTeams.add(region.team());
        }
        if (regionTeams.size() != manifest.teamCount()
                || gameplay.regions().size() != manifest.teamCount()) {
            throw failure(
                    VerifiedPreparationMapException.Code.MANIFEST_GAMEPLAY_MISMATCH,
                    "preparation region count does not match the map manifest");
        }

        Map<PreparationTeam, Integer> spawnCounts = new EnumMap<>(PreparationTeam.class);
        for (PreparationMapSpawn spawn : gameplay.spawns()) {
            spawnCounts.merge(spawn.team(), 1, Integer::sum);
        }
        if (gameplay.spawns().size() < manifest.maximumPlayers()) {
            throw failure(
                    VerifiedPreparationMapException.Code.INSUFFICIENT_TEAM_SPAWNS,
                    "preparation gameplay does not cover the declared maximum player count");
        }
        for (PreparationTeam team : regionTeams) {
            if (spawnCounts.getOrDefault(team, 0) < manifest.playersPerTeam()) {
                throw failure(
                        VerifiedPreparationMapException.Code.INSUFFICIENT_TEAM_SPAWNS,
                        "preparation gameplay does not cover the declared team capacity");
            }
        }
    }

    private static TeamId toTeamId(PreparationTeam team) {
        return switch (Objects.requireNonNull(team, "team")) {
            case GREEN -> TeamId.GREEN;
            case BLUE -> TeamId.BLUE;
            case RED -> TeamId.RED;
            case YELLOW -> TeamId.YELLOW;
        };
    }

    private static byte[] decodeDigest(String hexadecimal) {
        byte[] digest = new byte[PreparationMapDefinition.SHA_256_BYTES];
        for (int index = 0; index < digest.length; index++) {
            int offset = index * 2;
            int high = Character.digit(hexadecimal.charAt(offset), 16);
            int low = Character.digit(hexadecimal.charAt(offset + 1), 16);
            if (high < 0 || low < 0) {
                throw new AssertionError("verified SHA-256 digest is not hexadecimal");
            }
            digest[index] = (byte) ((high << 4) | low);
        }
        return digest;
    }

    private static VerifiedPreparationMapException failure(
            VerifiedPreparationMapException.Code code, String message) {
        return new VerifiedPreparationMapException(code, message);
    }

    private record VerifiedCollision(
            PreparationSupportMap supportMap, PreparationObstacleMap obstacleMap) {
        private VerifiedCollision {
            supportMap = Objects.requireNonNull(supportMap, "supportMap");
            obstacleMap = Objects.requireNonNull(obstacleMap, "obstacleMap");
        }
    }
}
