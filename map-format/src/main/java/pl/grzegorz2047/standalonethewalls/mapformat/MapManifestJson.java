package pl.grzegorz2047.standalonethewalls.mapformat;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;

/** Strict bounded streaming decoder for untrusted `.twmap` manifest JSON. */
public final class MapManifestJson {
    public static final int MAXIMUM_BYTES = 64 * 1024;
    private static final int MAXIMUM_DECLARED_FILES = 128;

    private static final JsonFactory JSON_FACTORY =
            JsonFactory.builder()
                    .streamReadConstraints(
                            StreamReadConstraints.builder()
                                    .maxNestingDepth(5)
                                    .maxNumberLength(24)
                                    .maxStringLength(512)
                                    .maxNameLength(200)
                                    .build())
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build();

    private MapManifestJson() {
        throw new AssertionError("No instances");
    }

    public static MapManifestDraft decode(byte[] encoded) throws MapManifestJsonException {
        if (encoded == null || encoded.length == 0 || encoded.length > MAXIMUM_BYTES) {
            throw failure(
                    MapManifestJsonException.Code.INVALID_SIZE,
                    "map manifest JSON has an invalid size");
        }
        byte[] json = encoded.clone();
        try (JsonParser parser = JSON_FACTORY.createParser(ObjectReadContext.empty(), json)) {
            requireToken(parser.nextToken(), JsonToken.START_OBJECT, "manifest object");
            Integer schemaVersion = null;
            String id = null;
            String name = null;
            String author = null;
            String version = null;
            Integer minimumPlayers = null;
            Integer maximumPlayers = null;
            Integer teamCount = null;
            Integer playersPerTeam = null;
            ProtocolDraft requiredProtocol = null;
            String license = null;
            Map<String, String> files = null;
            MapLimitsDraft limits = null;

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                requireToken(parser.currentToken(), JsonToken.PROPERTY_NAME, "manifest property");
                String property = parser.currentName();
                JsonToken value = parser.nextToken();
                switch (property) {
                    case "schemaVersion" ->
                            schemaVersion = readInteger(parser, value, "schemaVersion");
                    case "id" -> id = readString(parser, value, "id");
                    case "name" -> name = readString(parser, value, "name");
                    case "author" -> author = readString(parser, value, "author");
                    case "version" -> version = readString(parser, value, "version");
                    case "minimumPlayers" ->
                            minimumPlayers = readInteger(parser, value, "minimumPlayers");
                    case "maximumPlayers" ->
                            maximumPlayers = readInteger(parser, value, "maximumPlayers");
                    case "teamCount" -> teamCount = readInteger(parser, value, "teamCount");
                    case "playersPerTeam" ->
                            playersPerTeam = readInteger(parser, value, "playersPerTeam");
                    case "requiredProtocol" -> requiredProtocol = readProtocol(parser, value);
                    case "license" -> license = readString(parser, value, "license");
                    case "files" -> files = readFiles(parser, value);
                    case "limits" -> limits = readLimits(parser, value);
                    default ->
                            throw failure(
                                    MapManifestJsonException.Code.UNKNOWN_FIELD,
                                    "map manifest contains an unknown field");
                }
            }
            if (parser.nextToken() != null) {
                throw failure(
                        MapManifestJsonException.Code.MALFORMED_JSON,
                        "map manifest contains trailing JSON data");
            }
            return new MapManifestDraft(
                    schemaVersion,
                    id,
                    name,
                    author,
                    version,
                    minimumPlayers,
                    maximumPlayers,
                    teamCount,
                    playersPerTeam,
                    requiredProtocol == null ? null : requiredProtocol.major(),
                    requiredProtocol == null ? null : requiredProtocol.minor(),
                    license,
                    files,
                    limits);
        } catch (MapManifestJsonException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new MapManifestJsonException(
                    MapManifestJsonException.Code.MALFORMED_JSON,
                    "map manifest JSON could not be parsed",
                    exception);
        }
    }

    private static ProtocolDraft readProtocol(JsonParser parser, JsonToken token)
            throws IOException, MapManifestJsonException {
        requireToken(token, JsonToken.START_OBJECT, "requiredProtocol object");
        Integer major = null;
        Integer minor = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            requireToken(parser.currentToken(), JsonToken.PROPERTY_NAME, "protocol property");
            String property = parser.currentName();
            JsonToken value = parser.nextToken();
            switch (property) {
                case "major" -> major = readInteger(parser, value, "protocol major");
                case "minor" -> minor = readInteger(parser, value, "protocol minor");
                default ->
                        throw failure(
                                MapManifestJsonException.Code.UNKNOWN_FIELD,
                                "requiredProtocol contains an unknown field");
            }
        }
        return new ProtocolDraft(major, minor);
    }

    private static Map<String, String> readFiles(JsonParser parser, JsonToken token)
            throws IOException, MapManifestJsonException {
        requireToken(token, JsonToken.START_OBJECT, "files object");
        Map<String, String> files = new LinkedHashMap<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            requireToken(parser.currentToken(), JsonToken.PROPERTY_NAME, "file path property");
            if (files.size() >= MAXIMUM_DECLARED_FILES) {
                throw failure(
                        MapManifestJsonException.Code.TOO_MANY_FILES,
                        "map manifest declares too many files");
            }
            String path = parser.currentName();
            String digest = readString(parser, parser.nextToken(), "file digest");
            files.put(path, digest);
        }
        return Map.copyOf(files);
    }

    private static MapLimitsDraft readLimits(JsonParser parser, JsonToken token)
            throws IOException, MapManifestJsonException {
        requireToken(token, JsonToken.START_OBJECT, "limits object");
        Long archiveBytes = null;
        Long uncompressedBytes = null;
        Integer fileCount = null;
        Integer sceneNodes = null;
        Integer triangles = null;
        Integer textureDimension = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            requireToken(parser.currentToken(), JsonToken.PROPERTY_NAME, "limits property");
            String property = parser.currentName();
            JsonToken value = parser.nextToken();
            switch (property) {
                case "archiveBytes" -> archiveBytes = readLong(parser, value, "archiveBytes");
                case "uncompressedBytes" ->
                        uncompressedBytes = readLong(parser, value, "uncompressedBytes");
                case "fileCount" -> fileCount = readInteger(parser, value, "fileCount");
                case "sceneNodes" -> sceneNodes = readInteger(parser, value, "sceneNodes");
                case "triangles" -> triangles = readInteger(parser, value, "triangles");
                case "textureDimension" ->
                        textureDimension = readInteger(parser, value, "textureDimension");
                default ->
                        throw failure(
                                MapManifestJsonException.Code.UNKNOWN_FIELD,
                                "limits contains an unknown field");
            }
        }
        return new MapLimitsDraft(
                archiveBytes,
                uncompressedBytes,
                fileCount,
                sceneNodes,
                triangles,
                textureDimension);
    }

    private static String readString(JsonParser parser, JsonToken token, String description)
            throws IOException, MapManifestJsonException {
        requireToken(token, JsonToken.VALUE_STRING, description + " string");
        return parser.getString();
    }

    private static Integer readInteger(JsonParser parser, JsonToken token, String description)
            throws IOException, MapManifestJsonException {
        requireToken(token, JsonToken.VALUE_NUMBER_INT, description + " integer");
        return parser.getIntValue();
    }

    private static Long readLong(JsonParser parser, JsonToken token, String description)
            throws IOException, MapManifestJsonException {
        requireToken(token, JsonToken.VALUE_NUMBER_INT, description + " integer");
        return parser.getLongValue();
    }

    private static void requireToken(JsonToken actual, JsonToken expected, String description)
            throws MapManifestJsonException {
        if (actual != expected) {
            throw failure(
                    MapManifestJsonException.Code.MALFORMED_JSON,
                    description + " has an invalid JSON token");
        }
    }

    private static MapManifestJsonException failure(
            MapManifestJsonException.Code code, String message) {
        return new MapManifestJsonException(code, message);
    }

    private record ProtocolDraft(Integer major, Integer minor) {}
}
