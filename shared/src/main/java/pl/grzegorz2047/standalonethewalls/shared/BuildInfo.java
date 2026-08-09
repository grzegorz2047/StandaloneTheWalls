package pl.grzegorz2047.standalonethewalls.shared;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Shared build metadata that is safe to use in every module. */
public final class BuildInfo {
    public static final String PRODUCT_NAME = "Sunderfront";
    public static final String VERSION = "0.1.0-alpha.5";
    public static final String RELEASE_TAG = "v" + VERSION;

    static final String BUILD_PROVENANCE_RESOURCE = "/META-INF/sunderfront-build.properties";
    private static final String UNAVAILABLE_REPOSITORY_COMMIT = "unavailable";
    private static final Optional<String> REPOSITORY_COMMIT = loadRepositoryCommit();

    private BuildInfo() {
        throw new AssertionError("No instances");
    }

    public static Optional<String> repositoryCommit() {
        return REPOSITORY_COMMIT;
    }

    static Optional<String> parseRepositoryCommit(String content) {
        Objects.requireNonNull(content, "content");
        String[] lines = content.split("\\n", -1);
        if (lines.length != 3 || !lines[2].isEmpty()) {
            throw new IllegalArgumentException(
                    "build provenance must contain exactly two LF lines");
        }
        if (!"schemaVersion=1".equals(lines[0])) {
            throw new IllegalArgumentException("unsupported build provenance schema");
        }
        String prefix = "repositoryCommit=";
        if (!lines[1].startsWith(prefix)) {
            throw new IllegalArgumentException(
                    "build provenance repositoryCommit field is missing");
        }
        String repositoryCommit = lines[1].substring(prefix.length());
        if (UNAVAILABLE_REPOSITORY_COMMIT.equals(repositoryCommit)) {
            return Optional.empty();
        }
        int length = repositoryCommit.length();
        if (length != 40 && length != 64) {
            throw new IllegalArgumentException("repositoryCommit must be a full Git object id");
        }
        for (int index = 0; index < length; index++) {
            char character = repositoryCommit.charAt(index);
            boolean digit = character >= '0' && character <= '9';
            boolean lowerHex = character >= 'a' && character <= 'f';
            boolean upperHex = character >= 'A' && character <= 'F';
            if (!digit && !lowerHex && !upperHex) {
                throw new IllegalArgumentException("repositoryCommit must be hexadecimal");
            }
        }
        return Optional.of(repositoryCommit.toLowerCase(Locale.ROOT));
    }

    private static Optional<String> loadRepositoryCommit() {
        try (InputStream input = BuildInfo.class.getResourceAsStream(BUILD_PROVENANCE_RESOURCE)) {
            if (input == null) {
                return Optional.empty();
            }
            return parseRepositoryCommit(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read build provenance", exception);
        }
    }
}
