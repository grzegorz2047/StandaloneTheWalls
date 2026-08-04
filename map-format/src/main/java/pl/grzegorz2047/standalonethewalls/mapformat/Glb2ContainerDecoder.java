package pl.grzegorz2047.standalonethewalls.mapformat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;

/** Strictly decodes the GLB 2.0 container and bounded self-contained glTF metadata. */
public final class Glb2ContainerDecoder {
    private static final int MAGIC = 0x46546C67;
    private static final int VERSION = 2;
    private static final int JSON_CHUNK = 0x4E4F534A;
    private static final int BINARY_CHUNK = 0x004E4942;
    private static final int HEADER_BYTES = 12;
    private static final int CHUNK_HEADER_BYTES = 8;

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

    private Glb2ContainerDecoder() {
        throw new AssertionError("No instances");
    }

    public static Glb2Document decode(byte[] encoded, MapLimits limits) throws Glb2Exception {
        Objects.requireNonNull(limits, "limits");
        if (encoded == null
                || encoded.length < HEADER_BYTES + CHUNK_HEADER_BYTES + 4
                || encoded.length > limits.uncompressedBytes()
                || encoded.length > Integer.MAX_VALUE - 8) {
            throw failure(Glb2Exception.Code.INVALID_SIZE, "GLB byte length is outside limits");
        }
        byte[] bytes = encoded.clone();
        ByteBuffer input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (input.getInt() != MAGIC || input.getInt() != VERSION || input.getInt() != bytes.length) {
            throw failure(Glb2Exception.Code.INVALID_HEADER, "GLB 2.0 header is invalid");
        }

        byte[] json = readChunk(input, JSON_CHUNK, true);
        byte[] binary = readChunk(input, BINARY_CHUNK, false);
        if (input.hasRemaining()) {
            throw failure(Glb2Exception.Code.INVALID_CHUNK, "GLB contains unsupported chunks");
        }
        Metadata metadata = parseJson(json, limits);
        if (metadata.declaredBufferBytes() > binary.length
                || binary.length - metadata.declaredBufferBytes() > 3) {
            throw failure(
                    Glb2Exception.Code.INVALID_DOCUMENT,
                    "GLB binary chunk does not match the declared buffer length");
        }
        return new Glb2Document(
                json,
                binary,
                metadata.defaultScene(),
                metadata.sceneCount(),
                metadata.nodeCount(),
                metadata.meshCount(),
                metadata.materialCount(),
                metadata.lightCount(),
                metadata.declaredBufferBytes());
    }

    private static byte[] readChunk(ByteBuffer input, int expectedType, boolean required)
            throws Glb2Exception {
        if (!input.hasRemaining()) {
            if (required) {
                throw failure(Glb2Exception.Code.INVALID_CHUNK, "GLB required chunk is missing");
            }
            return new byte[0];
        }
        if (input.remaining() < CHUNK_HEADER_BYTES) {
            throw failure(Glb2Exception.Code.INVALID_CHUNK, "GLB chunk header is truncated");
        }
        int length = input.getInt();
        int type = input.getInt();
        if (length <= 0 || (length & 3) != 0 || length > input.remaining() || type != expectedType) {
            throw failure(Glb2Exception.Code.INVALID_CHUNK, "GLB chunk layout is invalid");
        }
        byte[] chunk = new byte[length];
        input.get(chunk);
        return chunk;
    }

    private static Metadata parseJson(byte[] json, MapLimits limits) throws Glb2Exception {
        try (JsonParser parser = JSON_FACTORY.createParser(ObjectReadContext.empty(), json)) {
            requireToken(parser.nextToken(), JsonToken.START_OBJECT, "glTF root object");
            String assetVersion = null;
            Integer defaultScene = null;
            int sceneCount = -1;
            int nodeCount = -1;
            int meshCount = -1;
            int materialCount = 0;
            int lightCount = 0;
            BufferMetadata buffers = null;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                requireToken(parser.currentToken(), JsonToken.PROPERTY_NAME, "glTF root property");
                String property = parser.currentName();
                JsonToken value = parser.nextToken();
                switch (property) {
                    case "asset" -> assetVersion = readAssetVersion(parser, value);
                    case "scene" -> defaultScene = readInteger(parser, value, "default scene");
                    case "scenes" -> sceneCount = countArray(parser, value, "scenes");
                    case "nodes" -> nodeCount = countArray(parser, value, "nodes");
                    case "meshes" -> meshCount = countArray(parser, value, "meshes");
                    case "materials" -> materialCount = countArray(parser, value, "materials");
                    case "buffers" -> buffers = readBuffers(parser, value);
                    case "images" -> rejectExternalImageUris(parser, value);
                    case "extensions" -> lightCount = readLightCount(parser, value);
                    default -> parser.skipChildren();
                }
            }
            if (parser.nextToken() != null) {
                throw failure(Glb2Exception.Code.INVALID_JSON, "GLB JSON contains trailing data");
            }
            if (!"2.0".equals(assetVersion)
                    || defaultScene == null
                    || sceneCount < 1
                    || defaultScene < 0
                    || defaultScene >= sceneCount
                    || nodeCount < 1
                    || meshCount < 1
                    || buffers == null
                    || buffers.count() != 1
                    || buffers.byteLength() < 1) {
                throw failure(
                        Glb2Exception.Code.INVALID_DOCUMENT,
                        "GLB glTF metadata is incomplete or inconsistent");
            }
            if (nodeCount > limits.sceneNodes() || meshCount > limits.sceneNodes()) {
                throw failure(
                        Glb2Exception.Code.LIMIT_EXCEEDED,
                        "GLB scene or mesh count exceeds the map budget");
            }
            return new Metadata(
                    defaultScene,
                    sceneCount,
                    nodeCount,
                    meshCount,
                    materialCount,
                    lightCount,
                    buffers.byteLength());
        } catch (Glb2Exception exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new Glb2Exception(
                    Glb2Exception.Code.INVALID_JSON,
                    "GLB JSON chunk could not be parsed",
                    exception);
        }
    }

    private static String readAssetVersion(JsonParser parser, JsonToken token)
            throws IOException, Glb2Exception {
        requireToken(token, JsonToken.START_OBJECT, "asset object");
        String version = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            requireToken(parser.currentToken(), JsonToken.PROPERTY_NAME, "asset property");
            String property = parser.currentName();
            JsonToken value = parser.nextToken();
            if ("version".equals(property)) {
                requireToken(value, JsonToken.VALUE_STRING, "asset version");
                version = parser.getString();
            } else {
                parser.skipChildren();
            }
        }
        return version;
    }

    private static BufferMetadata readBuffers(JsonParser parser, JsonToken token)
            throws IOException, Glb2Exception {
        requireToken(token, JsonToken.START_ARRAY, "buffers array");
        int count = 0;
        Integer byteLength = null;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            requireToken(parser.currentToken(), JsonToken.START_OBJECT, "buffer object");
            count++;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                requireToken(parser.currentToken(), JsonToken.PROPERTY_NAME, "buffer property");
                String property = parser.currentName();
                JsonToken value = parser.nextToken();
                if ("uri".equals(property)) {
                    throw failure(
                            Glb2Exception.Code.EXTERNAL_RESOURCE,
                            "GLB buffers cannot reference external URIs");
                }
                if ("byteLength".equals(property)) {
                    byteLength = readInteger(parser, value, "buffer byte length");
                } else {
                    parser.skipChildren();
                }
            }
        }
        return new BufferMetadata(count, byteLength == null ? -1 : byteLength);
    }

    private static void rejectExternalImageUris(JsonParser parser, JsonToken token)
            throws IOException, Glb2Exception {
        requireToken(token, JsonToken.START_ARRAY, "images array");
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            requireToken(parser.currentToken(), JsonToken.START_OBJECT, "image object");
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                requireToken(parser.currentToken(), JsonToken.PROPERTY_NAME, "image property");
                String property = parser.currentName();
                parser.nextToken();
                if ("uri".equals(property)) {
                    throw failure(
                            Glb2Exception.Code.EXTERNAL_RESOURCE,
                            "GLB images cannot reference external URIs");
                }
                parser.skipChildren();
            }
        }
    }

    private static int readLightCount(JsonParser parser, JsonToken token)
            throws IOException, Glb2Exception {
        requireToken(token, JsonToken.START_OBJECT, "extensions object");
        int count = 0;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            requireToken(parser.currentToken(), JsonToken.PROPERTY_NAME, "extension property");
            String property = parser.currentName();
            JsonToken value = parser.nextToken();
            if (!"KHR_lights_punctual".equals(property)) {
                parser.skipChildren();
                continue;
            }
            requireToken(value, JsonToken.START_OBJECT, "punctual lights extension");
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                requireToken(parser.currentToken(), JsonToken.PROPERTY_NAME, "light property");
                String lightProperty = parser.currentName();
                JsonToken lightValue = parser.nextToken();
                if ("lights".equals(lightProperty)) {
                    count = countArray(parser, lightValue, "lights");
                } else {
                    parser.skipChildren();
                }
            }
        }
        return count;
    }

    private static int countArray(JsonParser parser, JsonToken token, String description)
            throws IOException, Glb2Exception {
        requireToken(token, JsonToken.START_ARRAY, description + " array");
        int count = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            count++;
            parser.skipChildren();
        }
        return count;
    }

    private static int readInteger(JsonParser parser, JsonToken token, String description)
            throws IOException, Glb2Exception {
        requireToken(token, JsonToken.VALUE_NUMBER_INT, description + " integer");
        return parser.getIntValue();
    }

    private static void requireToken(JsonToken actual, JsonToken expected, String description)
            throws Glb2Exception {
        if (actual != expected) {
            throw failure(
                    Glb2Exception.Code.INVALID_JSON,
                    description + " has an invalid JSON token");
        }
    }

    private static Glb2Exception failure(Glb2Exception.Code code, String message) {
        return new Glb2Exception(code, message);
    }

    private record BufferMetadata(int count, int byteLength) {}

    private record Metadata(
            int defaultScene,
            int sceneCount,
            int nodeCount,
            int meshCount,
            int materialCount,
            int lightCount,
            int declaredBufferBytes) {}
}
