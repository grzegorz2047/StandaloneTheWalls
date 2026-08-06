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

/** Derives bounded axis-aligned obstacle boxes from a structurally verified collision GLB. */
public final class Glb2PreparationObstacleDecoder {
    private static final int FLOAT_COMPONENT_TYPE = 5126;
    private static final int MAXIMUM_ACCESSORS = 256;
    private static final String WALL_SUFFIX = "WallCollision";
    private static final String WALL_X_SUFFIX = "WallXCollision";
    private static final String WALL_Z_SUFFIX = "WallZCollision";
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

    private Glb2PreparationObstacleDecoder() {
        throw new AssertionError("No instances");
    }

    public static PreparationObstacleMap decode(Glb2Document document)
            throws PreparationObstacleException {
        Glb2Document verified = Objects.requireNonNull(document, "document");
        Set<Integer> canonicalBoxMeshes = verifiedBoxMeshes(verified);
        try (JsonParser parser =
                JSON_FACTORY.createParser(ObjectReadContext.empty(), verified.jsonChunk())) {
            requireToken(parser.nextToken(), JsonToken.START_OBJECT, "glTF root object");
            List<Accessor> accessors = null;
            List<Mesh> meshes = null;
            List<Node> nodes = null;
            List<Scene> scenes = null;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                requireToken(parser.currentToken(), JsonToken.PROPERTY_NAME, "glTF root property");
                String property = parser.currentName();
                JsonToken value = parser.nextToken();
                switch (property) {
                    case "accessors" -> accessors = readAccessors(parser, value);
                    case "meshes" -> meshes = readMeshes(parser, value);
                    case "nodes" -> nodes = readNodes(parser, value);
                    case "scenes" -> scenes = readScenes(parser, value);
                    default -> parser.skipChildren();
                }
            }
            if (parser.nextToken() != null) {
                throw failure(
                        PreparationObstacleException.Code.MALFORMED_JSON,
                        "collision GLB JSON contains trailing data");
            }
            if (accessors == null
                    || meshes == null
                    || nodes == null
                    || scenes == null
                    || meshes.size() != verified.meshCount()
                    || nodes.size() != verified.nodeCount()
                    || scenes.size() != verified.sceneCount()
                    || verified.defaultScene() < 0
                    || verified.defaultScene() >= scenes.size()) {
                throw failure(
                        PreparationObstacleException.Code.MISSING_LAYOUT,
                        "collision GLB is missing obstacle decoding metadata");
            }
            return buildObstacleMap(
                    accessors,
                    meshes,
                    nodes,
                    scenes.get(verified.defaultScene()).nodeIndices(),
                    canonicalBoxMeshes);
        } catch (PreparationObstacleException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new PreparationObstacleException(
                    PreparationObstacleException.Code.MALFORMED_JSON,
                    "collision GLB obstacle metadata could not be parsed",
                    exception);
        }
    }

    private static PreparationObstacleMap buildObstacleMap(
            List<Accessor> accessors,
            List<Mesh> meshes,
            List<Node> nodes,
            List<Integer> defaultSceneNodeIndices,
            Set<Integer> canonicalBoxMeshes)
            throws PreparationObstacleException {
        Set<Integer> defaultSceneNodes = validatedIndices(defaultSceneNodeIndices, nodes.size());
        Set<Integer> childNodes = new HashSet<>();
        for (Node node : nodes) {
            childNodes.addAll(validatedIndices(node.children(), nodes.size()));
        }

        List<PreparationObstacleBox> boxes = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
            Node node = nodes.get(nodeIndex);
            if (!isObstacleName(node.name())) {
                continue;
            }
            if (!defaultSceneNodes.contains(nodeIndex)
                    || childNodes.contains(nodeIndex)
                    || !node.children().isEmpty()) {
                throw failure(
                        PreparationObstacleException.Code.INVALID_NODE,
                        "obstacle node must be a direct leaf of the default scene");
            }
            if (boxes.size() >= PreparationObstacleMap.MAXIMUM_BOXES) {
                throw failure(
                        PreparationObstacleException.Code.TOO_MANY_OBSTACLES,
                        "collision GLB contains too many obstacle boxes");
            }
            if (!names.add(node.name())) {
                throw failure(
                        PreparationObstacleException.Code.DUPLICATE_NAME,
                        "collision GLB repeats an obstacle node name");
            }
            if (node.mesh() == null
                    || node.mesh() < 0
                    || node.mesh() >= meshes.size()
                    || node.translation() == null
                    || node.scale() == null
                    || node.rotatedOrMatrix()) {
                throw failure(
                        PreparationObstacleException.Code.INVALID_NODE,
                        "obstacle node is not an explicit axis-aligned mesh transform");
            }
            Mesh mesh = meshes.get(node.mesh());
            if (mesh.positionAccessor() == null
                    || mesh.positionAccessor() < 0
                    || mesh.positionAccessor() >= accessors.size()) {
                throw failure(
                        PreparationObstacleException.Code.INVALID_MESH,
                        "obstacle node mesh has no canonical POSITION accessor");
            }
            Accessor accessor = accessors.get(mesh.positionAccessor());
            if (!accessor.isUnitCubePosition()) {
                throw failure(
                        PreparationObstacleException.Code.INVALID_ACCESSOR,
                        "obstacle node POSITION accessor is not a unit cube");
            }
            if (!canonicalBoxMeshes.contains(node.mesh())) {
                throw failure(
                        PreparationObstacleException.Code.INVALID_ACCESSOR,
                        "obstacle node mesh bytes are not a closed canonical unit cube");
            }
            MapVector3 translation = node.translation();
            MapVector3 scale = node.scale();
            if (scale.x() <= 0.0d || scale.y() <= 0.0d || scale.z() <= 0.0d) {
                throw failure(
                        PreparationObstacleException.Code.INVALID_NODE,
                        "obstacle node scale must be finite and positive");
            }
            try {
                boxes.add(
                        new PreparationObstacleBox(
                                node.name(),
                                new MapVector3(
                                        translation.x() - scale.x() / 2.0d,
                                        translation.y() - scale.y() / 2.0d,
                                        translation.z() - scale.z() / 2.0d),
                                new MapVector3(
                                        translation.x() + scale.x() / 2.0d,
                                        translation.y() + scale.y() / 2.0d,
                                        translation.z() + scale.z() / 2.0d)));
            } catch (IllegalArgumentException exception) {
                throw new PreparationObstacleException(
                        PreparationObstacleException.Code.INVALID_NODE,
                        "obstacle node produces invalid world bounds",
                        exception);
            }
        }
        try {
            return new PreparationObstacleMap(boxes);
        } catch (IllegalArgumentException exception) {
            throw new PreparationObstacleException(
                    PreparationObstacleException.Code.MISSING_LAYOUT,
                    "collision GLB obstacle layout is invalid",
                    exception);
        }
    }

    private static List<Accessor> readAccessors(JsonParser parser, JsonToken token)
            throws IOException, PreparationObstacleException {
        requireToken(token, JsonToken.START_ARRAY, "accessors array");
        List<Accessor> accessors = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (accessors.size() >= MAXIMUM_ACCESSORS) {
                throw failure(
                        PreparationObstacleException.Code.MISSING_LAYOUT,
                        "collision GLB contains too many accessors");
            }
            requireToken(parser.currentToken(), JsonToken.START_OBJECT, "accessor object");
            Integer componentType = null;
            String type = null;
            MapVector3 minimum = null;
            MapVector3 maximum = null;
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
            throws IOException, PreparationObstacleException {
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
            throws IOException, PreparationObstacleException {
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
            throws IOException, PreparationObstacleException {
        requireToken(token, JsonToken.START_ARRAY, "nodes array");
        List<Node> nodes = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            requireToken(parser.currentToken(), JsonToken.START_OBJECT, "node object");
            String name = null;
            Integer mesh = null;
            MapVector3 translation = null;
            MapVector3 scale = null;
            List<Integer> children = List.of();
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
                    case "children" -> children = readIndices(parser, value, "node children");
                    case "rotation", "matrix" -> {
                        rotatedOrMatrix = true;
                        parser.skipChildren();
                    }
                    default -> parser.skipChildren();
                }
            }
            nodes.add(new Node(name, mesh, translation, scale, children, rotatedOrMatrix));
        }
        return List.copyOf(nodes);
    }

    private static List<Scene> readScenes(JsonParser parser, JsonToken token)
            throws IOException, PreparationObstacleException {
        requireToken(token, JsonToken.START_ARRAY, "scenes array");
        List<Scene> scenes = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            requireToken(parser.currentToken(), JsonToken.START_OBJECT, "scene object");
            List<Integer> nodeIndices = null;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                requireToken(parser.currentToken(), JsonToken.PROPERTY_NAME, "scene property");
                String property = parser.currentName();
                JsonToken value = parser.nextToken();
                if ("nodes".equals(property)) {
                    nodeIndices = readIndices(parser, value, "scene nodes");
                } else {
                    parser.skipChildren();
                }
            }
            scenes.add(new Scene(nodeIndices == null ? List.of() : nodeIndices));
        }
        return List.copyOf(scenes);
    }

    private static List<Integer> readIndices(JsonParser parser, JsonToken token, String description)
            throws IOException, PreparationObstacleException {
        requireToken(token, JsonToken.START_ARRAY, description + " array");
        List<Integer> indices = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            indices.add(readInteger(parser, parser.currentToken(), description + " index"));
        }
        return List.copyOf(indices);
    }

    private static Set<Integer> validatedIndices(List<Integer> indices, int nodeCount)
            throws PreparationObstacleException {
        Set<Integer> validated = new HashSet<>();
        for (Integer index : indices) {
            if (index == null || index < 0 || index >= nodeCount || !validated.add(index)) {
                throw failure(
                        PreparationObstacleException.Code.INVALID_NODE,
                        "collision GLB node index layout is invalid");
            }
        }
        return validated;
    }

    private static MapVector3 readVector3(JsonParser parser, JsonToken token, String description)
            throws IOException, PreparationObstacleException {
        requireToken(token, JsonToken.START_ARRAY, description + " array");
        double x = readRequiredNumber(parser, description);
        double y = readRequiredNumber(parser, description);
        double z = readRequiredNumber(parser, description);
        requireToken(parser.nextToken(), JsonToken.END_ARRAY, description + " end");
        return new MapVector3(x, y, z);
    }

    private static double readRequiredNumber(JsonParser parser, String description)
            throws IOException, PreparationObstacleException {
        JsonToken component = parser.nextToken();
        if (component == JsonToken.END_ARRAY) {
            throw failure(
                    PreparationObstacleException.Code.MALFORMED_JSON,
                    description + " must contain exactly three values");
        }
        return readNumber(parser, component, description + " component");
    }

    private static int readInteger(JsonParser parser, JsonToken token, String description)
            throws IOException, PreparationObstacleException {
        requireToken(token, JsonToken.VALUE_NUMBER_INT, description + " integer");
        return parser.getIntValue();
    }

    private static double readNumber(JsonParser parser, JsonToken token, String description)
            throws IOException, PreparationObstacleException {
        if (token != JsonToken.VALUE_NUMBER_INT && token != JsonToken.VALUE_NUMBER_FLOAT) {
            throw failure(
                    PreparationObstacleException.Code.MALFORMED_JSON,
                    description + " must be numeric");
        }
        double value = parser.getDoubleValue();
        if (!Double.isFinite(value)) {
            throw failure(
                    PreparationObstacleException.Code.INVALID_NODE,
                    description + " must be finite");
        }
        return value;
    }

    private static void requireToken(JsonToken actual, JsonToken expected, String description)
            throws PreparationObstacleException {
        if (actual != expected) {
            throw failure(
                    PreparationObstacleException.Code.MALFORMED_JSON,
                    description + " has an unexpected token");
        }
    }

    private static boolean isObstacleName(String name) {
        return name != null
                && (name.endsWith(WALL_SUFFIX)
                        || name.endsWith(WALL_X_SUFFIX)
                        || name.endsWith(WALL_Z_SUFFIX));
    }

    private static Set<Integer> verifiedBoxMeshes(Glb2Document document)
            throws PreparationObstacleException {
        try {
            return Glb2CanonicalBoxMeshVerifier.verifiedMeshes(document);
        } catch (Glb2CanonicalBoxMeshVerifier.VerificationException exception) {
            PreparationObstacleException.Code code =
                    exception.malformedJson()
                            ? PreparationObstacleException.Code.MALFORMED_JSON
                            : PreparationObstacleException.Code.MISSING_LAYOUT;
            throw new PreparationObstacleException(
                    code, "collision GLB box mesh bytes could not be verified", exception);
        }
    }

    private static PreparationObstacleException failure(
            PreparationObstacleException.Code code, String message) {
        return new PreparationObstacleException(code, message);
    }

    private record Accessor(
            Integer componentType, String type, MapVector3 minimum, MapVector3 maximum) {
        private boolean isUnitCubePosition() {
            return componentType != null
                    && componentType == FLOAT_COMPONENT_TYPE
                    && "VEC3".equals(type)
                    && minimum != null
                    && maximum != null
                    && Double.compare(minimum.x(), -0.5d) == 0
                    && Double.compare(minimum.y(), -0.5d) == 0
                    && Double.compare(minimum.z(), -0.5d) == 0
                    && Double.compare(maximum.x(), 0.5d) == 0
                    && Double.compare(maximum.y(), 0.5d) == 0
                    && Double.compare(maximum.z(), 0.5d) == 0;
        }
    }

    private record Mesh(Integer positionAccessor) {}

    private record Node(
            String name,
            Integer mesh,
            MapVector3 translation,
            MapVector3 scale,
            List<Integer> children,
            boolean rotatedOrMatrix) {
        private Node {
            children = List.copyOf(Objects.requireNonNull(children, "children"));
        }
    }

    private record Scene(List<Integer> nodeIndices) {
        private Scene {
            nodeIndices = List.copyOf(Objects.requireNonNull(nodeIndices, "nodeIndices"));
        }
    }
}
