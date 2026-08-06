package pl.grzegorz2047.standalonethewalls.client.preparation;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import pl.grzegorz2047.standalonethewalls.mapformat.Glb2Document;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationBarrierPolicy;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationMapSpawn;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationObstacleMap;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationRegion;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationSupportMap;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationWorldBounds;

/** Verified client scene input plus one-way central-barrier state for the owned round. */
public final class VerifiedPreparationScene {
    private final String mapId;
    private final byte[] mapSha256;
    private final byte[] sceneGlb;
    private final byte[] collisionGlb;
    private final Glb2Document sceneDocument;
    private final Glb2Document collisionDocument;
    private final PreparationSupportMap supportMap;
    private final PreparationObstacleMap obstacleMap;
    private final PreparationWorldBounds worldBounds;
    private final PreparationRegion region;
    private final PreparationMapSpawn spawn;
    private final AtomicReference<PreparationBarrierPolicy> barrierPolicy =
            new AtomicReference<>(PreparationBarrierPolicy.CLOSED);

    VerifiedPreparationScene(
            String mapId,
            byte[] mapSha256,
            byte[] sceneGlb,
            byte[] collisionGlb,
            Glb2Document sceneDocument,
            Glb2Document collisionDocument,
            PreparationSupportMap supportMap,
            PreparationObstacleMap obstacleMap,
            PreparationRegion region,
            PreparationMapSpawn spawn) {
        this(
                mapId,
                mapSha256,
                sceneGlb,
                collisionGlb,
                sceneDocument,
                collisionDocument,
                supportMap,
                obstacleMap,
                PreparationWorldBounds.fromRegions(List.of(region)),
                region,
                spawn);
    }

    VerifiedPreparationScene(
            String mapId,
            byte[] mapSha256,
            byte[] sceneGlb,
            byte[] collisionGlb,
            Glb2Document sceneDocument,
            Glb2Document collisionDocument,
            PreparationSupportMap supportMap,
            PreparationObstacleMap obstacleMap,
            PreparationWorldBounds worldBounds,
            PreparationRegion region,
            PreparationMapSpawn spawn) {
        this.mapId = Objects.requireNonNull(mapId, "mapId");
        this.mapSha256 = Objects.requireNonNull(mapSha256, "mapSha256").clone();
        this.sceneGlb = Objects.requireNonNull(sceneGlb, "sceneGlb").clone();
        this.collisionGlb = Objects.requireNonNull(collisionGlb, "collisionGlb").clone();
        this.sceneDocument = Objects.requireNonNull(sceneDocument, "sceneDocument");
        this.collisionDocument = Objects.requireNonNull(collisionDocument, "collisionDocument");
        this.supportMap = Objects.requireNonNull(supportMap, "supportMap");
        this.obstacleMap = Objects.requireNonNull(obstacleMap, "obstacleMap");
        this.worldBounds = Objects.requireNonNull(worldBounds, "worldBounds");
        this.region = Objects.requireNonNull(region, "region");
        this.spawn = Objects.requireNonNull(spawn, "spawn");
    }

    public String mapId() {
        return mapId;
    }

    public byte[] mapSha256() {
        return mapSha256.clone();
    }

    public byte[] sceneGlb() {
        return sceneGlb.clone();
    }

    public byte[] collisionGlb() {
        return collisionGlb.clone();
    }

    public Glb2Document sceneDocument() {
        return sceneDocument;
    }

    public Glb2Document collisionDocument() {
        return collisionDocument;
    }

    public PreparationSupportMap supportMap() {
        return supportMap;
    }

    public PreparationObstacleMap obstacleMap() {
        return obstacleMap;
    }

    public PreparationWorldBounds worldBounds() {
        return worldBounds;
    }

    public PreparationRegion region() {
        return region;
    }

    public PreparationMapSpawn spawn() {
        return spawn;
    }

    public PreparationBarrierPolicy barrierPolicy() {
        return barrierPolicy.get();
    }

    public boolean openCentralBarriers() {
        return barrierPolicy.compareAndSet(
                PreparationBarrierPolicy.CLOSED, PreparationBarrierPolicy.OPEN);
    }
}
