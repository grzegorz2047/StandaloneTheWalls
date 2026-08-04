package pl.grzegorz2047.standalonethewalls.mapformat;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Bounded metadata and copied chunks from a structurally verified embedded GLB 2.0 document. */
public final class Glb2Document {
    private final byte[] jsonChunk;
    private final byte[] binaryChunk;
    private final int defaultScene;
    private final int sceneCount;
    private final int nodeCount;
    private final int meshCount;
    private final int materialCount;
    private final int lightCount;
    private final int declaredBufferBytes;

    Glb2Document(
            byte[] jsonChunk,
            byte[] binaryChunk,
            int defaultScene,
            int sceneCount,
            int nodeCount,
            int meshCount,
            int materialCount,
            int lightCount,
            int declaredBufferBytes) {
        this.jsonChunk = Objects.requireNonNull(jsonChunk, "jsonChunk").clone();
        this.binaryChunk = Objects.requireNonNull(binaryChunk, "binaryChunk").clone();
        this.defaultScene = defaultScene;
        this.sceneCount = sceneCount;
        this.nodeCount = nodeCount;
        this.meshCount = meshCount;
        this.materialCount = materialCount;
        this.lightCount = lightCount;
        this.declaredBufferBytes = declaredBufferBytes;
    }

    public byte[] jsonChunk() {
        return jsonChunk.clone();
    }

    public String jsonUtf8() {
        return new String(jsonChunk, StandardCharsets.UTF_8).stripTrailing();
    }

    public byte[] binaryChunk() {
        return binaryChunk.clone();
    }

    public int defaultScene() {
        return defaultScene;
    }

    public int sceneCount() {
        return sceneCount;
    }

    public int nodeCount() {
        return nodeCount;
    }

    public int meshCount() {
        return meshCount;
    }

    public int materialCount() {
        return materialCount;
    }

    public int lightCount() {
        return lightCount;
    }

    public int declaredBufferBytes() {
        return declaredBufferBytes;
    }
}
