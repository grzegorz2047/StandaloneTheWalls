package pl.grzegorz2047.standalonethewalls.server.config.identity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Package-private bounded reader shared by strict identity process configuration files. */
final class StrictUtf8TextFile {
    private StrictUtf8TextFile() {
        throw new AssertionError("No instances");
    }

    static List<String> readLines(Path path, int maximumBytes, String label) throws IOException {
        Path normalized = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        if (maximumBytes < 1) {
            throw new IllegalArgumentException("maximumBytes must be positive");
        }
        if (Files.isSymbolicLink(normalized)
                || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(label + " must be a regular non-symbolic-link file");
        }

        byte[] encoded;
        try (InputStream input = Files.newInputStream(normalized)) {
            encoded = input.readNBytes(maximumBytes + 1);
        }
        if (encoded.length > maximumBytes) {
            throw new IllegalArgumentException(label + " exceeds the maximum byte size");
        }

        String text;
        try {
            text =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(encoded))
                            .toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException(label + " must contain valid UTF-8", exception);
        }

        String[] rawLines = text.split("\n", -1);
        List<String> lines = new ArrayList<>(rawLines.length);
        for (String rawLine : rawLines) {
            if (rawLine.endsWith("\r")) {
                lines.add(rawLine.substring(0, rawLine.length() - 1));
            } else {
                if (rawLine.indexOf('\r') >= 0) {
                    throw new IllegalArgumentException(label + " contains an invalid line ending");
                }
                lines.add(rawLine);
            }
        }
        return List.copyOf(lines);
    }
}
