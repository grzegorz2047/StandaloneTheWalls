package pl.grzegorz2047.standalonethewalls.server.config.identity;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotException;
import pl.grzegorz2047.standalonethewalls.registry.RegistryTrustBundle;

/** Loads 1–64 Ed25519 public X.509 keys from bounded lowercase hexadecimal lines. */
public final class RegistryTrustBundleFileLoader {
    public static final int MAXIMUM_FILE_BYTES = 16 * 1024;
    private static final int MAXIMUM_ROOTS = 64;

    private RegistryTrustBundleFileLoader() {
        throw new AssertionError("No instances");
    }

    public static RegistryTrustBundle load(Path path)
            throws IOException, RegistrySnapshotException {
        List<String> lines =
                StrictUtf8TextFile.readLines(
                        Objects.requireNonNull(path, "path"),
                        MAXIMUM_FILE_BYTES,
                        "registry trust roots file");
        List<byte[]> encodedRoots = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (index == lines.size() - 1 && line.isEmpty()) {
                continue;
            }
            int lineNumber = index + 1;
            if (line.isEmpty() || !line.equals(line.strip())) {
                throw invalidLine(lineNumber, "must be non-empty and trimmed");
            }
            if (line.codePoints().anyMatch(Character::isISOControl)) {
                throw invalidLine(lineNumber, "cannot contain control characters");
            }
            if ((line.length() & 1) != 0
                    || !line.chars()
                            .allMatch(
                                    value ->
                                            (value >= '0' && value <= '9')
                                                    || (value >= 'a' && value <= 'f'))) {
                throw invalidLine(lineNumber, "must be lowercase hexadecimal");
            }
            if (encodedRoots.size() >= MAXIMUM_ROOTS) {
                throw new IllegalArgumentException(
                        "registry trust roots file exceeds the maximum root count");
            }
            encodedRoots.add(HexFormat.of().parseHex(line));
        }
        if (encodedRoots.isEmpty()) {
            throw new IllegalArgumentException(
                    "registry trust roots file must contain at least one public key");
        }
        return RegistryTrustBundle.of(encodedRoots);
    }

    private static IllegalArgumentException invalidLine(int lineNumber, String reason) {
        return new IllegalArgumentException(
                "registry trust root line " + lineNumber + ' ' + reason);
    }
}
