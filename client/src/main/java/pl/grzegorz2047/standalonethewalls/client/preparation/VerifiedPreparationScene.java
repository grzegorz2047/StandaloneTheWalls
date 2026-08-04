package pl.grzegorz2047.standalonethewalls.client.preparation;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.mapformat.Glb2Document;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationMapSpawn;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationRegion;

/** Immutable client scene input created only after bundle, identity, and spawn verification. */
public final class VerifiedPreparationScene {
    private final String mapId;
    private final byte[] mapSha256;
    private final byte[] sceneGlb;
    private final byte[] collisionGlb;
    private final Glb2Document sceneDocument;
    private final Glb2Document collisionDocument;
    private final PreparationRegion region;
    private final PreparationMapSpawn spawn;

    VerifiedPreparationScene(
            String mapId,
            byte[] mapSha256,
            byte[] sceneGlb,
            byte[] collisionGlb,
            Glb2Document sceneDocument,
            Glb2Document collisionDocument,
            PreparationRegion region,
            PreparationMapSpawn spawn) {
        this.mapId = Objects.requireNonNull(mapId, "mapId");
        this.mapSha256 = Objects.requireNonNull(mapSha256, "mapSha256").clone();
        this.sceneGlb = Objects.requireNonNull(sceneGlb, "sceneGlb").clone();
        this.collisionGlb = Objects.requireNonNull(collisionGlb, "collisionGlb").clone();
        this.sceneDocument = Objects.requireNonNull(sceneDocument, "sceneDocument");
        this.collisionDocument = Objects.requireNonNull(collisionDocument, "collisionDocument");
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

    public PreparationRegion region() {
        return region;
    }

    public PreparationMapSpawn spawn() {
        return spawn;
    }
}
