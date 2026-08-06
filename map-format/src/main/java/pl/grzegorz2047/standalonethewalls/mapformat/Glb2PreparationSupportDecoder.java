package pl.grzegorz2047.standalonethewalls.mapformat;

import java.io.IOException;
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

/** Derives bounded axis-aligned support boxes from a structurally verified collision GLB. */
public final class Glb2PreparationSupportDecoder {
    private static final int FLOAT_COMPONENT_TYPE = 5126;
    private static final int MAXIMUM_ACCESSORS = 256;
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

    private Glb2PreparationSupportDecoder() {
        throw new AssertionError("No instances");
    }

    public static PreparationSupportMap decode(Glb2Document document)
            throws PreparationSupportException {
        Glb2Document verified = Objects.requireNonNull(document, "document");
        try (JsonParser parser =
                JSON_FACTORY.createParser(ObjectReadContext.empty(), verified.jsonChunk())) {
            requireToken(parser.nextToken(), JsonToken.START_OBJECT, "glTF root object");
            List<Accessor> accessors = null;
            List<Mesh> meshes = null;
            List<Node> nodes = null;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                requireToken(parser.currentToken(), JsonToken.PROPERTY_NAME, "glTF root property");
                String property = parser.currentName();
                JsonToken value = parser.nextToken();
                switch (property) {
                    case "accessors" -> accessors = readAccessors(parser, value);
                    case "meshes" -> meshes = readMeshes(parser, value);
                    case "nodes" -> nodes = readNodes(parser, value);
                    default -> parser.skipChildren();
                }
            }
            if (parser.nextToken() != null) {
                throw failure(
                        PreparationSupportException.Code.MALFORMED_JSON,
                        "collision GLB JSON contains trailing data");
            }
            if (accessors == null
                    || meshes == null
                    || nodes == null
                    || meshes.size() != verified.meshCount()
                    || nodes.size() != verified.nodeCount()) {
                throw failure(
                        PreparationSupportException.Code.MISSING_LAYOUT,
                        "collision GLB is missing support decoding metadata");
            }
            return buildSupportMap(accessors, meshes, nodes);
        } catch (PreparationSupportException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new PreparationSupportException(
                    PreparationSupportException.Code.MALFORMED_JSON,
                    "collision GLB support metadata could not be parsed",
                    exception);
        }
    }

    private static PreparationSupportMap buildSupportMap(
            List<Accessor> accessors, List<Mesh> meshes, List<Node> nodes)
            throws PreparationSupportException {
        List<PreparationSupportBox> boxes = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Node node : nodes) {
            if (!isSupportName(node.name())) {
                continue;
            }
            if (boxes.size() >= PreparationSupportMap.MAXIMUM_BOXES) {
                throw failure(
                        PreparationSupportException.Code.TOO_MANY_SUPPORTS,
                        "collision GLB contains too many support boxes");
            }
            if (!names.add(node.name())) {
                throw failure(
                        PreparationSupportException.Code.DUPLICATE_NAME,
                        "collision GLB repeats a support node name");
            }
            if (node.mesh() == null
                    || node.mesh() < 0
                    || node.mesh() >= meshes.size()
                    || node.translation() == null
                    || node.scale() == null
                    || node.rotatedOrMatrix()) {
                throw failure(
                        PreparationSupportException.Code.INVALID_NODE,
                        "support node is not an explicit axis-aligned mesh transform");
            }
            Mesh mesh = meshes.get(node.mesh());
            if (mesh.positionAccessor() == null
                    || mesh.positionAccessor() < 0
                    || mesh.positionAccessor() >= accessors.size()) {
                throw failure(
                        PreparationSupportException.Code.INVALID_MESH,
                        "support node mesh has no canonical POSITION accessor");
            }
            Accessor accessor = accessors.get(mesh.positionAccessor());
            if (!accessor.isUnitCubePosition()) {
                throw failure(
                        PreparationSupportException.Code.INVALID_ACCESSOR,
                        "support node POSITION accessor is not a unit cube");
            }
            double[] translation = node.translation();
            double[] scale = node.scale();
            for (double component : scale) {
                if (!Double.isFinite(component) || component <= 0.0d) {
                    throw failure(
                            PreparationSupportException.Code.INVALID_NODE,
                            "support node scale must be finite and positive");
                }
            }
            for (double component : translation) {
                if (!Double.isFinite(component)) {
                    throw failure(
                            PreparationSupportException.Code.INVALID_NODE,
                            "support node translation must be finite");
                }
            }
            try {
                boxes.add(
                        new PreparationSupportBox(
                                node.name(),
                                new MapVector3(
                                        translation[0] - scale[0] / 2.0d,
                                        translation[1] - scale[1] / 2.0d,
                                        translation[2] - scale[2] / 2.0d),
                                new MapVector3(
                                        translation[0] + scale[0] / 2.0d,
                                        translation[1] + scale[1] / 2.0d,
                                        translation[2] + scale[2] / 2.0d)));
            } catch (IllegalArgumentException exception) {
                throw new PreparationSupportException(
                        PreparationSupportException.Code.INVALID_NODE,
                        "support node produces invalid world bounds",
                        exception);
            }
        }
        try {
            return new PreparationSupportMap(boxes);
        } catch (IllegalArgumentException exception) {
            throw new PreparationSupportException(
                    PreparationSupportException.Code.MISSING_LAYOUT,
                    "collision GLB support layout is invalid",
                    exception);
        }
    }

    private static List<Accessor> readAccessors(JsonParser parser, JsonToken token)
            throws IOException, PreparationSupportException {
        requireToken(token, JsonToken.START_ARRAY, "accessors array");
        List<Accessor> accessors = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (accessors.size() >= MAXIMUM_ACCESSORS) {
                throw failure(
                        PreparationSupportException.Code.MISSING_LAYOUT,
                        "collision GLB contains too many accessors");
            }
            requireToken(parser.currentToken(), JsonToken.START_OBJECT, "accessor object");
            Integer componentType = null;
            String type = null;
            double[] minimum = null;
            double[] maximum = null;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                requireToken(parser.currentToken(), JsonToken.PROPERTY_NAME, "accessor property");
                String property = parser.currentName();
                JsonToken value = parser.nextToken();
                switch (property) {
                    case "componentType" ->
                            componentType = readInteger(parser, value, "accessor component type");
                    case "type" -> {
                        requireToken(value, JsonToken.VALUE_STRING, "accessor type");
                        type = parser.getString();
                    }
                    case "min" -> minimum = readVector3(parser, value, "accessor minimum");
                    case "max" -> maximum = readVector3(parser, value, "accessor maximum");
                    default -> parser.skipChildren();
                }
            }
            accessors.add(new Accessor(componentType, type, minimum, maximum));
        }
        return List.copyOf(accessors);
    }

    private static List<Mesh> readMeshes(JsonParser parser, JsonToken token)
            throws IOException, PreparationSupportException {
        requireToken(token, JsonToken.START_ARRAY, "meshes array");
        List<Mesh> meshes = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            requireToken(parser.currentToken(), JsonToken.START_OBJECT, "mesh object");
            Integer positionAccessor = null;
            boolean canonicalPrimitive = true;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                requireToken(parser.currentToken(), JsonToken.PROPERTY_NAME, "mesh property");
                String property = parser.currentName();
                JsonToken value = parser.nextToken();
                if (!"primitives".equals(property)) {
                    parser.skipChildren();
                    continue;
                }
                requireToken(value, JsonToken.START_ARRAY, "mesh primitives array");
                int primitives = 0;
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    requireToken(parser.currentToken(), JsonToken.START_OBJECT, "mesh primitive");
                    primitives++;
                    Integer currentPosition = readPrimitivePosition(parser);
                    if (primitives == 1) {
                        positionAccessor = currentPosition;
                    } else {
                        canonicalPrimitive = false;
                    }
                }
                canonicalPrimitive &= primitives == 1;
            }
            meshes.add(new Mesh(canonicalPrimitive ? positionAccessor : null));
        }
        return List.copyOf(meshes);
    }

    private static Integer readPrimitivePosition(JsonParser parser)
            throws IOException, PreparationSupportException {
        Integer positionAccessor = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            requireToken(parser.currentToken(), JsonToken.PROPERTY_NAME, "primitive property");
            String property = parser.currentName();
            JsonToken value = parser.nextToken();
            if (!"attributes".equals(property)) {
                parser.skipChildren();
                continue;
            }
            requireToken(value, JsonToken.START_OBJECT, "primitive attributes object");
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                requireToken(parser.currentToken(), JsonToken.PROPERTY_NAME, "attribute property");
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
        return positionAccessor;
    }

    private static List<Node> readNodes(JsonParser parser, JsonToken token)
            throws IOException, PreparationSupportException {
        requireToken(token, JsonToken.START_ARRAY, "nodes array");
        List<Node> nodes = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            requireToken(parser.currentToken(), JsonToken.START_OBJECT, "node object");
            String name = null;
            Integer mesh = null;
            double[] translation = null;
            double[] scale = null;
            boolean rotatedOrMatrix = false;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                requireToken(parser.currentToken(), JsonToken.PROPERTY_NAME, "node property");
                String property = parser.currentName();
                JsonToken value = parser.nextToken();
                switch (property) {
                    case "name" -> {
                        requireToken(value, JsonToken.VALUE_STRING, "node name");
                        name = parser.getString();
                    }
                    case "mesh" -> mesh = readInteger(parser, value, "node mesh index");
                    case "translation" ->
                            translation = readVector3(parser, value, "node translation");
                    case "scale" -> scale = readVector3(parser, value, "node scale");
                    case "rotation", "matrix" -> {
                        rotatedOrMatrix = true;
                        parser.skipChildren();
                    }
                    default -> parser.skipChildren();
                }
            }
            nodes.add(new Node(name, mesh, translation, scale, rotatedOrMatrix));
        }
        return List.copyOf(nodes);
    }

    private static double[] readVector3(JsonParser parser, JsonToken token, String description)
            throws IOException, PreparationSupportException {
        requireToken(token, JsonToken.START_ARRAY, description + " array");
        double[] values = new double[3];
        for (int index = 0; index < values.length; index++) {
            JsonToken component = parser.nextToken();
            if (component == JsonToken.END_ARRAY) {
                throw failure(
                        PreparationSupportException.Code.MALFORMED_JSON,
                        description + " must contain exactly three values");
            }
            values[index] = readNumber(parser, component, description + " component");
        }
        requireToken(parser.nextToken(), JsonToken.END_ARRAY, description + " end");
        return values;
    }

    private static int readInteger(JsonParser parser, JsonToken token, String description)
            throws IOException, PreparationSupportException {
        requireToken(token, JsonToken.VALUE_NUMBER_INT, description + " integer");
        return parser.getIntValue();
    }

    private static double readNumber(JsonParser parser, JsonToken token, String description)
            throws IOException, PreparationSupportException {
        if (token != JsonToken.VALUE_NUMBER_INT && token != JsonToken.VALUE_NUMBER_FLOAT) {
            throw failure(
                    PreparationSupportException.Code.MALFORMED_JSON,
                    description + " must be numeric");
        }
        double value = parser.getDoubleValue();
        if (!Double.isFinite(value)) {
            throw failure(
                    PreparationSupportException.Code.INVALID_NODE,
                    description + " must be finite");
        }
        return value;
    }

    private static void requireToken(JsonToken actual, JsonToken expected, String description)
            throws PreparationSupportException {
        if (actual != expected) {
            throw failure(
                    PreparationSupportException.Code.MALFORMED_JSON,
                    description + " has an invalid JSON token");
        }
    }

    private static boolean isSupportName(String name) {
        return name != null
                && ("GroundCollision".equals(name) || name.endsWith("SupportCollision"));
    }

    private static PreparationSupportException failure(
            PreparationSupportException.Code code, String message) {
        return new PreparationSupportException(code, message);
    }

    private record Accessor(
            Integer componentType, String type, double[] minimum, double[] maximum) {
        private boolean isUnitCubePosition() {
            return componentType != null
                    && componentType == FLOAT_COMPONENT_TYPE
                    && "VEC3".equals(type)
                    && vectorEquals(minimum, -0.5d)
                    && vectorEquals(maximum, 0.5d);
        }

        private static boolean vectorEquals(double[] value, double expected) {
            return value != null
                    && value.length == 3
                    && Double.compare(value[0], expected) == 0
                    && Double.compare(value[1], expected) == 0
                    && Double.compare(value[2], expected) == 0;
        }
    }

    private record Mesh(Integer positionAccessor) {}

    private record Node(
            String name,
            Integer mesh,
            double[] translation,
            double[] scale,
            boolean rotatedOrMatrix) {}
}
