package pl.grzegorz2047.standalonethewalls.assets;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Exact-byte codec for asset-pack file and license manifests. */
public final class AssetPackManifestCodec {
    public static final int MAXIMUM_MANIFEST_BYTES = 4 * 1024 * 1024;

    private AssetPackManifestCodec() {
        throw new AssertionError("No instances");
    }

    public static byte[] encode(AssetPackManifest manifest) {
        AssetPackManifest value = Objects.requireNonNull(manifest, "manifest");
        StringBuilder output = new StringBuilder(256 + value.files().size() * 160);
        output.append("{\"files\":[");
        for (int index = 0; index < value.files().size(); index++) {
            if (index > 0) {
                output.append(',');
            }
            AssetPackFile file = value.files().get(index);
            output.append("{\"path\":");
            appendString(output, file.path());
            output.append(",\"sha256\":");
            appendString(output, file.sha256());
            output.append(",\"size\":").append(file.size()).append('}');
        }
        output.append("],\"license\":");
        appendString(output, value.license());
        output.append(",\"packId\":");
        appendString(output, value.packId());
        output.append(",\"version\":");
        appendString(output, value.version());
        output.append('}');
        byte[] encoded = output.toString().getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAXIMUM_MANIFEST_BYTES) {
            throw new IllegalArgumentException(
                    "canonical asset manifest exceeds the maximum byte size");
        }
        return encoded;
    }

    public static AssetPackManifest decode(byte[] encoded) throws AssetPackLockException {
        byte[] bytes = Objects.requireNonNull(encoded, "encoded").clone();
        if (bytes.length == 0 || bytes.length > MAXIMUM_MANIFEST_BYTES) {
            throw new AssetPackLockException(
                    "asset manifest is empty or exceeds the maximum byte size");
        }
        Cursor cursor = new Cursor(decodeUtf8(bytes));
        try {
            cursor.expect("{\"files\":[");
            List<AssetPackFile> files = new ArrayList<>();
            if (!cursor.consume(']')) {
                while (true) {
                    if (files.size() >= AssetPackManifest.MAXIMUM_FILES) {
                        throw new AssetPackLockException("asset manifest contains too many files");
                    }
                    files.add(parseFile(cursor));
                    if (cursor.consume(']')) {
                        break;
                    }
                    cursor.expect(',');
                }
            }
            cursor.expect(",\"license\":");
            String license = cursor.readString();
            cursor.expect(",\"packId\":");
            String packId = cursor.readString();
            cursor.expect(",\"version\":");
            String version = cursor.readString();
            cursor.expect('}');
            cursor.expectEnd();
            AssetPackManifest manifest = new AssetPackManifest(packId, version, license, files);
            if (!Arrays.equals(bytes, encode(manifest))) {
                throw new AssetPackLockException(
                        "asset manifest is not in canonical exact-byte form");
            }
            return manifest;
        } catch (IllegalArgumentException exception) {
            throw new AssetPackLockException("asset manifest contains an invalid value", exception);
        }
    }

    private static AssetPackFile parseFile(Cursor cursor) throws AssetPackLockException {
        cursor.expect("{\"path\":");
        String path = cursor.readString();
        cursor.expect(",\"sha256\":");
        String sha256 = cursor.readString();
        cursor.expect(",\"size\":");
        long size = cursor.readNonNegativeLong();
        cursor.expect('}');
        return new AssetPackFile(path, size, sha256);
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
                        "asset manifest must contain canonical printable ASCII");
            }
            return text;
        } catch (CharacterCodingException exception) {
            throw new AssetPackLockException("asset manifest must contain valid UTF-8", exception);
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
                    "asset manifest string cannot be represented canonically");
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

        private long readNonNegativeLong() throws AssetPackLockException {
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
                        "asset manifest integer is outside the safe range", exception);
            }
        }

        private void expectEnd() throws AssetPackLockException {
            if (offset != text.length()) {
                throw malformed();
            }
        }

        private static AssetPackLockException malformed() {
            return new AssetPackLockException("asset manifest is not canonical schema v1 JSON");
        }
    }
}
