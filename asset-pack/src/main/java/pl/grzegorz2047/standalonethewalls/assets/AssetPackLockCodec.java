package pl.grzegorz2047.standalonethewalls.assets;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Exact-byte codec for the canonical asset lock schema. */
public final class AssetPackLockCodec {
    public static final int MAXIMUM_LOCK_BYTES = 256 * 1024;

    private AssetPackLockCodec() {
        throw new AssertionError("No instances");
    }

    public static byte[] encode(AssetPackLock lock) {
        AssetPackLock value = Objects.requireNonNull(lock, "lock");
        StringBuilder output = new StringBuilder(256 + value.packs().size() * 384);
        output.append("{\"packs\":[");
        for (int index = 0; index < value.packs().size(); index++) {
            if (index > 0) {
                output.append(',');
            }
            AssetPackReference pack = value.packs().get(index);
            output.append("{\"formatVersion\":").append(pack.formatVersion());
            output.append(",\"id\":");
            appendString(output, pack.id());
            output.append(",\"manifestPath\":");
            appendString(output, pack.manifestPath());
            output.append(",\"manifestSha256\":");
            appendString(output, pack.manifestSha256());
            output.append(",\"sha256\":");
            appendString(output, pack.sha256());
            output.append(",\"size\":").append(pack.size());
            output.append(",\"url\":");
            appendString(output, pack.url().toASCIIString());
            output.append(",\"version\":");
            appendString(output, pack.version());
            output.append('}');
        }
        output.append("],\"schema\":").append(value.schema()).append('}');
        byte[] encoded = output.toString().getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAXIMUM_LOCK_BYTES) {
            throw new IllegalArgumentException(
                    "canonical asset lock exceeds the maximum byte size");
        }
        return encoded;
    }

    public static AssetPackLock decode(byte[] encoded) throws AssetPackLockException {
        byte[] bytes = Objects.requireNonNull(encoded, "encoded").clone();
        if (bytes.length == 0 || bytes.length > MAXIMUM_LOCK_BYTES) {
            throw new AssetPackLockException(
                    "asset lock is empty or exceeds the maximum byte size");
        }
        String text = decodeUtf8(bytes);
        Cursor cursor = new Cursor(text);
        try {
            cursor.expect("{\"packs\":[");
            List<AssetPackReference> packs = new ArrayList<>();
            if (!cursor.consume(']')) {
                while (true) {
                    if (packs.size() >= AssetPackLock.MAXIMUM_PACKS) {
                        throw new AssetPackLockException("asset lock contains too many packs");
                    }
                    packs.add(parsePack(cursor));
                    if (cursor.consume(']')) {
                        break;
                    }
                    cursor.expect(',');
                }
            }
            cursor.expect(",\"schema\":");
            int schema = cursor.readPositiveInt();
            cursor.expect('}');
            cursor.expectEnd();
            AssetPackLock lock = new AssetPackLock(schema, packs);
            if (!java.util.Arrays.equals(bytes, encode(lock))) {
                throw new AssetPackLockException("asset lock is not in canonical exact-byte form");
            }
            return lock;
        } catch (IllegalArgumentException exception) {
            throw new AssetPackLockException("asset lock contains an invalid value", exception);
        }
    }

    private static AssetPackReference parsePack(Cursor cursor) throws AssetPackLockException {
        cursor.expect("{\"formatVersion\":");
        int formatVersion = cursor.readPositiveInt();
        cursor.expect(",\"id\":");
        String id = cursor.readString();
        cursor.expect(",\"manifestPath\":");
        String manifestPath = cursor.readString();
        cursor.expect(",\"manifestSha256\":");
        String manifestSha256 = cursor.readString();
        cursor.expect(",\"sha256\":");
        String sha256 = cursor.readString();
        cursor.expect(",\"size\":");
        long size = cursor.readPositiveLong();
        cursor.expect(",\"url\":");
        String url = cursor.readString();
        cursor.expect(",\"version\":");
        String version = cursor.readString();
        cursor.expect('}');
        try {
            return new AssetPackReference(
                    id,
                    version,
                    formatVersion,
                    new URI(url),
                    size,
                    sha256,
                    manifestPath,
                    manifestSha256);
        } catch (URISyntaxException exception) {
            throw new AssetPackLockException("asset lock contains an invalid URL", exception);
        }
    }

    private static String decodeUtf8(byte[] encoded) throws AssetPackLockException {
        try {
            String text =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(encoded))
                            .toString();
            if (text.codePoints().anyMatch(codePoint -> codePoint < 0x20 || codePoint > 0x7e)) {
                throw new AssetPackLockException(
                        "asset lock must contain canonical printable ASCII");
            }
            return text;
        } catch (CharacterCodingException exception) {
            throw new AssetPackLockException("asset lock must contain valid UTF-8", exception);
        }
    }

    private static void appendString(StringBuilder output, String value) {
        if (value.codePoints()
                .anyMatch(
                        codePoint ->
                                codePoint < 0x20
                                        || codePoint > 0x7e
                                        || codePoint == '"'
                                        || codePoint == '\\')) {
            throw new IllegalArgumentException(
                    "asset lock string cannot be represented canonically");
        }
        output.append('"').append(value).append('"');
    }

    private static final class Cursor {
        private final String text;
        private int offset;

        private Cursor(String text) {
            this.text = text;
        }

        private void expect(String expected) throws AssetPackLockException {
            if (!text.startsWith(expected, offset)) {
                throw malformed();
            }
            offset += expected.length();
        }

        private void expect(char expected) throws AssetPackLockException {
            if (offset >= text.length() || text.charAt(offset) != expected) {
                throw malformed();
            }
            offset++;
        }

        private boolean consume(char value) {
            if (offset < text.length() && text.charAt(offset) == value) {
                offset++;
                return true;
            }
            return false;
        }

        private String readString() throws AssetPackLockException {
            expect('"');
            int start = offset;
            while (offset < text.length() && text.charAt(offset) != '"') {
                char current = text.charAt(offset);
                if (current == '\\' || current < 0x20 || current > 0x7e) {
                    throw malformed();
                }
                offset++;
            }
            if (offset == start || offset >= text.length()) {
                throw malformed();
            }
            String value = text.substring(start, offset);
            offset++;
            return value;
        }

        private int readPositiveInt() throws AssetPackLockException {
            long value = readPositiveLong();
            if (value > Integer.MAX_VALUE) {
                throw malformed();
            }
            return (int) value;
        }

        private long readPositiveLong() throws AssetPackLockException {
            int start = offset;
            if (offset >= text.length() || !Character.isDigit(text.charAt(offset))) {
                throw malformed();
            }
            if (text.charAt(offset) == '0') {
                offset++;
                if (offset < text.length() && Character.isDigit(text.charAt(offset))) {
                    throw malformed();
                }
                return 0L;
            }
            while (offset < text.length() && Character.isDigit(text.charAt(offset))) {
                offset++;
            }
            try {
                return Long.parseLong(text.substring(start, offset));
            } catch (NumberFormatException exception) {
                throw new AssetPackLockException(
                        "asset lock integer is outside the safe range", exception);
            }
        }

        private void expectEnd() throws AssetPackLockException {
            if (offset != text.length()) {
                throw malformed();
            }
        }

        private static AssetPackLockException malformed() {
            return new AssetPackLockException("asset lock is not canonical schema v1 JSON");
        }
    }
}
