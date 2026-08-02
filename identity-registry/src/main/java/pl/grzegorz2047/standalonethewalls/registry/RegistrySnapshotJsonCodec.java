package pl.grzegorz2047.standalonethewalls.registry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.erdtman.jcs.JsonCanonicalizer;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;

/** RFC 8785 canonical writer and strict streaming parser for registry snapshot payload v1. */
public final class RegistrySnapshotJsonCodec {
    private static final int MAXIMUM_STRING_LENGTH = 512;
    private static final JsonFactory JSON_FACTORY =
            JsonFactory.builder()
                    .streamReadConstraints(
                            StreamReadConstraints.builder()
                                    .maxNestingDepth(4)
                                    .maxNumberLength(20)
                                    .maxStringLength(MAXIMUM_STRING_LENGTH)
                                    .maxNameLength(32)
                                    .build())
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build();

    private RegistrySnapshotJsonCodec() {
        throw new AssertionError("No instances");
    }

    public static byte[] encode(RegistrySnapshotPayload payload) throws RegistrySnapshotException {
        StringBuilder json = new StringBuilder(256 + payload.entries().size() * 256);
        json.append("{\"entries\":[");
        boolean first = true;
        for (RegistrySnapshotEntry entry : payload.entries()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"handle\":\"")
                    .append(entry.handle().value())
                    .append("\",\"playerId\":\"")
                    .append(entry.playerId().value())
                    .append("\",\"publicKey\":\"")
                    .append(Base64.getEncoder().encodeToString(entry.publicKey()))
                    .append("\",\"status\":\"")
                    .append(entry.status().name())
                    .append("\"}");
        }
        json.append("],\"generatedAt\":\"")
                .append(payload.generatedAt())
                .append("\",\"rootKeyId\":\"")
                .append(payload.rootKeyId().value())
                .append("\",\"schema\":")
                .append(RegistrySnapshotPayload.SCHEMA_VERSION)
                .append(",\"sequence\":")
                .append(payload.sequence())
                .append('}');
        return canonicalize(json.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static RegistrySnapshotPayload decodeCanonical(
            byte[] encoded, RegistrySnapshotPolicy policy) throws RegistrySnapshotException {
        byte[] json = encoded.clone();
        byte[] canonical = canonicalize(json);
        if (!Arrays.equals(json, canonical)) {
            throw new RegistrySnapshotException(
                    RegistrySnapshotException.Code.NON_CANONICAL_JSON,
                    "registry snapshot JSON is not canonical RFC 8785 bytes");
        }
        try (JsonParser parser = JSON_FACTORY.createParser(ObjectReadContext.empty(), json)) {
            requireToken(parser.nextToken(), JsonToken.START_OBJECT, "snapshot object");
            Long sequence = null;
            Instant generatedAt = null;
            RegistryRootId rootKeyId = null;
            Integer schema = null;
            List<RegistrySnapshotEntry> entries = null;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                requireToken(parser.currentToken(), JsonToken.PROPERTY_NAME, "snapshot property");
                String name = parser.currentName();
                JsonToken value = parser.nextToken();
                switch (name) {
                    case "entries" -> {
                        requireUnset(entries, name);
                        entries = readEntries(parser, value, policy.maximumEntries());
                    }
                    case "generatedAt" -> {
                        requireUnset(generatedAt, name);
                        generatedAt = readInstant(parser, value);
                    }
                    case "rootKeyId" -> {
                        requireUnset(rootKeyId, name);
                        rootKeyId = readRootId(parser, value);
                    }
                    case "schema" -> {
                        requireUnset(schema, name);
                        requireToken(value, JsonToken.VALUE_NUMBER_INT, "schema integer");
                        schema = parser.getIntValue();
                    }
                    case "sequence" -> {
                        requireUnset(sequence, name);
                        requireToken(value, JsonToken.VALUE_NUMBER_INT, "sequence integer");
                        sequence = parser.getLongValue();
                    }
                    default ->
                            throw new RegistrySnapshotException(
                                    RegistrySnapshotException.Code.UNKNOWN_FIELD,
                                    "registry snapshot contains an unknown field");
                }
            }
            if (parser.nextToken() != null) {
                throw new RegistrySnapshotException(
                        RegistrySnapshotException.Code.MALFORMED_JSON,
                        "registry snapshot contains trailing JSON data");
            }
            if (schema == null
                    || sequence == null
                    || generatedAt == null
                    || rootKeyId == null
                    || entries == null) {
                throw new RegistrySnapshotException(
                        RegistrySnapshotException.Code.MISSING_FIELD,
                        "registry snapshot is missing a required field");
            }
            if (schema != RegistrySnapshotPayload.SCHEMA_VERSION) {
                throw new RegistrySnapshotException(
                        RegistrySnapshotException.Code.UNSUPPORTED_SCHEMA,
                        "registry snapshot schema is unsupported");
            }
            return new RegistrySnapshotPayload(sequence, generatedAt, rootKeyId, entries);
        } catch (RegistrySnapshotException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new RegistrySnapshotException(
                    RegistrySnapshotException.Code.MALFORMED_JSON,
                    "registry snapshot JSON could not be parsed",
                    exception);
        }
    }

    private static List<RegistrySnapshotEntry> readEntries(
            JsonParser parser, JsonToken token, int maximumEntries)
            throws IOException, RegistrySnapshotException {
        requireToken(token, JsonToken.START_ARRAY, "entries array");
        List<RegistrySnapshotEntry> entries = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (entries.size() >= maximumEntries) {
                throw new RegistrySnapshotException(
                        RegistrySnapshotException.Code.TOO_MANY_ENTRIES,
                        "registry snapshot exceeds the configured entry limit");
            }
            requireToken(parser.currentToken(), JsonToken.START_OBJECT, "entry object");
            entries.add(readEntry(parser));
        }
        return List.copyOf(entries);
    }

    private static RegistrySnapshotEntry readEntry(JsonParser parser)
            throws IOException, RegistrySnapshotException {
        String handle = null;
        String playerId = null;
        byte[] publicKey = null;
        RegistryEntryStatus status = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            requireToken(parser.currentToken(), JsonToken.PROPERTY_NAME, "entry property");
            String name = parser.currentName();
            JsonToken value = parser.nextToken();
            switch (name) {
                case "handle" -> {
                    requireUnset(handle, name);
                    handle = readString(parser, value, "handle");
                }
                case "playerId" -> {
                    requireUnset(playerId, name);
                    playerId = readString(parser, value, "playerId");
                }
                case "publicKey" -> {
                    requireUnset(publicKey, name);
                    publicKey = readPublicKey(parser, value);
                }
                case "status" -> {
                    requireUnset(status, name);
                    status = readStatus(parser, value);
                }
                default ->
                        throw new RegistrySnapshotException(
                                RegistrySnapshotException.Code.UNKNOWN_FIELD,
                                "registry entry contains an unknown field");
            }
        }
        if (handle == null || playerId == null || publicKey == null || status == null) {
            throw new RegistrySnapshotException(
                    RegistrySnapshotException.Code.MISSING_FIELD,
                    "registry entry is missing a required field");
        }
        try {
            return RegistrySnapshotEntry.create(
                    new CanonicalHandle(handle), new PlayerId(playerId), publicKey, status);
        } catch (IllegalArgumentException exception) {
            throw new RegistrySnapshotException(
                    RegistrySnapshotException.Code.INVALID_ENTRY,
                    "registry entry contains an invalid canonical value",
                    exception);
        }
    }

    private static Instant readInstant(JsonParser parser, JsonToken token)
            throws IOException, RegistrySnapshotException {
        String value = readString(parser, token, "generatedAt");
        try {
            Instant parsed = Instant.parse(value);
            if (!parsed.toString().equals(value)) {
                throw new RegistrySnapshotException(
                        RegistrySnapshotException.Code.INVALID_TIMESTAMP,
                        "registry generatedAt is not canonical UTC text");
            }
            return parsed;
        } catch (DateTimeException exception) {
            throw new RegistrySnapshotException(
                    RegistrySnapshotException.Code.INVALID_TIMESTAMP,
                    "registry generatedAt is invalid",
                    exception);
        }
    }

    private static RegistryRootId readRootId(JsonParser parser, JsonToken token)
            throws IOException, RegistrySnapshotException {
        String value = readString(parser, token, "rootKeyId");
        try {
            return new RegistryRootId(value);
        } catch (IllegalArgumentException exception) {
            throw new RegistrySnapshotException(
                    RegistrySnapshotException.Code.INVALID_ENTRY,
                    "registry root key ID is invalid",
                    exception);
        }
    }

    private static byte[] readPublicKey(JsonParser parser, JsonToken token)
            throws IOException, RegistrySnapshotException {
        String value = readString(parser, token, "publicKey");
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            if (!Base64.getEncoder().encodeToString(decoded).equals(value)) {
                throw new RegistrySnapshotException(
                        RegistrySnapshotException.Code.INVALID_PUBLIC_KEY,
                        "registry public key is not canonical Base64");
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new RegistrySnapshotException(
                    RegistrySnapshotException.Code.INVALID_PUBLIC_KEY,
                    "registry public key is not valid Base64",
                    exception);
        }
    }

    private static RegistryEntryStatus readStatus(JsonParser parser, JsonToken token)
            throws IOException, RegistrySnapshotException {
        String value = readString(parser, token, "status");
        try {
            return RegistryEntryStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new RegistrySnapshotException(
                    RegistrySnapshotException.Code.INVALID_ENTRY,
                    "registry entry status is unsupported",
                    exception);
        }
    }

    private static String readString(JsonParser parser, JsonToken token, String field)
            throws IOException, RegistrySnapshotException {
        requireToken(token, JsonToken.VALUE_STRING, field + " string");
        String value = parser.getString();
        if (value == null || value.length() > MAXIMUM_STRING_LENGTH) {
            throw new RegistrySnapshotException(
                    RegistrySnapshotException.Code.INVALID_ENTRY,
                    "registry text field is outside the safe range");
        }
        return value;
    }

    private static void requireToken(JsonToken actual, JsonToken expected, String part)
            throws RegistrySnapshotException {
        if (actual != expected) {
            throw new RegistrySnapshotException(
                    RegistrySnapshotException.Code.MALFORMED_JSON,
                    "registry JSON contains an invalid " + part);
        }
    }

    private static void requireUnset(Object value, String field) throws RegistrySnapshotException {
        if (value != null) {
            throw new RegistrySnapshotException(
                    RegistrySnapshotException.Code.MALFORMED_JSON, "registry JSON repeats a field");
        }
    }

    private static byte[] canonicalize(byte[] json) throws RegistrySnapshotException {
        try {
            return new JsonCanonicalizer(json).getEncodedUTF8();
        } catch (IOException | RuntimeException exception) {
            throw new RegistrySnapshotException(
                    RegistrySnapshotException.Code.MALFORMED_JSON,
                    "registry JSON cannot be canonicalized",
                    exception);
        }
    }
}
