package pl.grzegorz2047.standalonethewalls.mapformat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;

/** Strict bounded streaming decoder for versioned preparation gameplay metadata. */
public final class PreparationGameplayJson {
    public static final int MAXIMUM_BYTES = 64 * 1024;

    private static final JsonFactory JSON_FACTORY =
            JsonFactory.builder()
                    .streamReadConstraints(
                            StreamReadConstraints.builder()
                                    .maxNestingDepth(5)
                                    .maxNumberLength(32)
                                    .maxStringLength(16)
                                    .maxNameLength(16)
                                    .build())
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build();

    private PreparationGameplayJson() {
        throw new AssertionError("No instances");
    }

    public static PreparationGameplay decode(byte[] encoded)
            throws PreparationGameplayException {
        if (encoded == null || encoded.length == 0 || encoded.length > MAXIMUM_BYTES) {
            throw failure(
                    PreparationGameplayException.Code.INVALID_SIZE,
                    "preparation gameplay JSON has an invalid size");
        }
        byte[] json = encoded.clone();
        try (JsonParser parser = JSON_FACTORY.createParser(ObjectReadContext.empty(), json)) {
            requireToken(parser.nextToken(), JsonToken.START_OBJECT, "gameplay object");
            Integer schema = null;
            List<PreparationRegion> regions = null;
            List<PreparationMapSpawn> spawns = null;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                requireToken(parser.currentToken(), JsonToken.PROPERTY_NAME, "gameplay property");
                String name = parser.currentName();
                JsonToken value = parser.nextToken();
                switch (name) {
                    case "schema" -> {
                        requireUnset(schema, name);
                        requireToken(value, JsonToken.VALUE_NUMBER_INT, "schema integer");
                        schema = parser.getIntValue();
                    }
                    case "regions" -> {
                        requireUnset(regions, name);
                        regions = readRegions(parser, value);
                    }
                    case "spawns" -> {
                        requireUnset(spawns, name);
                        spawns = readSpawns(parser, value);
                    }
                    default ->
                            throw failure(
                                    PreparationGameplayException.Code.UNKNOWN_FIELD,
                                    "preparation gameplay contains an unknown field");
                }
            }
            if (parser.nextToken() != null) {
                throw failure(
                        PreparationGameplayException.Code.MALFORMED_JSON,
                        "preparation gameplay contains trailing JSON data");
            }
            if (schema == null || regions == null || spawns == null) {
                throw failure(
                        PreparationGameplayException.Code.MISSING_FIELD,
                        "preparation gameplay is missing a required field");
            }
            if (schema != PreparationGameplay.SCHEMA_VERSION) {
                throw failure(
                        PreparationGameplayException.Code.UNSUPPORTED_SCHEMA,
                        "preparation gameplay schema is unsupported");
            }
            try {
                return new PreparationGameplay(schema, regions, spawns);
            } catch (IllegalArgumentException exception) {
                throw new PreparationGameplayException(
                        PreparationGameplayException.Code.INVALID_LAYOUT,
                        "preparation gameplay layout is invalid",
                        exception);
            }
        } catch (PreparationGameplayException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new PreparationGameplayException(
                    PreparationGameplayException.Code.MALFORMED_JSON,
                    "preparation gameplay JSON could not be parsed",
                    exception);
        }
    }

    private static List<PreparationRegion> readRegions(JsonParser parser, JsonToken token)
            throws IOException, PreparationGameplayException {
        requireToken(token, JsonToken.START_ARRAY, "regions array");
        List<PreparationRegion> regions = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (regions.size() >= PreparationTeam.values().length) {
                throw failure(
                        PreparationGameplayException.Code.TOO_MANY_ENTRIES,
                        "preparation gameplay contains too many regions");
            }
            requireToken(parser.currentToken(), JsonToken.START_OBJECT, "region object");
            regions.add(readRegion(parser));
        }
        return List.copyOf(regions);
    }

    private static PreparationRegion readRegion(JsonParser parser)
            throws IOException, PreparationGameplayException {
        PreparationTeam team = null;
        MapVector3 minimum = null;
        MapVector3 maximum = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            requireToken(parser.currentToken(), JsonToken.PROPERTY_NAME, "region property");
            String name = parser.currentName();
            JsonToken value = parser.nextToken();
            switch (name) {
                case "team" -> {
                    requireUnset(team, name);
                    team = readTeam(parser, value);
                }
                case "minimum" -> {
                    requireUnset(minimum, name);
                    minimum = readVector(parser, value);
                }
                case "maximum" -> {
                    requireUnset(maximum, name);
                    maximum = readVector(parser, value);
                }
                default ->
                        throw failure(
                                PreparationGameplayException.Code.UNKNOWN_FIELD,
                                "preparation region contains an unknown field");
            }
        }
        if (team == null || minimum == null || maximum == null) {
            throw failure(
                    PreparationGameplayException.Code.MISSING_FIELD,
                    "preparation region is missing a required field");
        }
        try {
            return new PreparationRegion(team, minimum, maximum);
        } catch (IllegalArgumentException exception) {
            throw new PreparationGameplayException(
                    PreparationGameplayException.Code.INVALID_VALUE,
                    "preparation region contains invalid bounds",
                    exception);
        }
    }

    private static List<PreparationMapSpawn> readSpawns(JsonParser parser, JsonToken token)
            throws IOException, PreparationGameplayException {
        requireToken(token, JsonToken.START_ARRAY, "spawns array");
        List<PreparationMapSpawn> spawns = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (spawns.size() >= PreparationGameplay.MAXIMUM_SPAWNS) {
                throw failure(
                        PreparationGameplayException.Code.TOO_MANY_ENTRIES,
                        "preparation gameplay contains too many spawns");
            }
            requireToken(parser.currentToken(), JsonToken.START_OBJECT, "spawn object");
            spawns.add(readSpawn(parser));
        }
        return List.copyOf(spawns);
    }

    private static PreparationMapSpawn readSpawn(JsonParser parser)
            throws IOException, PreparationGameplayException {
        Integer index = null;
        PreparationTeam team = null;
        MapVector3 position = null;
        Double yaw = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            requireToken(parser.currentToken(), JsonToken.PROPERTY_NAME, "spawn property");
            String name = parser.currentName();
            JsonToken value = parser.nextToken();
            switch (name) {
                case "index" -> {
                    requireUnset(index, name);
                    requireToken(value, JsonToken.VALUE_NUMBER_INT, "spawn index integer");
                    index = parser.getIntValue();
                }
                case "team" -> {
                    requireUnset(team, name);
                    team = readTeam(parser, value);
                }
                case "position" -> {
                    requireUnset(position, name);
                    position = readVector(parser, value);
                }
                case "yaw" -> {
                    requireUnset(yaw, name);
                    yaw = readNumber(parser, value, "spawn yaw");
                }
                default ->
                        throw failure(
                                PreparationGameplayException.Code.UNKNOWN_FIELD,
                                "preparation spawn contains an unknown field");
            }
        }
        if (index == null || team == null || position == null || yaw == null) {
            throw failure(
                    PreparationGameplayException.Code.MISSING_FIELD,
                    "preparation spawn is missing a required field");
        }
        try {
            return new PreparationMapSpawn(index, team, position, yaw);
        } catch (IllegalArgumentException exception) {
            throw new PreparationGameplayException(
                    PreparationGameplayException.Code.INVALID_VALUE,
                    "preparation spawn contains an invalid value",
                    exception);
        }
    }

    private static MapVector3 readVector(JsonParser parser, JsonToken token)
            throws IOException, PreparationGameplayException {
        requireToken(token, JsonToken.START_ARRAY, "vector array");
        double[] values = new double[3];
        for (int index = 0; index < values.length; index++) {
            JsonToken component = parser.nextToken();
            if (component == JsonToken.END_ARRAY) {
                throw failure(
                        PreparationGameplayException.Code.INVALID_VALUE,
                        "map vector must contain exactly three components");
            }
            values[index] = readNumber(parser, component, "vector component");
        }
        requireToken(parser.nextToken(), JsonToken.END_ARRAY, "vector end");
        try {
            return new MapVector3(values[0], values[1], values[2]);
        } catch (IllegalArgumentException exception) {
            throw new PreparationGameplayException(
                    PreparationGameplayException.Code.INVALID_VALUE,
                    "map vector contains an invalid coordinate",
                    exception);
        }
    }

    private static PreparationTeam readTeam(JsonParser parser, JsonToken token)
            throws IOException, PreparationGameplayException {
        requireToken(token, JsonToken.VALUE_STRING, "team string");
        String value = parser.getString();
        try {
            PreparationTeam parsed = PreparationTeam.valueOf(value);
            if (!parsed.name().equals(value)) {
                throw new IllegalArgumentException("team is not canonical");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new PreparationGameplayException(
                    PreparationGameplayException.Code.INVALID_TEAM,
                    "preparation team is invalid",
                    exception);
        }
    }

    private static double readNumber(JsonParser parser, JsonToken token, String description)
            throws IOException, PreparationGameplayException {
        if (token != JsonToken.VALUE_NUMBER_INT && token != JsonToken.VALUE_NUMBER_FLOAT) {
            throw failure(
                    PreparationGameplayException.Code.INVALID_VALUE,
                    description + " must be a number");
        }
        return parser.getDoubleValue();
    }

    private static void requireToken(JsonToken actual, JsonToken expected, String description)
            throws PreparationGameplayException {
        if (actual != expected) {
            throw failure(
                    PreparationGameplayException.Code.MALFORMED_JSON,
                    description + " has an invalid JSON token");
        }
    }

    private static void requireUnset(Object value, String field)
            throws PreparationGameplayException {
        if (value != null) {
            throw failure(
                    PreparationGameplayException.Code.MALFORMED_JSON,
                    "preparation gameplay repeats field " + field);
        }
    }

    private static PreparationGameplayException failure(
            PreparationGameplayException.Code code, String message) {
        return new PreparationGameplayException(code, message);
    }
}
