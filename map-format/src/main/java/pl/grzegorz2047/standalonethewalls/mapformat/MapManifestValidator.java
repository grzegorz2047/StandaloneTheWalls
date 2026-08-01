package pl.grzegorz2047.standalonethewalls.mapformat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Semantic validator shared by the client, server, Map Studio, and future Blender tooling. */
public final class MapManifestValidator {
    public static final int SUPPORTED_SCHEMA_VERSION = 1;
    public static final int MAXIMUM_PLAYERS = 40;
    public static final long MAXIMUM_ARCHIVE_BYTES = 512L * 1024L * 1024L;
    public static final long MAXIMUM_UNCOMPRESSED_BYTES = 1024L * 1024L * 1024L;
    public static final int MAXIMUM_FILES = 10_000;
    public static final int MAXIMUM_SCENE_NODES = 100_000;
    public static final int MAXIMUM_TRIANGLES = 2_000_000;
    public static final int MAXIMUM_TEXTURE_DIMENSION = 4096;

    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9._-]{2,63}");
    private static final Pattern SPDX_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9.+-]{0,63}");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern SAFE_PATH = Pattern.compile("[a-z0-9._/-]{1,200}");
    private static final Pattern SEMVER = Pattern.compile(
            "(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"
                    + "(?:-((?:0|[1-9][0-9]*|[0-9A-Za-z-]*[A-Za-z-][0-9A-Za-z-]*)"
                    + "(?:\\.(?:0|[1-9][0-9]*|[0-9A-Za-z-]*[A-Za-z-][0-9A-Za-z-]*))*))?"
                    + "(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?");
    private static final Set<String> REQUIRED_FILES = Set.of(
            "scene.glb",
            "collision.glb",
            "gameplay.json",
            "thumbnail.webp",
            "licenses.json");

    private MapManifestValidator() {
        throw new AssertionError("No instances");
    }

    public static MapManifestValidation validate(MapManifestDraft draft) {
        Objects.requireNonNull(draft, "draft");
        List<MapValidationIssue> issues = new ArrayList<>();

        validateSchema(draft.schemaVersion(), issues);
        String id = validateIdentifier(draft.id(), "$.id", issues);
        String name = validateText(draft.name(), "$.name", 1, 80, issues);
        String author = validateText(draft.author(), "$.author", 1, 80, issues);
        SemanticVersion version = validateVersion(draft.version(), issues);
        PlayerLimits playerLimits = validatePlayers(draft, issues);
        ProtocolRequirement protocol = validateProtocol(draft, issues);
        String license = validateLicense(draft.license(), issues);
        Map<String, Sha256Digest> files = validateFiles(draft.files(), issues);
        MapLimits limits = validateLimits(draft.limits(), files.size(), issues);

        if (!issues.isEmpty()) {
            return MapManifestValidation.invalid(issues);
        }

        return MapManifestValidation.valid(new MapManifest(
                SUPPORTED_SCHEMA_VERSION,
                id,
                name,
                author,
                version,
                playerLimits.minimumPlayers(),
                playerLimits.maximumPlayers(),
                playerLimits.teamCount(),
                playerLimits.playersPerTeam(),
                protocol,
                license,
                files,
                limits));
    }

    private static void validateSchema(Integer schemaVersion, List<MapValidationIssue> issues) {
        if (schemaVersion == null) {
            issue(issues, "$.schemaVersion", MapValidationIssue.Code.REQUIRED, "field is required");
        } else if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            issue(
                    issues,
                    "$.schemaVersion",
                    MapValidationIssue.Code.UNSUPPORTED,
                    "only schema version 1 is supported");
        }
    }

    private static String validateIdentifier(
            String value, String path, List<MapValidationIssue> issues) {
        if (value == null) {
            issue(issues, path, MapValidationIssue.Code.REQUIRED, "field is required");
            return "";
        }
        if (!ID.matcher(value).matches()) {
            issue(
                    issues,
                    path,
                    MapValidationIssue.Code.FORMAT,
                    "must match [a-z0-9][a-z0-9._-]{2,63}");
        }
        return value;
    }

    private static String validateText(
            String value,
            String path,
            int minimumLength,
            int maximumLength,
            List<MapValidationIssue> issues) {
        if (value == null) {
            issue(issues, path, MapValidationIssue.Code.REQUIRED, "field is required");
            return "";
        }
        if (!value.equals(value.strip())) {
            issue(issues, path, MapValidationIssue.Code.FORMAT, "leading/trailing whitespace is forbidden");
        }
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints < minimumLength || codePoints > maximumLength) {
            issue(
                    issues,
                    path,
                    MapValidationIssue.Code.RANGE,
                    "length must be between " + minimumLength + " and " + maximumLength + " code points");
        }
        if (value.codePoints().anyMatch(MapManifestValidator::isForbiddenTextCodePoint)) {
            issue(issues, path, MapValidationIssue.Code.FORMAT, "control and bidi override characters are forbidden");
        }
        return value;
    }

    private static SemanticVersion validateVersion(
            String value, List<MapValidationIssue> issues) {
        if (value == null) {
            issue(issues, "$.version", MapValidationIssue.Code.REQUIRED, "field is required");
            return new SemanticVersion(0, 0, 0, "", "");
        }
        Matcher matcher = SEMVER.matcher(value);
        if (!matcher.matches()) {
            issue(
                    issues,
                    "$.version",
                    MapValidationIssue.Code.FORMAT,
                    "must be a canonical Semantic Version");
            return new SemanticVersion(0, 0, 0, "", "");
        }
        try {
            return new SemanticVersion(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)),
                    nullToEmpty(matcher.group(4)),
                    nullToEmpty(matcher.group(5)));
        } catch (NumberFormatException exception) {
            issue(
                    issues,
                    "$.version",
                    MapValidationIssue.Code.RANGE,
                    "numeric version components exceed the supported integer range");
            return new SemanticVersion(0, 0, 0, "", "");
        }
    }

    private static PlayerLimits validatePlayers(
            MapManifestDraft draft, List<MapValidationIssue> issues) {
        int minimum = requireInteger(draft.minimumPlayers(), "$.minimumPlayers", 2, MAXIMUM_PLAYERS, issues);
        int maximum = requireInteger(draft.maximumPlayers(), "$.maximumPlayers", 2, MAXIMUM_PLAYERS, issues);
        int teamCount = requireInteger(draft.teamCount(), "$.teamCount", 2, 4, issues);
        int playersPerTeam = requireInteger(draft.playersPerTeam(), "$.playersPerTeam", 1, 20, issues);

        if (draft.teamCount() != null && teamCount != 2 && teamCount != 4) {
            issue(
                    issues,
                    "$.teamCount",
                    MapValidationIssue.Code.UNSUPPORTED,
                    "schema v1 supports exactly 2 or 4 teams");
        }
        if (draft.minimumPlayers() != null
                && draft.maximumPlayers() != null
                && minimum > maximum) {
            issue(
                    issues,
                    "$.minimumPlayers",
                    MapValidationIssue.Code.CONFLICT,
                    "minimumPlayers cannot exceed maximumPlayers");
        }
        if (draft.maximumPlayers() != null
                && draft.teamCount() != null
                && draft.playersPerTeam() != null
                && maximum > teamCount * playersPerTeam) {
            issue(
                    issues,
                    "$.maximumPlayers",
                    MapValidationIssue.Code.CONFLICT,
                    "maximumPlayers exceeds teamCount * playersPerTeam");
        }
        if (draft.minimumPlayers() != null
                && draft.teamCount() != null
                && minimum < teamCount) {
            issue(
                    issues,
                    "$.minimumPlayers",
                    MapValidationIssue.Code.CONFLICT,
                    "minimumPlayers must allow at least one player per team");
        }
        return new PlayerLimits(minimum, maximum, teamCount, playersPerTeam);
    }

    private static ProtocolRequirement validateProtocol(
            MapManifestDraft draft, List<MapValidationIssue> issues) {
        int major = requireInteger(
                draft.requiredProtocolMajor(), "$.requiredProtocol.major", 0, 0xFFFF, issues);
        int minor = requireInteger(
                draft.requiredProtocolMinor(), "$.requiredProtocol.minor", 0, 0xFFFF, issues);
        return new ProtocolRequirement(major, minor);
    }

    private static String validateLicense(String value, List<MapValidationIssue> issues) {
        if (value == null) {
            issue(issues, "$.license", MapValidationIssue.Code.REQUIRED, "field is required");
            return "";
        }
        if (!SPDX_ID.matcher(value).matches()) {
            issue(
                    issues,
                    "$.license",
                    MapValidationIssue.Code.FORMAT,
                    "must be a simple SPDX license identifier");
        }
        return value;
    }

    private static Map<String, Sha256Digest> validateFiles(
            Map<String, String> values, List<MapValidationIssue> issues) {
        Map<String, Sha256Digest> validated = new LinkedHashMap<>();
        if (values == null) {
            issue(issues, "$.files", MapValidationIssue.Code.REQUIRED, "field is required");
            return validated;
        }
        if (values.isEmpty() || values.size() > 128) {
            issue(
                    issues,
                    "$.files",
                    MapValidationIssue.Code.RANGE,
                    "manifest must declare between 1 and 128 files");
        }

        for (Map.Entry<String, String> entry : values.entrySet()) {
            String path = entry.getKey();
            String issuePath = "$.files[" + String.valueOf(path) + ']';
            if (!isSafeRelativePath(path)) {
                issue(
                        issues,
                        issuePath,
                        MapValidationIssue.Code.UNSAFE_PATH,
                        "path must be a portable lowercase relative path without dot segments");
                continue;
            }
            String hash = entry.getValue();
            if (hash == null || !SHA_256.matcher(hash).matches()) {
                issue(
                        issues,
                        issuePath,
                        hash == null ? MapValidationIssue.Code.REQUIRED : MapValidationIssue.Code.FORMAT,
                        "hash must be 64 lowercase hexadecimal characters");
                continue;
            }
            validated.put(path, new Sha256Digest(hash));
        }

        for (String required : REQUIRED_FILES) {
            if (!values.containsKey(required)) {
                issue(
                        issues,
                        "$.files[" + required + ']',
                        MapValidationIssue.Code.REQUIRED,
                        "required package member is missing");
            }
        }
        return validated;
    }

    private static MapLimits validateLimits(
            MapLimitsDraft draft, int declaredFileCount, List<MapValidationIssue> issues) {
        if (draft == null) {
            issue(issues, "$.limits", MapValidationIssue.Code.REQUIRED, "field is required");
            return new MapLimits(1, 1, 1, 1, 1, 1);
        }
        long archive = requireLong(
                draft.archiveBytes(), "$.limits.archiveBytes", 1, MAXIMUM_ARCHIVE_BYTES, issues);
        long uncompressed = requireLong(
                draft.uncompressedBytes(),
                "$.limits.uncompressedBytes",
                1,
                MAXIMUM_UNCOMPRESSED_BYTES,
                issues);
        int fileCount = requireInteger(
                draft.fileCount(), "$.limits.fileCount", 1, MAXIMUM_FILES, issues);
        int sceneNodes = requireInteger(
                draft.sceneNodes(), "$.limits.sceneNodes", 1, MAXIMUM_SCENE_NODES, issues);
        int triangles = requireInteger(
                draft.triangles(), "$.limits.triangles", 1, MAXIMUM_TRIANGLES, issues);
        int textureDimension = requireInteger(
                draft.textureDimension(),
                "$.limits.textureDimension",
                64,
                MAXIMUM_TEXTURE_DIMENSION,
                issues);

        if (draft.archiveBytes() != null
                && draft.uncompressedBytes() != null
                && uncompressed < archive) {
            issue(
                    issues,
                    "$.limits.uncompressedBytes",
                    MapValidationIssue.Code.CONFLICT,
                    "uncompressedBytes cannot be smaller than archiveBytes");
        }
        if (draft.fileCount() != null && fileCount < declaredFileCount) {
            issue(
                    issues,
                    "$.limits.fileCount",
                    MapValidationIssue.Code.CONFLICT,
                    "fileCount cannot be smaller than the number of declared files");
        }
        if (draft.textureDimension() != null && !isPowerOfTwo(textureDimension)) {
            issue(
                    issues,
                    "$.limits.textureDimension",
                    MapValidationIssue.Code.FORMAT,
                    "textureDimension must be a power of two");
        }

        long safeUncompressed = Math.max(uncompressed, archive);
        int safeFileCount = Math.max(fileCount, Math.max(1, declaredFileCount));
        int safeTextureDimension = isPowerOfTwo(textureDimension) ? textureDimension : 64;
        return new MapLimits(
                archive,
                safeUncompressed,
                safeFileCount,
                sceneNodes,
                triangles,
                safeTextureDimension);
    }

    private static int requireInteger(
            Integer value,
            String path,
            int minimum,
            int maximum,
            List<MapValidationIssue> issues) {
        if (value == null) {
            issue(issues, path, MapValidationIssue.Code.REQUIRED, "field is required");
            return minimum;
        }
        if (value < minimum || value > maximum) {
            issue(
                    issues,
                    path,
                    MapValidationIssue.Code.RANGE,
                    "must be between " + minimum + " and " + maximum);
            return Math.clamp(value, minimum, maximum);
        }
        return value;
    }

    private static long requireLong(
            Long value,
            String path,
            long minimum,
            long maximum,
            List<MapValidationIssue> issues) {
        if (value == null) {
            issue(issues, path, MapValidationIssue.Code.REQUIRED, "field is required");
            return minimum;
        }
        if (value < minimum || value > maximum) {
            issue(
                    issues,
                    path,
                    MapValidationIssue.Code.RANGE,
                    "must be between " + minimum + " and " + maximum);
            return Math.clamp(value, minimum, maximum);
        }
        return value;
    }

    private static boolean isSafeRelativePath(String path) {
        if (path == null
                || !SAFE_PATH.matcher(path).matches()
                || path.startsWith("/")
                || path.endsWith("/")
                || path.contains("//")
                || path.contains("\\")
                || path.contains(":")) {
            return false;
        }
        for (String segment : path.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    private static boolean isForbiddenTextCodePoint(int codePoint) {
        return Character.isISOControl(codePoint)
                || codePoint == 0x202A
                || codePoint == 0x202B
                || codePoint == 0x202D
                || codePoint == 0x202E
                || codePoint == 0x202C
                || codePoint == 0x2066
                || codePoint == 0x2067
                || codePoint == 0x2068
                || codePoint == 0x2069;
    }

    private static boolean isPowerOfTwo(int value) {
        return value > 0 && (value & (value - 1)) == 0;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void issue(
            List<MapValidationIssue> issues,
            String path,
            MapValidationIssue.Code code,
            String message) {
        issues.add(new MapValidationIssue(path, code, message));
    }

    private record PlayerLimits(
            int minimumPlayers, int maximumPlayers, int teamCount, int playersPerTeam) {}
}
