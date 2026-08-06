package pl.grzegorz2047.standalonethewalls.mapformat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

final class CanonicalCollisionGlbFixture {
    static final int POSITION_BYTES = 288;
    static final int INDEX_BYTES = 72;

    private static final float[][] POSITIONS = {
        {0.5f, -0.5f, -0.5f}, {0.5f, -0.5f, 0.5f}, {0.5f, 0.5f, 0.5f},
        {0.5f, 0.5f, -0.5f}, {-0.5f, -0.5f, 0.5f}, {-0.5f, -0.5f, -0.5f},
        {-0.5f, 0.5f, -0.5f}, {-0.5f, 0.5f, 0.5f}, {-0.5f, 0.5f, -0.5f},
        {0.5f, 0.5f, -0.5f}, {0.5f, 0.5f, 0.5f}, {-0.5f, 0.5f, 0.5f},
        {-0.5f, -0.5f, 0.5f}, {0.5f, -0.5f, 0.5f}, {0.5f, -0.5f, -0.5f},
        {-0.5f, -0.5f, -0.5f}, {0.5f, -0.5f, 0.5f}, {-0.5f, -0.5f, 0.5f},
        {-0.5f, 0.5f, 0.5f}, {0.5f, 0.5f, 0.5f}, {-0.5f, -0.5f, -0.5f},
        {0.5f, -0.5f, -0.5f}, {0.5f, 0.5f, -0.5f}, {-0.5f, 0.5f, -0.5f}
    };
    private static final int[] INDICES = {
        0, 1, 2, 0, 2, 3,
        4, 5, 6, 4, 6, 7,
        8, 9, 10, 8, 10, 11,
        12, 13, 14, 12, 14, 15,
        16, 17, 18, 16, 18, 19,
        20, 21, 22, 20, 22, 23
    };

    private CanonicalCollisionGlbFixture() {
        throw new AssertionError("No instances");
    }

    static Glb2Document document(String positionAccessor, String nodes, String sceneNodes)
            throws Glb2Exception {
        return document(positionAccessor, nodes, sceneNodes, canonicalBinary());
    }

    static Glb2Document document(
            String positionAccessor, String nodes, String sceneNodes, byte[] binary)
            throws Glb2Exception {
        return meshDocument(
                positionAccessor,
                canonicalIndexAccessor(),
                canonicalBufferViews(),
                binary.length,
                canonicalMesh(),
                nodes,
                sceneNodes,
                binary);
    }

    static Glb2Document canonicalMeshDocument(byte[] binary) throws Glb2Exception {
        return meshDocument(
                canonicalPositionAccessor(),
                canonicalIndexAccessor(),
                canonicalBufferViews(),
                binary.length,
                canonicalMesh(),
                "[{\"mesh\":0,\"name\":\"Fixture\"}]",
                "[0]",
                binary);
    }

    static Glb2Document meshDocument(
            String positionAccessor,
            String indexAccessor,
            String bufferViews,
            int declaredBufferBytes,
            String mesh,
            String nodes,
            String sceneNodes,
            byte[] binary)
            throws Glb2Exception {
        String json =
                "{\"accessors\":["
                        + positionAccessor
                        + ","
                        + indexAccessor
                        + "],\"asset\":{\"version\":\"2.0\"},\"bufferViews\":"
                        + bufferViews
                        + ",\"buffers\":[{\"byteLength\":"
                        + declaredBufferBytes
                        + "}],\"meshes\":["
                        + mesh
                        + "],\"nodes\":"
                        + nodes
                        + ",\"scene\":0,\"scenes\":[{\"nodes\":"
                        + sceneNodes
                        + "}]}";
        return Glb2ContainerDecoder.decode(glb(json, binary), limits());
    }

    static String canonicalPositionAccessor() {
        return "{\"bufferView\":0,\"componentType\":5126,\"count\":24,"
                + "\"max\":[0.5,0.5,0.5],\"min\":[-0.5,-0.5,-0.5],\"type\":\"VEC3\"}";
    }

    static String canonicalIndexAccessor() {
        return "{\"bufferView\":1,\"componentType\":5123,\"count\":36,\"type\":\"SCALAR\"}";
    }

    static String canonicalBufferViews() {
        return "[{\"buffer\":0,\"byteLength\":288,\"byteOffset\":0},"
                + "{\"buffer\":0,\"byteLength\":72,\"byteOffset\":288}]";
    }

    static String stridedBufferViews(int stride) {
        int positionBytes = POSITIONS.length * stride;
        return "[{\"buffer\":0,\"byteLength\":"
                + positionBytes
                + ",\"byteOffset\":0,\"byteStride\":"
                + stride
                + "},{\"buffer\":0,\"byteLength\":72,\"byteOffset\":"
                + positionBytes
                + "}]";
    }

    static String canonicalMesh() {
        return "{\"primitives\":[{\"attributes\":{\"POSITION\":0},\"indices\":1}]}";
    }

    static byte[] canonicalBinary() {
        return binary(POSITIONS, INDICES, 12);
    }

    static byte[] tamperedPositionBinary(float value) {
        byte[] binary = canonicalBinary();
        ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN).putFloat(0, value);
        return binary;
    }

    static byte[] permutedBinary() {
        float[][] positions = new float[POSITIONS.length][];
        for (int index = 0; index < POSITIONS.length; index++) {
            positions[index] = POSITIONS[POSITIONS.length - 1 - index];
        }
        int[] indices = new int[INDICES.length];
        int target = 0;
        for (int triangle = INDICES.length / 3 - 1; triangle >= 0; triangle--) {
            int offset = triangle * 3;
            indices[target++] = POSITIONS.length - 1 - INDICES[offset + 2];
            indices[target++] = POSITIONS.length - 1 - INDICES[offset + 1];
            indices[target++] = POSITIONS.length - 1 - INDICES[offset];
        }
        return binary(positions, indices, 12);
    }

    static byte[] stridedBinary(int stride) {
        return binary(POSITIONS, INDICES, stride);
    }

    private static byte[] binary(float[][] positions, int[] indices, int stride) {
        ByteBuffer output =
                ByteBuffer.allocate(positions.length * stride + indices.length * Short.BYTES)
                        .order(ByteOrder.LITTLE_ENDIAN);
        for (float[] position : positions) {
            int start = output.position();
            output.putFloat(position[0]).putFloat(position[1]).putFloat(position[2]);
            output.position(start + stride);
        }
        for (int index : indices) {
            output.putShort((short) index);
        }
        return output.array();
    }

    private static MapLimits limits() {
        return new MapLimits(1, 1024 * 1024, 5, 256, 256, 64);
    }

    private static byte[] glb(String json, byte[] binary) {
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        int paddedJsonBytes = (jsonBytes.length + 3) & ~3;
        int paddedBinaryBytes = (binary.length + 3) & ~3;
        int totalBytes = 12 + 8 + paddedJsonBytes + 8 + paddedBinaryBytes;
        ByteBuffer output = ByteBuffer.allocate(totalBytes).order(ByteOrder.LITTLE_ENDIAN);
        output.putInt(0x46546C67).putInt(2).putInt(totalBytes);
        output.putInt(paddedJsonBytes).putInt(0x4E4F534A).put(jsonBytes);
        while (output.position() < 20 + paddedJsonBytes) {
            output.put((byte) ' ');
        }
        output.putInt(paddedBinaryBytes).putInt(0x004E4942).put(binary);
        while (output.hasRemaining()) {
            output.put((byte) 0);
        }
        return output.array();
    }
}
