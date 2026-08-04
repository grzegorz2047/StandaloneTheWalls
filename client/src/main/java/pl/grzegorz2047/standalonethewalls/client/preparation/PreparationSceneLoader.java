package pl.grzegorz2047.standalonethewalls.client.preparation;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.mapformat.Glb2ContainerDecoder;
import pl.grzegorz2047.standalonethewalls.mapformat.Glb2Document;
import pl.grzegorz2047.standalonethewalls.mapformat.Glb2Exception;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationGameplay;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationMapSpawn;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationRegion;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationTeam;
import pl.grzegorz2047.standalonethewalls.mapformat.TwMapBundleException;
import pl.grzegorz2047.standalonethewalls.mapformat.TwMapBundleLoader;
import pl.grzegorz2047.standalonethewalls.mapformat.TwMapLoadPolicy;
import pl.grzegorz2047.standalonethewalls.mapformat.VerifiedMapBundle;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;

/** Loads the embedded preparation map and ties every scene input to the server assignment. */
public final class PreparationSceneLoader {
    private static final String SCENE_PATH = "scene.glb";
    private static final String COLLISION_PATH = "collision.glb";
    private static final TwMapLoadPolicy LOAD_POLICY =
            new TwMapLoadPolicy(2 * 1024 * 1024, 4 * 1024 * 1024, 16, 100);

    private PreparationSceneLoader() {
        throw new AssertionError("No instances");
    }

    public static VerifiedPreparationScene loadDefault(PreparationSpawnAssignment assignment)
            throws PreparationSceneLoadException {
        VerifiedMapBundle bundle;
        try {
            bundle =
                    TwMapBundleLoader.load(
                            MinimalPreparationBundle.createArchive(), LOAD_POLICY);
        } catch (TwMapBundleException exception) {
            throw new PreparationSceneLoadException(
                    PreparationSceneLoadException.Code.BUNDLE_LOAD_FAILED,
                    "embedded preparation bundle could not be verified",
                    exception);
        }
        return load(bundle, assignment);
    }

    static VerifiedPreparationScene load(
            VerifiedMapBundle bundle, PreparationSpawnAssignment assignment)
            throws PreparationSceneLoadException {
        VerifiedMapBundle verifiedBundle = Objects.requireNonNull(bundle, "bundle");
        PreparationSpawnAssignment authoritativeAssignment =
                Objects.requireNonNull(assignment, "assignment");
        if (!verifiedBundle.manifest().id().equals(authoritativeAssignment.mapId())) {
            throw failure(
                    PreparationSceneLoadException.Code.MAP_ID_MISMATCH,
                    "preparation assignment map id does not match the verified bundle");
        }

        byte[] verifiedArchiveSha256 =
                HexFormat.of().parseHex(verifiedBundle.archiveSha256().value());
        if (!MessageDigest.isEqual(
                verifiedArchiveSha256, authoritativeAssignment.mapSha256())) {
            throw failure(
                    PreparationSceneLoadException.Code.MAP_SHA256_MISMATCH,
                    "preparation assignment digest does not match the verified archive");
        }

        byte[] sceneGlb = verifiedBundle.member(SCENE_PATH);
        byte[] collisionGlb = verifiedBundle.member(COLLISION_PATH);
        Glb2Document sceneDocument = decodeScene(verifiedBundle, sceneGlb);
        Glb2Document collisionDocument = decodeCollision(verifiedBundle, collisionGlb);

        PreparationTeam team = preparationTeam(authoritativeAssignment.team());
        PreparationGameplay gameplay = verifiedBundle.gameplay();
        PreparationRegion region =
                gameplay.regions().stream()
                        .filter(candidate -> candidate.team() == team)
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        failure(
                                                PreparationSceneLoadException.Code.REGION_MISSING,
                                                "verified gameplay has no region for the assigned team"));
        PreparationMapSpawn spawn =
                gameplay.spawns().stream()
                        .filter(candidate -> candidate.index() == authoritativeAssignment.spawnIndex())
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        failure(
                                                PreparationSceneLoadException.Code.SPAWN_MISSING,
                                                "verified gameplay has no assigned spawn index"));
        requireMatchingSpawn(spawn, team, authoritativeAssignment);
        if (!region.contains(spawn.position())) {
            throw failure(
                    PreparationSceneLoadException.Code.SPAWN_STATE_MISMATCH,
                    "assigned spawn is outside the verified team region");
        }

        return new VerifiedPreparationScene(
                verifiedBundle.manifest().id(),
                verifiedArchiveSha256,
                sceneGlb,
                collisionGlb,
                sceneDocument,
                collisionDocument,
                region,
                spawn);
    }

    private static Glb2Document decodeScene(VerifiedMapBundle bundle, byte[] sceneGlb)
            throws PreparationSceneLoadException {
        try {
            Glb2Document document =
                    Glb2ContainerDecoder.decode(sceneGlb, bundle.manifest().limits());
            if (document.lightCount() < 1) {
                throw failure(
                        PreparationSceneLoadException.Code.SCENE_INVALID,
                        "verified preparation scene has no declared light");
            }
            return document;
        } catch (Glb2Exception exception) {
            throw new PreparationSceneLoadException(
                    PreparationSceneLoadException.Code.SCENE_INVALID,
                    "verified preparation scene GLB is invalid",
                    exception);
        }
    }

    private static Glb2Document decodeCollision(VerifiedMapBundle bundle, byte[] collisionGlb)
            throws PreparationSceneLoadException {
        try {
            return Glb2ContainerDecoder.decode(collisionGlb, bundle.manifest().limits());
        } catch (Glb2Exception exception) {
            throw new PreparationSceneLoadException(
                    PreparationSceneLoadException.Code.COLLISION_INVALID,
                    "verified preparation collision GLB is invalid",
                    exception);
        }
    }

    private static void requireMatchingSpawn(
            PreparationMapSpawn spawn,
            PreparationTeam team,
            PreparationSpawnAssignment assignment)
            throws PreparationSceneLoadException {
        if (spawn.team() != team
                || Double.compare(spawn.position().x(), assignment.x()) != 0
                || Double.compare(spawn.position().y(), assignment.y()) != 0
                || Double.compare(spawn.position().z(), assignment.z()) != 0
                || Double.compare(spawn.yawDegrees(), assignment.yawDegrees()) != 0) {
            throw failure(
                    PreparationSceneLoadException.Code.SPAWN_STATE_MISMATCH,
                    "preparation assignment does not match the verified spawn metadata");
        }
    }

    private static PreparationTeam preparationTeam(LobbyTeam team)
            throws PreparationSceneLoadException {
        return switch (Objects.requireNonNull(team, "team")) {
            case GREEN -> PreparationTeam.GREEN;
            case BLUE -> PreparationTeam.BLUE;
            case RED -> PreparationTeam.RED;
            case YELLOW -> PreparationTeam.YELLOW;
            case UNASSIGNED ->
                    throw failure(
                            PreparationSceneLoadException.Code.SPAWN_STATE_MISMATCH,
                            "preparation assignment cannot use the unassigned team");
        };
    }

    private static PreparationSceneLoadException failure(
            PreparationSceneLoadException.Code code, String message) {
        return new PreparationSceneLoadException(code, message);
    }
}
