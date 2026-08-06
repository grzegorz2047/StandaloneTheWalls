from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    target = ROOT / path
    source = target.read_text(encoding="utf-8")
    if source.count(old) != 1:
        raise SystemExit(f"expected exactly one marker in {path}: {old[:80]!r}")
    target.write_text(source.replace(old, new), encoding="utf-8")


write(
    "map-format/src/main/java/pl/grzegorz2047/standalonethewalls/mapformat/Glb2CanonicalBoxMeshVerifier.java",
    r'''package pl.grzegorz2047.standalonethewalls.mapformat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;

/** Verifies that semantic collision boxes reference real closed unit-cube mesh bytes. */
final class Glb2CanonicalBoxMeshVerifier {
    private static final int FLOAT_COMPONENT_TYPE = 5126;
    private static final int UNSIGNED_SHORT_COMPONENT_TYPE = 5123;
    private static final int TRIANGLES_MODE = 4;
    private static final int POSITION_COUNT = 24;
    private static final int INDEX_COUNT = 36;
    private static final int POSITION_ELEMENT_BYTES = 12;
    private static final int INDEX_ELEMENT_BYTES = 2;
    private static final int MAXIMUM_ACCESSORS = 256;
    private static final int MAXIMUM_BUFFER_VIEWS = 256;
    private static final JsonFactory JSON_FACTORY =
            JsonFactory.builder()
                    .streamReadConstraints(
                            StreamReadConstraints.builder()
                                    .maxNestingDepth(32)
                                    .maxNumberLength(32)
                                    .maxStringLength(4096)
                                    .maxNameLength(256)
                                    .build())
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build();

    private Glb2CanonicalBoxMeshVerifier() {
        throw new AssertionError("No instances");
    }

    static Set<Integer> verifiedMeshes(Glb2Document document) throws VerificationException {
        Glb2Document verified = Objects.requireNonNull(document, "document");
        try (JsonParser parser =
                JSON_FACTORY.createParser(ObjectReadContext.empty(), verified.jsonChunk())) {
            requireToken(parser.nextToken(), JsonToken.START_OBJECT, "glTF root object");
            List<Accessor> accessors = null;
            List<BufferView> bufferViews = null;
            List<Mesh> meshes = null;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                requireToken(parser.currentToken(), JsonToken.PROPERTY_NAME, "glTF root property");
                String property = parser.currentName();
                JsonToken value = parser.nextToken();
                switch (property) {
                    case "accessors" -> accessors = readAccessors(parser, value);
                    case "bufferViews" -> bufferViews = readBufferViews(parser, value);
                    case "meshes" -> meshes = readMeshes(parser, value);
                    default -> parser.skipChildren();
                }
            }
            if (parser.nextToken() != null) {
                throw failure(Code.MALFORMED_JSON, "collision GLB JSON contains trailing data");
            }
            if (accessors == null
                    || bufferViews == null
                    || meshes == null
                    || meshes.size() != verified.meshCount()) {
                throw failure(
                        Code.MISSING_LAYOUT,
                        "collision GLB is missing canonical box mesh metadata");
            }
            byte[] binary = verified.binaryChunk();
            int declaredBufferBytes = verified.declaredBufferBytes();
            if (declaredBufferBytes < 1 || declaredBufferBytes > binary.length) {
                throw failure(
                        Code.MISSING_LAYOUT,
                        "collision GLB declared buffer is unavailable for box verification");
            }
            Set<Integer> canonical = new HashSet<>();
            for (int meshIndex = 0; meshIndex < meshes.size(); meshIndex++) {
                if (isCanonicalBoxMesh(
                        meshes.get(meshIndex),
                        accessors,
                        bufferViews,
                        binary,
                        declaredBufferBytes)) {
                    canonical.add(meshIndex);
                }
            }
            return Set.copyOf(canonical);
        } catch (VerificationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new VerificationException(
                    Code.MALFORMED_JSON,
                    "collision GLB canonical box metadata could not be parsed",
                    exception);
        }
    }

    private static boolean isCanonicalBoxMesh(
            Mesh mesh,
            List<Accessor> accessors,
            List<BufferView> bufferViews,
            byte[] binary,
            int declaredBufferBytes) {
        Primitive primitive = mesh.primitive();
        if (primitive == null
                || primitive.mode() == null
                || primitive.mode() != TRIANGLES_MODE
                || primitive.morphTargets()
                || primitive.positionAccessor() == null
                || primitive.indexAccessor() == null
                || primitive.positionAccessor() < 0
                || primitive.positionAccessor() >= accessors.size()
                || primitive.indexAccessor() < 0
                || primitive.indexAccessor() >= accessors.size()) {
            return false;
        }
        Accessor positions = accessors.get(primitive.positionAccessor());
        Accessor indices = accessors.get(primitive.indexAccessor());
        int[] cornerCodes =
                readCornerCodes(
                        positions, bufferViews, binary, declaredBufferBytes);
        return cornerCodes != null
                && hasCanonicalTopology(
                        indices,
                        bufferViews,
                        binary,
                        declaredBufferBytes,
                        cornerCodes);
    }

    private static int[] readCornerCodes(
            Accessor accessor,
            List<BufferView> bufferViews,
            byte[] binary,
            int declaredBufferBytes) {
        if (accessor.bufferView() == null
                || accessor.componentType() == null
                || accessor.componentType() != FLOAT_COMPONENT_TYPE
                || accessor.count() == null
                || accessor.count() != POSITION_COUNT
                || !"VEC3".equals(accessor.type())
                || accessor.normalized()
                || accessor.sparse()
                || !isVector(accessor.minimum(), -0.5d, -0.5d, -0.5d)
                || !isVector(accessor.maximum(), 0.5d, 0.5d, 0.5d)
                || accessor.bufferView() < 0
                || accessor.bufferView() >= bufferViews.size()) {
            return null;
        }
        BufferView view = bufferViews.get(accessor.bufferView());
        int stride = view.byteStride() == null ? POSITION_ELEMENT_BYTES : view.byteStride();
        if (stride < POSITION_ELEMENT_BYTES || stride > 252 || (stride & 3) != 0) {
            return null;
        }
        Range range =
                range(
                        accessor,
                        view,
                        POSITION_COUNT,
                        POSITION_ELEMENT_BYTES,
                        stride,
                        declaredBufferBytes,
                        binary.length);
        if (range == null || (range.start() & 3) != 0) {
            return null;
        }
        ByteBuffer input = ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN);
        int[] cornerCodes = new int[POSITION_COUNT];
        int[] occurrences = new int[8];
        for (int index = 0; index < POSITION_COUNT; index++) {
            int offset = range.start() + index * range.stride();
            int code =
                    cornerCode(
                            input.getFloat(offset),
                            input.getFloat(offset + Float.BYTES),
                            input.getFloat(offset + Float.BYTES * 2));
            if (code < 0) {
                return null;
            }
            cornerCodes[index] = code;
            occurrences[code]++;
        }
        for (int occurrence : occurrences) {
            if (occurrence != 3) {
                return null;
            }
        }
        return cornerCodes;
    }

    private static boolean hasCanonicalTopology(
            Accessor accessor,
            List<BufferView> bufferViews,
            byte[] binary,
            int declaredBufferBytes,
            int[] cornerCodes) {
        if (accessor.bufferView() == null
                || accessor.componentType() == null
                || accessor.componentType() != UNSIGNED_SHORT_COMPONENT_TYPE
                || accessor.count() == null
                || accessor.count() != INDEX_COUNT
                || !"SCALAR".equals(accessor.type())
                || accessor.normalized()
                || accessor.sparse()
                || accessor.bufferView() < 0
                || accessor.bufferView() >= bufferViews.size()) {
            return false;
        }
        BufferView view = bufferViews.get(accessor.bufferView());
        if (view.byteStride() != null) {
            return false;
        }
        Range range =
                range(
                        accessor,
                        view,
                        INDEX_COUNT,
                        INDEX_ELEMENT_BYTES,
                        INDEX_ELEMENT_BYTES,
                        declaredBufferBytes,
                        binary.length);
        if (range == null || (range.start() & 1) != 0) {
            return false;
        }
        ByteBuffer input = ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN);
        int[] indices = new int[INDEX_COUNT];
        boolean[] usedVertices = new boolean[POSITION_COUNT];
        for (int index = 0; index < INDEX_COUNT; index++) {
            int vertex = Short.toUnsignedInt(input.getShort(range.start() + index * INDEX_ELEMENT_BYTES));
            if (vertex >= POSITION_COUNT) {
                return false;
            }
            indices[index] = vertex;
            usedVertices[vertex] = true;
        }
        for (boolean used : usedVertices) {
            if (!used) {
                return false;
            }
        }

        int[] faceTriangleCounts = new int[6];
        int[] faceCornerMasks = new int[6];
        for (int index = 0; index < INDEX_COUNT; index += 3) {
            int first = cornerCodes[indices[index]];
            int second = cornerCodes[indices[index + 1]];
            int third = cornerCodes[indices[index + 2]];
            if (first == second || first == third || second == third) {
                return false;
            }
            int face = face(first, second, third);
            if (face < 0) {
                return false;
            }
            faceTriangleCounts[face]++;
            faceCornerMasks[face] |= (1 << first) | (1 << second) | (1 << third);
        }
        for (int face = 0; face < faceTriangleCounts.length; face++) {
            if (faceTriangleCounts[face] != 2
                    || Integer.bitCount(faceCornerMasks[face]) != 4) {
                return false;
            }
        }
        return true;
    }

    private static int face(int first, int second, int third) {
        int face = -1;
        for (int axis = 0; axis < 3; axis++) {
            int firstSign = (first >>> axis) & 1;
            if (firstSign == ((second >>> axis) & 1)
                    && firstSign == ((third >>> axis) & 1)) {
                if (face >= 0) {
                    return -1;
                }
                face = axis * 2 + firstSign;
            }
        }
        return face;
    }

    private static int cornerCode(float x, float y, float z) {
        int xSign = coordinateSign(x);
        int ySign = coordinateSign(y);
        int zSign = coordinateSign(z);
        if (xSign < 0 || ySign < 0 || zSign < 0) {
            return -1;
        }
        return xSign | (ySign << 1) | (zSign << 2);
    }

    private static int coordinateSign(float value) {
        if (!Float.isFinite(value)) {
            return -1;
        }
        if (Float.compare(value, -0.5f) == 0) {
            return 0;
        }
        return Float.compare(value, 0.5f) == 0 ? 1 : -1;
    }

    private static Range range(
            Accessor accessor,
            BufferView view,
            int count,
            int elementBytes,
            int stride,
            int declaredBufferBytes,
            int binaryBytes) {
        if (accessor.byteOffset() == null
                || accessor.byteOffset() < 0
                || view.buffer() == null
                || view.buffer() != 0
                || view.byteOffset() == null
                || view.byteOffset() < 0
                || view.byteLength() == null
                || view.byteLength() < elementBytes) {
            return null;
        }
        long viewStart = view.byteOffset();
        long viewEnd = viewStart + view.byteLength();
        long start = viewStart + accessor.byteOffset();
        long end = start + (long) (count - 1) * stride + elementBytes;
        if (viewEnd < viewStart
                || start < viewStart
                || end < start
                || end > viewEnd
                || viewEnd > declaredBufferBytes
                || viewEnd > binaryBytes
                || start > Integer.MAX_VALUE) {
            return null;
        }
        return new Range((int) start, stride);
    }

    private static boolean isVector(MapVector3 vector, double x, double y, double z) {
        return vector != null
                && Double.compare(vector.x(), x) == 0
                && Double.compare(vector.y(), y) == 0
                && Double.compare(vector.z(), z) == 0;
    }

    private static List<Accessor> readAccessors(JsonParser parser, JsonToken token)
            throws IOException, VerificationException {
        requireToken(token, JsonToken.START_ARRAY, "accessors array");
        List<Accessor> accessors = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (accessors.size() >= MAXIMUM_ACCESSORS) {
                throw failure(Code.MISSING_LAYOUT, "collision GLB contains too many accessors");
            }
            requireToken(parser.currentToken(), JsonToken.START_OBJECT, "accessor object");
            Integer bufferView = null;
            Integer byteOffset = 0;
            Integer componentType = null;
            Integer count = null;
            String type = null;
            MapVector3 minimum = null;
            MapVector3 maximum = null;
            boolean normalized = false;
            boolean sparse = false;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                requireToken(parser.currentToken(), JsonToken.PROPERTY_NAME, "accessor property");
                String property = parser.currentName();
                JsonToken value = parser.nextToken();
                switch (property) {
                    case "bufferView" ->
                            bufferView = readInteger(parser, value, "accessor bufferView");
                    case "byteOffset" ->
                            byteOffset = readInteger(parser, value, "accessor byteOffset");
                    case "componentType" ->
                            componentType = readInteger(parser, value, "accessor componentType");
                    case "count" -> count = readInteger(parser, value, "accessor count");
                    case "type" -> {
                        requireToken(value, JsonToken.VALUE_STRING, "accessor type");
                        type = parser.getString();
                    }
                    case "min" -> minimum = readVector3(parser, value, "accessor minimum");
                    case "max" -> maximum = readVector3(parser, value, "accessor maximum");
                    case "normalized" ->
                            normalized = readBoolean(value, "accessor normalized");
                    case "sparse" -> {
                        sparse = true;
                        parser.skipChildren();
                    }
                    default -> parser.skipChildren();
                }
            }
            accessors.add(
                    new Accessor(
                            bufferView,
                            byteOffset,
                            componentType,
                            count,
                            type,
                            minimum,
                            maximum,
                            normalized,
                            sparse));
        }
        return List.copyOf(accessors);
    }

    private static List<BufferView> readBufferViews(JsonParser parser, JsonToken token)
            throws IOException, VerificationException {
        requireToken(token, JsonToken.START_ARRAY, "bufferViews array");
        List<BufferView> bufferViews = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (bufferViews.size() >= MAXIMUM_BUFFER_VIEWS) {
                throw failure(Code.MISSING_LAYOUT, "collision GLB contains too many bufferViews");
            }
            requireToken(parser.currentToken(), JsonToken.START_OBJECT, "bufferView object");
            Integer buffer = null;
            Integer byteOffset = 0;
            Integer byteLength = null;
            Integer byteStride = null;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                requireToken(parser.currentToken(), JsonToken.PROPERTY_NAME, "bufferView property");
                String property = parser.currentName();
                JsonToken value = parser.nextToken();
                switch (property) {
                    case "buffer" -> buffer = readInteger(parser, value, "bufferView buffer");
                    case "byteOffset" ->
                            byteOffset = readInteger(parser, value, "bufferView byteOffset");
                    case "byteLength" ->
                            byteLength = readInteger(parser, value, "bufferView byteLength");
                    case "byteStride" ->
                            byteStride = readInteger(parser, value, "bufferView byteStride");
                    default -> parser.skipChildren();
                }
            }
            bufferViews.add(new BufferView(buffer, byteOffset, byteLength, byteStride));
        }
        return List.copyOf(bufferViews);
    }

    private static List<Mesh> readMeshes(JsonParser parser, JsonToken token)
            throws IOException, VerificationException {
        requireToken(token, JsonToken.START_ARRAY, "meshes array");
        List<Mesh> meshes = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            requireToken(parser.currentToken(), JsonToken.START_OBJECT, "mesh object");
            Primitive primitive = null;
            int primitiveCount = 0;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                requireToken(parser.currentToken(), JsonToken.PROPERTY_NAME, "mesh property");
                String property = parser.currentName();
                JsonToken value = parser.nextToken();
                if (!"primitives".equals(property)) {
                    parser.skipChildren();
                    continue;
                }
                requireToken(value, JsonToken.START_ARRAY, "mesh primitives array");
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    requireToken(parser.currentToken(), JsonToken.START_OBJECT, "mesh primitive");
                    Primitive current = readPrimitive(parser);
                    primitiveCount++;
                    if (primitiveCount == 1) {
                        primitive = current;
                    }
                }
            }
            meshes.add(new Mesh(primitiveCount == 1 ? primitive : null));
        }
        return List.copyOf(meshes);
    }

    private static Primitive readPrimitive(JsonParser parser)
            throws IOException, VerificationException {
        Integer positionAccessor = null;
        Integer indexAccessor = null;
        Integer mode = TRIANGLES_MODE;
        boolean morphTargets = false;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            requireToken(parser.currentToken(), JsonToken.PROPERTY_NAME, "primitive property");
            String property = parser.currentName();
            JsonToken value = parser.nextToken();
            switch (property) {
                case "attributes" -> {
                    requireToken(value, JsonToken.START_OBJECT, "primitive attributes object");
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        requireToken(
                                parser.currentToken(),
                                JsonToken.PROPERTY_NAME,
                                "primitive attribute property");
                        String attribute = parser.currentName();
                        JsonToken attributeValue = parser.nextToken();
                        if ("POSITION".equals(attribute)) {
                            positionAccessor =
                                    readInteger(
                                            parser,
                                            attributeValue,
                                            "POSITION accessor index");
                        } else {
                            parser.skipChildren();
                        }
                    }
                }
                case "indices" ->
                        indexAccessor = readInteger(parser, value, "indices accessor index");
                case "mode" -> mode = readInteger(parser, value, "primitive mode");
                case "targets" -> {
                    morphTargets = true;
                    parser.skipChildren();
                }
                default -> parser.skipChildren();
            }
        }
        return new Primitive(positionAccessor, indexAccessor, mode, morphTargets);
    }

    private static List<Integer> readVectorTokens(JsonParser parser, JsonToken token, String description)
            throws IOException, VerificationException {
        requireToken(token, JsonToken.START_ARRAY, description + " array");
        List<Integer> ignored = new ArrayList<>(3);
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            ignored.add(0);
            if (ignored.size() > 3) {
                throw failure(Code.MALFORMED_JSON, description + " must contain three values");
            }
        }
        return ignored;
    }

    private static MapVector3 readVector3(JsonParser parser, JsonToken token, String description)
            throws IOException, VerificationException {
        requireToken(token, JsonToken.START_ARRAY, description + " array");
        double x = readRequiredNumber(parser, description);
        double y = readRequiredNumber(parser, description);
        double z = readRequiredNumber(parser, description);
        requireToken(parser.nextToken(), JsonToken.END_ARRAY, description + " end");
        return new MapVector3(x, y, z);
    }

    private static double readRequiredNumber(JsonParser parser, String description)
            throws IOException, VerificationException {
        JsonToken component = parser.nextToken();
        if (component == JsonToken.END_ARRAY) {
            throw failure(Code.MALFORMED_JSON, description + " must contain exactly three values");
        }
        if (component != JsonToken.VALUE_NUMBER_INT && component != JsonToken.VALUE_NUMBER_FLOAT) {
            throw failure(Code.MALFORMED_JSON, description + " component must be numeric");
        }
        double value = parser.getDoubleValue();
        if (!Double.isFinite(value)) {
            throw failure(Code.MALFORMED_JSON, description + " component must be finite");
        }
        return value;
    }

    private static int readInteger(JsonParser parser, JsonToken token, String description)
            throws IOException, VerificationException {
        requireToken(token, JsonToken.VALUE_NUMBER_INT, description + " integer");
        return parser.getIntValue();
    }

    private static boolean readBoolean(JsonToken token, String description)
            throws VerificationException {
        if (token == JsonToken.VALUE_TRUE) {
            return true;
        }
        if (token == JsonToken.VALUE_FALSE) {
            return false;
        }
        throw failure(Code.MALFORMED_JSON, description + " must be boolean");
    }

    private static void requireToken(JsonToken actual, JsonToken expected, String description)
            throws VerificationException {
        if (actual != expected) {
            throw failure(Code.MALFORMED_JSON, description + " has an unexpected token");
        }
    }

    private static VerificationException failure(Code code, String message) {
        return new VerificationException(code, message);
    }

    enum Code {
        MALFORMED_JSON,
        MISSING_LAYOUT
    }

    static final class VerificationException extends Exception {
        private final Code code;

        private VerificationException(Code code, String message) {
            super(message);
            this.code = Objects.requireNonNull(code, "code");
        }

        private VerificationException(Code code, String message, Throwable cause) {
            super(message, cause);
            this.code = Objects.requireNonNull(code, "code");
        }

        boolean malformedJson() {
            return code == Code.MALFORMED_JSON;
        }
    }

    private record Accessor(
            Integer bufferView,
            Integer byteOffset,
            Integer componentType,
            Integer count,
            String type,
            MapVector3 minimum,
            MapVector3 maximum,
            boolean normalized,
            boolean sparse) {}

    private record BufferView(
            Integer buffer, Integer byteOffset, Integer byteLength, Integer byteStride) {}

    private record Mesh(Primitive primitive) {}

    private record Primitive(
            Integer positionAccessor,
            Integer indexAccessor,
            Integer mode,
            boolean morphTargets) {}

    private record Range(int start, int stride) {}
}
''',
)

write(
    "map-format/src/test/java/pl/grzegorz2047/standalonethewalls/mapformat/CanonicalCollisionGlbFixture.java",
    r'''package pl.grzegorz2047.standalonethewalls.mapformat;

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
''',
)

write(
    "map-format/src/test/java/pl/grzegorz2047/standalonethewalls/mapformat/Glb2CanonicalBoxMeshVerifierTest.java",
    r'''package pl.grzegorz2047.standalonethewalls.mapformat;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Glb2CanonicalBoxMeshVerifierTest {
    @Test
    void acceptsCanonicalCubeAndEquivalentPermutation()
            throws Glb2Exception, Glb2CanonicalBoxMeshVerifier.VerificationException {
        assertThat(
                        Glb2CanonicalBoxMeshVerifier.verifiedMeshes(
                                CanonicalCollisionGlbFixture.canonicalMeshDocument(
                                        CanonicalCollisionGlbFixture.canonicalBinary())))
                .containsExactly(0);
        assertThat(
                        Glb2CanonicalBoxMeshVerifier.verifiedMeshes(
                                CanonicalCollisionGlbFixture.canonicalMeshDocument(
                                        CanonicalCollisionGlbFixture.permutedBinary())))
                .containsExactly(0);
    }

    @Test
    void acceptsBoundedInterleavedPositionStride()
            throws Glb2Exception, Glb2CanonicalBoxMeshVerifier.VerificationException {
        int stride = 16;
        byte[] binary = CanonicalCollisionGlbFixture.stridedBinary(stride);
        Glb2Document document =
                CanonicalCollisionGlbFixture.meshDocument(
                        CanonicalCollisionGlbFixture.canonicalPositionAccessor(),
                        CanonicalCollisionGlbFixture.canonicalIndexAccessor(),
                        CanonicalCollisionGlbFixture.stridedBufferViews(stride),
                        binary.length,
                        CanonicalCollisionGlbFixture.canonicalMesh(),
                        "[{\"mesh\":0,\"name\":\"Fixture\"}]",
                        "[0]",
                        binary);

        assertThat(Glb2CanonicalBoxMeshVerifier.verifiedMeshes(document)).containsExactly(0);
    }

    @Test
    void rejectsTamperedAndNonFinitePositionBytes()
            throws Glb2Exception, Glb2CanonicalBoxMeshVerifier.VerificationException {
        assertRejected(CanonicalCollisionGlbFixture.tamperedPositionBinary(0.25f));
        assertRejected(CanonicalCollisionGlbFixture.tamperedPositionBinary(Float.NaN));
        assertRejected(CanonicalCollisionGlbFixture.tamperedPositionBinary(Float.POSITIVE_INFINITY));
    }

    @Test
    void rejectsNormalizedSparseTruncatedAndInvalidStrideLayouts()
            throws Glb2Exception, Glb2CanonicalBoxMeshVerifier.VerificationException {
        byte[] canonical = CanonicalCollisionGlbFixture.canonicalBinary();
        String normalized =
                "{\"normalized\":true,"
                        + CanonicalCollisionGlbFixture.canonicalPositionAccessor().substring(1);
        assertRejected(
                CanonicalCollisionGlbFixture.meshDocument(
                        normalized,
                        CanonicalCollisionGlbFixture.canonicalIndexAccessor(),
                        CanonicalCollisionGlbFixture.canonicalBufferViews(),
                        canonical.length,
                        CanonicalCollisionGlbFixture.canonicalMesh(),
                        "[{\"mesh\":0,\"name\":\"Fixture\"}]",
                        "[0]",
                        canonical));

        String sparse =
                "{\"sparse\":{\"count\":1},"
                        + CanonicalCollisionGlbFixture.canonicalPositionAccessor().substring(1);
        assertRejected(
                CanonicalCollisionGlbFixture.meshDocument(
                        sparse,
                        CanonicalCollisionGlbFixture.canonicalIndexAccessor(),
                        CanonicalCollisionGlbFixture.canonicalBufferViews(),
                        canonical.length,
                        CanonicalCollisionGlbFixture.canonicalMesh(),
                        "[{\"mesh\":0,\"name\":\"Fixture\"}]",
                        "[0]",
                        canonical));

        String truncatedViews =
                "[{\"buffer\":0,\"byteLength\":287,\"byteOffset\":0},"
                        + "{\"buffer\":0,\"byteLength\":72,\"byteOffset\":288}]";
        assertRejected(
                CanonicalCollisionGlbFixture.meshDocument(
                        CanonicalCollisionGlbFixture.canonicalPositionAccessor(),
                        CanonicalCollisionGlbFixture.canonicalIndexAccessor(),
                        truncatedViews,
                        canonical.length,
                        CanonicalCollisionGlbFixture.canonicalMesh(),
                        "[{\"mesh\":0,\"name\":\"Fixture\"}]",
                        "[0]",
                        canonical));

        String invalidStrideViews =
                "[{\"buffer\":0,\"byteLength\":288,\"byteOffset\":0,\"byteStride\":10},"
                        + "{\"buffer\":0,\"byteLength\":72,\"byteOffset\":288}]";
        assertRejected(
                CanonicalCollisionGlbFixture.meshDocument(
                        CanonicalCollisionGlbFixture.canonicalPositionAccessor(),
                        CanonicalCollisionGlbFixture.canonicalIndexAccessor(),
                        invalidStrideViews,
                        canonical.length,
                        CanonicalCollisionGlbFixture.canonicalMesh(),
                        "[{\"mesh\":0,\"name\":\"Fixture\"}]",
                        "[0]",
                        canonical));
    }

    @Test
    void rejectsOutOfRangeDegenerateMissingFaceAndUnusedVertexIndices()
            throws Glb2Exception, Glb2CanonicalBoxMeshVerifier.VerificationException {
        byte[] outOfRange = CanonicalCollisionGlbFixture.canonicalBinary();
        ByteBuffer.wrap(outOfRange)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort(CanonicalCollisionGlbFixture.POSITION_BYTES, (short) 24);
        assertRejected(outOfRange);

        byte[] degenerate = CanonicalCollisionGlbFixture.canonicalBinary();
        ByteBuffer degenerateBuffer = ByteBuffer.wrap(degenerate).order(ByteOrder.LITTLE_ENDIAN);
        short first = degenerateBuffer.getShort(CanonicalCollisionGlbFixture.POSITION_BYTES);
        degenerateBuffer.putShort(CanonicalCollisionGlbFixture.POSITION_BYTES + Short.BYTES, first);
        assertRejected(degenerate);

        byte[] missingFace = CanonicalCollisionGlbFixture.canonicalBinary();
        ByteBuffer missingFaceBuffer = ByteBuffer.wrap(missingFace).order(ByteOrder.LITTLE_ENDIAN);
        for (int index = 0; index < 6; index++) {
            short source =
                    missingFaceBuffer.getShort(
                            CanonicalCollisionGlbFixture.POSITION_BYTES + index * Short.BYTES);
            missingFaceBuffer.putShort(
                    CanonicalCollisionGlbFixture.POSITION_BYTES
                            + (CanonicalCollisionGlbFixture.INDEX_BYTES / Short.BYTES - 6 + index)
                                    * Short.BYTES,
                    source);
        }
        assertRejected(missingFace);

        byte[] unusedVertex = CanonicalCollisionGlbFixture.canonicalBinary();
        ByteBuffer unusedVertexBuffer = ByteBuffer.wrap(unusedVertex).order(ByteOrder.LITTLE_ENDIAN);
        for (int index = 0; index < CanonicalCollisionGlbFixture.INDEX_BYTES / Short.BYTES; index++) {
            int offset = CanonicalCollisionGlbFixture.POSITION_BYTES + index * Short.BYTES;
            if (Short.toUnsignedInt(unusedVertexBuffer.getShort(offset)) == 23) {
                unusedVertexBuffer.putShort(offset, (short) 22);
            }
        }
        assertRejected(unusedVertex);
    }

    private static void assertRejected(byte[] binary)
            throws Glb2Exception, Glb2CanonicalBoxMeshVerifier.VerificationException {
        assertRejected(CanonicalCollisionGlbFixture.canonicalMeshDocument(binary));
    }

    private static void assertRejected(Glb2Document document)
            throws Glb2CanonicalBoxMeshVerifier.VerificationException {
        Set<Integer> verified = Glb2CanonicalBoxMeshVerifier.verifiedMeshes(document);
        assertThat(verified).isEmpty();
    }
}
''',
)

obstacle_path = (
    "map-format/src/main/java/pl/grzegorz2047/standalonethewalls/mapformat/"
    "Glb2PreparationObstacleDecoder.java"
)
replace_once(
    obstacle_path,
    '        Glb2Document verified = Objects.requireNonNull(document, "document");\n'
    '        try (JsonParser parser =',
    '        Glb2Document verified = Objects.requireNonNull(document, "document");\n'
    '        Set<Integer> canonicalBoxMeshes = verifiedBoxMeshes(verified);\n'
    '        try (JsonParser parser =',
)
replace_once(
    obstacle_path,
    '            return buildObstacleMap(\n'
    '                    accessors, meshes, nodes, scenes.get(verified.defaultScene()).nodeIndices());',
    '            return buildObstacleMap(\n'
    '                    accessors,\n'
    '                    meshes,\n'
    '                    nodes,\n'
    '                    scenes.get(verified.defaultScene()).nodeIndices(),\n'
    '                    canonicalBoxMeshes);',
)
replace_once(
    obstacle_path,
    '            List<Node> nodes,\n'
    '            List<Integer> defaultSceneNodeIndices)\n'
    '            throws PreparationObstacleException {',
    '            List<Node> nodes,\n'
    '            List<Integer> defaultSceneNodeIndices,\n'
    '            Set<Integer> canonicalBoxMeshes)\n'
    '            throws PreparationObstacleException {',
)
replace_once(
    obstacle_path,
    '            if (!accessor.isUnitCubePosition()) {\n'
    '                throw failure(\n'
    '                        PreparationObstacleException.Code.INVALID_ACCESSOR,\n'
    '                        "obstacle node POSITION accessor is not a unit cube");\n'
    '            }',
    '            if (!accessor.isUnitCubePosition()) {\n'
    '                throw failure(\n'
    '                        PreparationObstacleException.Code.INVALID_ACCESSOR,\n'
    '                        "obstacle node POSITION accessor is not a unit cube");\n'
    '            }\n'
    '            if (!canonicalBoxMeshes.contains(node.mesh())) {\n'
    '                throw failure(\n'
    '                        PreparationObstacleException.Code.INVALID_ACCESSOR,\n'
    '                        "obstacle node mesh bytes are not a closed canonical unit cube");\n'
    '            }',
)
replace_once(
    obstacle_path,
    '    private static PreparationObstacleException failure(\n'
    '            PreparationObstacleException.Code code, String message) {\n'
    '        return new PreparationObstacleException(code, message);\n'
    '    }',
    '    private static Set<Integer> verifiedBoxMeshes(Glb2Document document)\n'
    '            throws PreparationObstacleException {\n'
    '        try {\n'
    '            return Glb2CanonicalBoxMeshVerifier.verifiedMeshes(document);\n'
    '        } catch (Glb2CanonicalBoxMeshVerifier.VerificationException exception) {\n'
    '            PreparationObstacleException.Code code =\n'
    '                    exception.malformedJson()\n'
    '                            ? PreparationObstacleException.Code.MALFORMED_JSON\n'
    '                            : PreparationObstacleException.Code.MISSING_LAYOUT;\n'
    '            throw new PreparationObstacleException(\n'
    '                    code, "collision GLB box mesh bytes could not be verified", exception);\n'
    '        }\n'
    '    }\n\n'
    '    private static PreparationObstacleException failure(\n'
    '            PreparationObstacleException.Code code, String message) {\n'
    '        return new PreparationObstacleException(code, message);\n'
    '    }',
)

support_path = (
    "map-format/src/main/java/pl/grzegorz2047/standalonethewalls/mapformat/"
    "Glb2PreparationSupportDecoder.java"
)
replace_once(
    support_path,
    '        Glb2Document verified = Objects.requireNonNull(document, "document");\n'
    '        try (JsonParser parser =',
    '        Glb2Document verified = Objects.requireNonNull(document, "document");\n'
    '        Set<Integer> canonicalBoxMeshes = verifiedBoxMeshes(verified);\n'
    '        try (JsonParser parser =',
)
replace_once(
    support_path,
    '            return buildSupportMap(\n'
    '                    accessors, meshes, nodes, scenes.get(verified.defaultScene()).nodeIndices());',
    '            return buildSupportMap(\n'
    '                    accessors,\n'
    '                    meshes,\n'
    '                    nodes,\n'
    '                    scenes.get(verified.defaultScene()).nodeIndices(),\n'
    '                    canonicalBoxMeshes);',
)
replace_once(
    support_path,
    '            List<Node> nodes,\n'
    '            List<Integer> defaultSceneNodeIndices)\n'
    '            throws PreparationSupportException {',
    '            List<Node> nodes,\n'
    '            List<Integer> defaultSceneNodeIndices,\n'
    '            Set<Integer> canonicalBoxMeshes)\n'
    '            throws PreparationSupportException {',
)
replace_once(
    support_path,
    '            if (!accessor.isUnitCubePosition()) {\n'
    '                throw failure(\n'
    '                        PreparationSupportException.Code.INVALID_ACCESSOR,\n'
    '                        "support node POSITION accessor is not a unit cube");\n'
    '            }',
    '            if (!accessor.isUnitCubePosition()) {\n'
    '                throw failure(\n'
    '                        PreparationSupportException.Code.INVALID_ACCESSOR,\n'
    '                        "support node POSITION accessor is not a unit cube");\n'
    '            }\n'
    '            if (!canonicalBoxMeshes.contains(node.mesh())) {\n'
    '                throw failure(\n'
    '                        PreparationSupportException.Code.INVALID_ACCESSOR,\n'
    '                        "support node mesh bytes are not a closed canonical unit cube");\n'
    '            }',
)
replace_once(
    support_path,
    '    private static PreparationSupportException failure(\n'
    '            PreparationSupportException.Code code, String message) {\n'
    '        return new PreparationSupportException(code, message);\n'
    '    }',
    '    private static Set<Integer> verifiedBoxMeshes(Glb2Document document)\n'
    '            throws PreparationSupportException {\n'
    '        try {\n'
    '            return Glb2CanonicalBoxMeshVerifier.verifiedMeshes(document);\n'
    '        } catch (Glb2CanonicalBoxMeshVerifier.VerificationException exception) {\n'
    '            PreparationSupportException.Code code =\n'
    '                    exception.malformedJson()\n'
    '                            ? PreparationSupportException.Code.MALFORMED_JSON\n'
    '                            : PreparationSupportException.Code.MISSING_LAYOUT;\n'
    '            throw new PreparationSupportException(\n'
    '                    code, "collision GLB box mesh bytes could not be verified", exception);\n'
    '        }\n'
    '    }\n\n'
    '    private static PreparationSupportException failure(\n'
    '            PreparationSupportException.Code code, String message) {\n'
    '        return new PreparationSupportException(code, message);\n'
    '    }',
)

for test_path, exception_type, node_json in (
    (
        "map-format/src/test/java/pl/grzegorz2047/standalonethewalls/mapformat/Glb2PreparationObstacleDecoderTest.java",
        "PreparationObstacleException",
        "{\\\"mesh\\\":0,\\\"name\\\":\\\"TamperedWallCollision\\\",\\\"scale\\\":[1,1,1],\\\"translation\\\":[0,0,0]}",
    ),
    (
        "map-format/src/test/java/pl/grzegorz2047/standalonethewalls/mapformat/Glb2PreparationSupportDecoderTest.java",
        "PreparationSupportException",
        "{\\\"mesh\\\":0,\\\"name\\\":\\\"GroundCollision\\\",\\\"scale\\\":[20,0.2,20],\\\"translation\\\":[0,-0.1,0]}",
    ),
):
    target = ROOT / test_path
    source = target.read_text(encoding="utf-8")
    source = source.replace("import java.nio.ByteBuffer;\n", "")
    source = source.replace("import java.nio.ByteOrder;\n", "")
    source = source.replace("import java.nio.charset.StandardCharsets;\n", "")
    marker = "    private static Glb2Document document(String accessor, String nodes, String sceneNodes)"
    start = source.index(marker)
    canonical_marker = "    private static String canonicalAccessor()"
    canonical_start = source.index(canonical_marker, start)
    delegate = (
        "    private static Glb2Document document(String accessor, String nodes, String sceneNodes)\n"
        "            throws Glb2Exception {\n"
        "        return CanonicalCollisionGlbFixture.document(accessor, nodes, sceneNodes);\n"
        "    }\n\n"
    )
    source = source[:start] + delegate + source[canonical_start:]
    old_canonical_end = source.index("    private static void assertCode", source.index(canonical_marker))
    source = (
        source[: source.index(canonical_marker)]
        + "    private static String canonicalAccessor() {\n"
        + "        return CanonicalCollisionGlbFixture.canonicalPositionAccessor();\n"
        + "    }\n\n"
        + source[old_canonical_end:]
    )
    limits_marker = "\n    private static MapLimits limits()"
    if limits_marker in source:
        source = source[: source.index(limits_marker)] + "\n}\n"
    insertion_marker = "    private static Glb2Document document(String accessor, String nodes, String sceneNodes)"
    test_method = (
        "    @Test\n"
        "    void rejectsTamperedPositionBytesEvenWhenAccessorBoundsRemainCanonical()\n"
        "            throws Glb2Exception {\n"
        "        byte[] binary = CanonicalCollisionGlbFixture.tamperedPositionBinary(0.25f);\n"
        "        Glb2Document document =\n"
        "                CanonicalCollisionGlbFixture.document(\n"
        "                        canonicalAccessor(),\n"
        f"                        \"[{node_json}]\",\n"
        "                        \"[0]\",\n"
        "                        binary);\n\n"
        "        assertCode(document, "
        + exception_type
        + ".Code.INVALID_ACCESSOR);\n"
        "    }\n\n"
    )
    source = source.replace(insertion_marker, test_method + insertion_marker, 1)
    target.write_text(source, encoding="utf-8")

print("issue 194 sources applied")
