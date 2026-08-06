package pl.grzegorz2047.standalonethewalls.mapformat;

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
        int[] cornerCodes = readCornerCodes(positions, bufferViews, binary, declaredBufferBytes);
        return cornerCodes.length == POSITION_COUNT
                && hasCanonicalTopology(
                        indices, bufferViews, binary, declaredBufferBytes, cornerCodes);
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
            return new int[0];
        }
        BufferView view = bufferViews.get(accessor.bufferView());
        int stride = view.byteStride() == null ? POSITION_ELEMENT_BYTES : view.byteStride();
        if (stride < POSITION_ELEMENT_BYTES || stride > 252 || (stride & 3) != 0) {
            return new int[0];
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
            return new int[0];
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
                return new int[0];
            }
            cornerCodes[index] = code;
            occurrences[code]++;
        }
        for (int occurrence : occurrences) {
            if (occurrence != 3) {
                return new int[0];
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
            int vertex =
                    Short.toUnsignedInt(
                            input.getShort(range.start() + index * INDEX_ELEMENT_BYTES));
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
            if (faceTriangleCounts[face] != 2 || Integer.bitCount(faceCornerMasks[face]) != 4) {
                return false;
            }
        }
        return true;
    }

    private static int face(int first, int second, int third) {
        int face = -1;
        for (int axis = 0; axis < 3; axis++) {
            int firstSign = (first >>> axis) & 1;
            if (firstSign == ((second >>> axis) & 1) && firstSign == ((third >>> axis) & 1)) {
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
                    case "normalized" -> normalized = readBoolean(value, "accessor normalized");
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
                                    readInteger(parser, attributeValue, "POSITION accessor index");
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
        private static final long serialVersionUID = 1L;

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
            Integer positionAccessor, Integer indexAccessor, Integer mode, boolean morphTargets) {}

    private record Range(int start, int stride) {}
}
