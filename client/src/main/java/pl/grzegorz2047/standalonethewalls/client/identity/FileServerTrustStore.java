package pl.grzegorz2047.standalonethewalls.client.identity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerReference;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustRecord;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustStore;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustStoreException;

/** Canonical versioned file store for public TOFU server trust records. */
public final class FileServerTrustStore implements ServerTrustStore {
    private static final int MAGIC = 0x53465452;
    private static final int SCHEMA_VERSION = 1;
    private static final int MAXIMUM_RECORDS = 2_048;
    private static final int MAXIMUM_REFERENCE_BYTES = 255;
    private static final int MAXIMUM_SERVER_ID_BYTES = 64;
    private static final int MAXIMUM_REASON_BYTES = 1_024;
    private static final int MAXIMUM_FILE_BYTES = 1_048_576;

    private final Path path;

    public FileServerTrustStore(Path path) {
        this.path = SecureAtomicFile.requireAbsoluteFile(path, "path");
    }

    public Path path() {
        return path;
    }

    @Override
    public Optional<ServerTrustRecord> find(ServerReference reference)
            throws ServerTrustStoreException {
        Objects.requireNonNull(reference, "reference");
        try {
            return Optional.ofNullable(readRecords().get(reference.value()));
        } catch (IOException | RuntimeException exception) {
            throw failure("could not read the server trust store", exception);
        }
    }

    @Override
    public boolean saveIfAbsent(ServerTrustRecord record) throws ServerTrustStoreException {
        Objects.requireNonNull(record, "record");
        try {
            return SecureAtomicFile.withExclusiveLock(
                    path,
                    () -> {
                        TreeMap<String, ServerTrustRecord> records = readRecords();
                        if (records.containsKey(record.reference().value())) {
                            return false;
                        }
                        if (records.size() >= MAXIMUM_RECORDS) {
                            throw new IOException("server trust store record capacity is exhausted");
                        }
                        records.put(record.reference().value(), record);
                        SecureAtomicFile.replaceAtomically(path, encode(records));
                        return true;
                    });
        } catch (IOException | RuntimeException exception) {
            throw failure("could not update the server trust store", exception);
        }
    }

    @Override
    public boolean replace(ServerTrustRecord expected, ServerTrustRecord replacement)
            throws ServerTrustStoreException {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(replacement, "replacement");
        if (!expected.reference().equals(replacement.reference())) {
            throw new IllegalArgumentException("replacement must use the same server reference");
        }
        try {
            return SecureAtomicFile.withExclusiveLock(
                    path,
                    () -> {
                        TreeMap<String, ServerTrustRecord> records = readRecords();
                        ServerTrustRecord current = records.get(expected.reference().value());
                        if (!expected.equals(current)) {
                            return false;
                        }
                        records.put(replacement.reference().value(), replacement);
                        SecureAtomicFile.replaceAtomically(path, encode(records));
                        return true;
                    });
        } catch (IOException | RuntimeException exception) {
            throw failure("could not replace a server trust record", exception);
        }
    }

    private TreeMap<String, ServerTrustRecord> readRecords() throws IOException {
        Optional<byte[]> content =
                SecureAtomicFile.readIfPresent(path, MAXIMUM_FILE_BYTES);
        if (content.isEmpty()) {
            return new TreeMap<>();
        }
        return decode(content.orElseThrow());
    }

    private static byte[] encode(Map<String, ServerTrustRecord> records) throws IOException {
        if (records.size() > MAXIMUM_RECORDS) {
            throw new IOException("server trust store record count exceeds the accepted bound");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC);
            output.writeInt(SCHEMA_VERSION);
            output.writeInt(records.size());
            String previous = null;
            for (Map.Entry<String, ServerTrustRecord> entry : records.entrySet()) {
                ServerTrustRecord record = entry.getValue();
                if (!entry.getKey().equals(record.reference().value())) {
                    throw new IOException("server trust store key does not match its record");
                }
                if (previous != null && previous.compareTo(entry.getKey()) >= 0) {
                    throw new IOException("server trust records are not strictly sorted");
                }
                writeUtf8(output, record.reference().value(), MAXIMUM_REFERENCE_BYTES);
                writeUtf8(output, record.serverId().value(), MAXIMUM_SERVER_ID_BYTES);
                output.writeByte(sourceCode(record.source()));
                writeUtf8(output, record.reason(), MAXIMUM_REASON_BYTES);
                previous = entry.getKey();
            }
        }
        byte[] result = bytes.toByteArray();
        if (result.length > MAXIMUM_FILE_BYTES) {
            throw new IOException("server trust store exceeds the accepted byte bound");
        }
        return result;
    }

    private static TreeMap<String, ServerTrustRecord> decode(byte[] encoded) throws IOException {
        try (DataInputStream input =
                new DataInputStream(new ByteArrayInputStream(Objects.requireNonNull(encoded)))) {
            if (input.readInt() != MAGIC) {
                throw new StoreFormatException("server trust store magic is invalid");
            }
            if (input.readInt() != SCHEMA_VERSION) {
                throw new StoreFormatException("server trust store schema is unsupported");
            }
            int count = input.readInt();
            if (count < 0 || count > MAXIMUM_RECORDS) {
                throw new StoreFormatException(
                        "server trust store record count is outside the accepted bound");
            }

            TreeMap<String, ServerTrustRecord> records = new TreeMap<>();
            String previous = null;
            for (int index = 0; index < count; index++) {
                String referenceValue = readUtf8(input, MAXIMUM_REFERENCE_BYTES);
                String serverIdValue = readUtf8(input, MAXIMUM_SERVER_ID_BYTES);
                ServerTrustRecord.Source source = decodeSource(input.readUnsignedByte());
                String reason = readUtf8(input, MAXIMUM_REASON_BYTES);
                ServerTrustRecord record;
                try {
                    record =
                            new ServerTrustRecord(
                                    new ServerReference(referenceValue),
                                    new ServerId(serverIdValue),
                                    source,
                                    reason);
                } catch (IllegalArgumentException exception) {
                    throw new StoreFormatException(
                            "server trust store contains an invalid record", exception);
                }
                if (!record.reason().equals(reason)) {
                    throw new StoreFormatException(
                            "server trust record reason is not canonical");
                }
                if (previous != null && previous.compareTo(referenceValue) >= 0) {
                    throw new StoreFormatException(
                            "server trust records are not strictly sorted");
                }
                if (records.put(referenceValue, record) != null) {
                    throw new StoreFormatException(
                            "server trust store contains a duplicate reference");
                }
                previous = referenceValue;
            }
            if (input.read() != -1) {
                throw new StoreFormatException("server trust store contains trailing bytes");
            }
            return records;
        } catch (EOFException exception) {
            throw new StoreFormatException("server trust store is truncated", exception);
        }
    }

    private static void writeUtf8(DataOutputStream output, String value, int maximumBytes)
            throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length < 1 || encoded.length > maximumBytes) {
            throw new IOException("server trust field length is outside the accepted bound");
        }
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static String readUtf8(DataInputStream input, int maximumBytes) throws IOException {
        int length = input.readInt();
        if (length < 1 || length > maximumBytes) {
            throw new StoreFormatException(
                    "server trust field length is outside the accepted bound");
        }
        byte[] encoded = input.readNBytes(length);
        if (encoded.length != length) {
            throw new StoreFormatException("server trust store is truncated");
        }
        try {
            String decoded =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(encoded))
                            .toString();
            if (!Arrays.equals(encoded, decoded.getBytes(StandardCharsets.UTF_8))) {
                throw new StoreFormatException("server trust field is not canonical UTF-8");
            }
            return decoded;
        } catch (CharacterCodingException exception) {
            throw new StoreFormatException("server trust field is not valid UTF-8", exception);
        }
    }

    private static int sourceCode(ServerTrustRecord.Source source) {
        return switch (source) {
            case TOFU -> 1;
            case EXPECTED_PIN -> 2;
            case EXPLICIT_REPLACEMENT -> 3;
        };
    }

    private static ServerTrustRecord.Source decodeSource(int code) throws StoreFormatException {
        return switch (code) {
            case 1 -> ServerTrustRecord.Source.TOFU;
            case 2 -> ServerTrustRecord.Source.EXPECTED_PIN;
            case 3 -> ServerTrustRecord.Source.EXPLICIT_REPLACEMENT;
            default -> throw new StoreFormatException("server trust record source is unknown");
        };
    }

    private static ServerTrustStoreException failure(String message, Throwable cause) {
        return new ServerTrustStoreException(message, cause);
    }

    private static final class StoreFormatException extends IOException {
        private static final long serialVersionUID = 1L;

        private StoreFormatException(String message) {
            super(message);
        }

        private StoreFormatException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
