package pl.grzegorz2047.standalonethewalls.assets;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable, fully pinned reference to one runtime asset-pack archive. */
public record AssetPackReference(
        String id,
        String version,
        int formatVersion,
        URI url,
        long size,
        String sha256,
        String manifestPath,
        String manifestSha256) {
    public static final long MAXIMUM_ARCHIVE_BYTES = 2L * 1024L * 1024L * 1024L;

    private static final Pattern ID_PATTERN =
            Pattern.compile("[a-z0-9](?:[a-z0-9._-]{0,62}[a-z0-9])?");
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)");
    private static final Pattern DIGEST_PATTERN = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern PATH_SEGMENT_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,96}");

    public AssetPackReference {
        id = requireId(id);
        version = requireVersion(version);
        if (formatVersion < 1 || formatVersion > 1_000) {
            throw new IllegalArgumentException(
                    "asset pack format version is outside the safe range");
        }
        url = requireHttpsUrl(url);
        if (size < 1L || size > MAXIMUM_ARCHIVE_BYTES) {
            throw new IllegalArgumentException("asset pack size is outside the safe range");
        }
        sha256 = requireDigest(sha256, "sha256");
        manifestPath = requireManifestPath(manifestPath);
        manifestSha256 = requireDigest(manifestSha256, "manifestSha256");
    }

    static String requireId(String value) {
        String id = require(value, "id");
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("asset pack id is invalid");
        }
        return id;
    }

    static String requireVersion(String value) {
        String version = require(value, "version");
        if (version.length() > 32 || !VERSION_PATTERN.matcher(version).matches()) {
            throw new IllegalArgumentException(
                    "asset pack version must be canonical MAJOR.MINOR.PATCH");
        }
        for (String component : version.split("\\.")) {
            try {
                Integer.parseUnsignedInt(component);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "asset pack version component is out of range", exception);
            }
        }
        return version;
    }

    private static URI requireHttpsUrl(URI value) {
        URI uri = Objects.requireNonNull(value, "url");
        if (!uri.isAbsolute()
                || !"https".equals(uri.getScheme())
                || uri.getHost() == null
                || uri.getHost().isEmpty()
                || uri.getUserInfo() != null
                || uri.getFragment() != null
                || uri.getRawPath() == null
                || uri.getRawPath().isEmpty()
                || !uri.normalize().equals(uri)
                || !uri.toASCIIString().equals(uri.toString())) {
            throw new IllegalArgumentException(
                    "asset pack URL must be a canonical absolute HTTPS URI");
        }
        String[] segments = uri.getPath().toLowerCase(Locale.ROOT).split("/", -1);
        for (String segment : segments) {
            if (segment.equals("latest")
                    || segment.equals("current")
                    || segment.equals("nightly")
                    || segment.equals("snapshot")) {
                throw new IllegalArgumentException(
                        "asset pack URL contains a mutable path segment");
            }
        }
        return uri;
    }

    static String requireDigest(String value, String field) {
        String digest = require(value, field);
        if (!DIGEST_PATTERN.matcher(digest).matches()) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256 hexadecimal");
        }
        return digest;
    }

    static String requireRelativePath(String value, String field) {
        String path = require(value, field);
        if (path.length() > 512
                || path.startsWith("/")
                || path.endsWith("/")
                || path.indexOf('\\') >= 0
                || path.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(field + " must be a bounded relative POSIX path");
        }
        String[] segments = path.split("/", -1);
        for (String segment : segments) {
            if (segment.equals(".")
                    || segment.equals("..")
                    || !PATH_SEGMENT_PATTERN.matcher(segment).matches()) {
                throw new IllegalArgumentException(field + " contains an invalid path segment");
            }
        }
        return path;
    }

    private static String requireManifestPath(String value) {
        String path = requireRelativePath(value, "manifestPath");
        if (!path.endsWith(".json")) {
            throw new IllegalArgumentException("manifestPath must identify a JSON file");
        }
        return path;
    }

    private static String require(String value, String field) {
        String text = Objects.requireNonNull(value, field);
        if (text.isEmpty() || !text.equals(text.strip())) {
            throw new IllegalArgumentException(field + " must be non-empty and trimmed");
        }
        return text;
    }
}
