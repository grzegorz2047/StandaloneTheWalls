package pl.grzegorz2047.standalonethewalls.mapformat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Loads a complete `.twmap` archive only after every declared byte passes verification. */
public final class TwMapBundleLoader {
    public static final String MANIFEST_PATH = "manifest.json";

    private static final int BUFFER_BYTES = 8 * 1024;
    private static final Pattern SAFE_PATH = Pattern.compile("[a-z0-9._/-]{1,200}");

    private TwMapBundleLoader() {
        throw new AssertionError("No instances");
    }

    public static VerifiedMapBundle load(byte[] encoded, TwMapLoadPolicy policy)
            throws TwMapBundleException {
        TwMapLoadPolicy loadPolicy = Objects.requireNonNull(policy, "policy");
        if (encoded == null
                || encoded.length < 4
                || encoded.length > loadPolicy.maximumArchiveBytes()
                || encoded.length > MapManifestValidator.MAXIMUM_ARCHIVE_BYTES) {
            throw failure(
                    TwMapBundleException.Code.INVALID_ARCHIVE_SIZE,
                    ".twmap archive size is outside the configured limit");
        }
        byte[] archive = encoded.clone();
        if (!hasZipSignature(archive)) {
            throw failure(
                    TwMapBundleException.Code.MALFORMED_ARCHIVE,
                    ".twmap bytes do not start with a ZIP signature");
        }

        byte[] manifestJson = locateManifest(archive, loadPolicy);
        MapManifest manifest = parseManifest(manifestJson, archive.length, loadPolicy);
        Map<String, byte[]> members = readAndVerifyMembers(archive, manifestJson, manifest, loadPolicy);
        PreparationGameplay gameplay = parseGameplay(members.get("gameplay.json"));
        return new VerifiedMapBundle(
                manifest,
                gameplay,
                new Sha256Digest(toLowerHex(sha256(archive))),
                manifestJson,
                members);
    }

    private static byte[] locateManifest(byte[] archive, TwMapLoadPolicy policy)
            throws TwMapBundleException {
        Set<String> names = new HashSet<>();
        byte[] manifestJson = null;
        int entries = 0;
        try (ZipInputStream zip =
                new ZipInputStream(
                        new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String path = requireSafeFileEntry(entry);
                if (!names.add(path)) {
                    throw failure(
                            TwMapBundleException.Code.DUPLICATE_ENTRY,
                            ".twmap archive contains a duplicate entry");
                }
                entries++;
                if (entries > policy.maximumFiles() + 1
                        || entries > MapManifestValidator.MAXIMUM_FILES + 1) {
                    throw failure(
                            TwMapBundleException.Code.TOO_MANY_ENTRIES,
                            ".twmap archive contains too many entries");
                }
                if (MANIFEST_PATH.equals(path)) {
                    manifestJson =
                            readEntry(
                                    zip,
                                    entry,
                                    MapManifestJson.MAXIMUM_BYTES,
                                    "map manifest");
                }
                zip.closeEntry();
            }
        } catch (TwMapBundleException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new TwMapBundleException(
                    TwMapBundleException.Code.MALFORMED_ARCHIVE,
                    ".twmap ZIP structure could not be read",
                    exception);
        }
        if (manifestJson == null) {
            throw failure(
                    TwMapBundleException.Code.MISSING_MANIFEST,
                    ".twmap archive does not contain manifest.json");
        }
        return manifestJson;
    }

    private static MapManifest parseManifest(
            byte[] manifestJson, int archiveBytes, TwMapLoadPolicy policy)
            throws TwMapBundleException {
        MapManifestDraft draft;
        try {
            draft = MapManifestJson.decode(manifestJson);
        } catch (MapManifestJsonException exception) {
            throw new TwMapBundleException(
                    TwMapBundleException.Code.INVALID_MANIFEST_JSON,
                    ".twmap manifest JSON is invalid",
                    exception);
        }
        MapManifestValidation validation = MapManifestValidator.validate(draft);
        if (!validation.isValid()) {
            throw new TwMapBundleException(
                    TwMapBundleException.Code.INVALID_MANIFEST,
                    ".twmap manifest failed semantic validation",
                    validation.issues());
        }
        MapManifest manifest = validation.manifest().orElseThrow();
        MapLimits limits = manifest.limits();
        if (archiveBytes > limits.archiveBytes()) {
            throw failure(
                    TwMapBundleException.Code.INVALID_ARCHIVE_SIZE,
                    ".twmap archive exceeds its declared archive budget");
        }
        if (limits.archiveBytes() > policy.maximumArchiveBytes()
                || limits.uncompressedBytes() > policy.maximumUncompressedBytes()
                || limits.fileCount() > policy.maximumFiles()) {
            throw failure(
                    TwMapBundleException.Code.INVALID_MANIFEST,
                    ".twmap declared budgets exceed the local load policy");
        }
        return manifest;
    }

    private static Map<String, byte[]> readAndVerifyMembers(
            byte[] archive,
            byte[] expectedManifestJson,
            MapManifest manifest,
            TwMapLoadPolicy policy)
            throws TwMapBundleException {
        Set<String> expected = new HashSet<>(manifest.files().keySet());
        expected.add(MANIFEST_PATH);
        Set<String> seen = new HashSet<>();
        Map<String, byte[]> members = new LinkedHashMap<>();
        long maximumUncompressed =
                Math.min(
                        policy.maximumUncompressedBytes(),
                        manifest.limits().uncompressedBytes());
        long totalUncompressed = 0L;

        try (ZipInputStream zip =
                new ZipInputStream(
                        new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String path = requireSafeFileEntry(entry);
                if (!seen.add(path)) {
                    throw failure(
                            TwMapBundleException.Code.DUPLICATE_ENTRY,
                            ".twmap archive contains a duplicate entry");
                }
                if (!expected.contains(path)) {
                    throw failure(
                            TwMapBundleException.Code.UNDECLARED_ENTRY,
                            ".twmap archive contains an undeclared entry");
                }
                long remaining = maximumUncompressed - totalUncompressed;
                if (remaining < 0L) {
                    throw expansionFailure();
                }
                int entryLimit =
                        (int) Math.min(remaining, TwMapLoadPolicy.MAXIMUM_IN_MEMORY_BYTES);
                if (MANIFEST_PATH.equals(path)) {
                    entryLimit = Math.min(entryLimit, MapManifestJson.MAXIMUM_BYTES);
                }
                byte[] bytes = readEntry(zip, entry, entryLimit, path);
                totalUncompressed += bytes.length;
                enforceExpansionLimits(totalUncompressed, archive.length, policy);
                if (MANIFEST_PATH.equals(path)) {
                    if (!Arrays.equals(expectedManifestJson, bytes)) {
                        throw failure(
                                TwMapBundleException.Code.MALFORMED_ARCHIVE,
                                ".twmap manifest bytes changed between verification passes");
                    }
                } else {
                    verifyDigest(path, bytes, manifest);
                    members.put(path, bytes);
                }
                zip.closeEntry();
            }
        } catch (TwMapBundleException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new TwMapBundleException(
                    TwMapBundleException.Code.MALFORMED_ARCHIVE,
                    ".twmap member bytes could not be read",
                    exception);
        }
        if (!seen.equals(expected)) {
            throw failure(
                    TwMapBundleException.Code.MISSING_ENTRY,
                    ".twmap archive is missing a declared entry");
        }
        return Map.copyOf(members);
    }

    private static PreparationGameplay parseGameplay(byte[] gameplayJson)
            throws TwMapBundleException {
        try {
            return PreparationGameplayJson.decode(gameplayJson);
        } catch (PreparationGameplayException exception) {
            throw new TwMapBundleException(
                    TwMapBundleException.Code.INVALID_GAMEPLAY,
                    ".twmap gameplay metadata is invalid",
                    exception);
        }
    }

    private static String requireSafeFileEntry(ZipEntry entry) throws TwMapBundleException {
        String path = entry.getName();
        if (entry.isDirectory() || !isSafeRelativePath(path)) {
            throw failure(
                    TwMapBundleException.Code.UNSAFE_ENTRY,
                    ".twmap archive contains an unsafe entry path");
        }
        return path;
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

    private static byte[] readEntry(
            ZipInputStream zip, ZipEntry entry, int maximumBytes, String description)
            throws IOException, TwMapBundleException {
        if (maximumBytes < 0 || entry.getSize() > maximumBytes) {
            throw failure(
                    TwMapBundleException.Code.ENTRY_SIZE_LIMIT,
                    description + " exceeds its allowed size");
        }
        ByteArrayOutputStream output =
                new ByteArrayOutputStream(Math.min(maximumBytes, BUFFER_BYTES));
        byte[] buffer = new byte[BUFFER_BYTES];
        int total = 0;
        int read;
        while ((read = zip.read(buffer)) != -1) {
            if (read == 0) {
                continue;
            }
            if (total > maximumBytes - read) {
                throw failure(
                        TwMapBundleException.Code.ENTRY_SIZE_LIMIT,
                        description + " exceeds its allowed size");
            }
            output.write(buffer, 0, read);
            total += read;
        }
        return output.toByteArray();
    }

    private static void enforceExpansionLimits(
            long totalUncompressed, int archiveBytes, TwMapLoadPolicy policy)
            throws TwMapBundleException {
        long ratioLimit = (long) archiveBytes * policy.maximumExpansionRatio();
        if (totalUncompressed > policy.maximumUncompressedBytes()
                || totalUncompressed > ratioLimit) {
            throw expansionFailure();
        }
    }

    private static TwMapBundleException expansionFailure() {
        return failure(
                TwMapBundleException.Code.EXPANSION_LIMIT,
                ".twmap archive exceeds its expanded-byte or compression-ratio limit");
    }

    private static void verifyDigest(String path, byte[] bytes, MapManifest manifest)
            throws TwMapBundleException {
        String actual = toLowerHex(sha256(bytes));
        String expected = manifest.files().get(path).value();
        if (!actual.equals(expected)) {
            throw failure(
                    TwMapBundleException.Code.HASH_MISMATCH,
                    ".twmap member SHA-256 does not match the manifest");
        }
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 is required by the Java platform", exception);
        }
    }

    private static String toLowerHex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            value.append(Character.forDigit((current >>> 4) & 0x0F, 16));
            value.append(Character.forDigit(current & 0x0F, 16));
        }
        return value.toString();
    }

    private static boolean hasZipSignature(byte[] bytes) {
        return bytes[0] == 'P'
                && bytes[1] == 'K'
                && ((bytes[2] == 3 && bytes[3] == 4)
                        || (bytes[2] == 5 && bytes[3] == 6)
                        || (bytes[2] == 7 && bytes[3] == 8));
    }

    private static TwMapBundleException failure(
            TwMapBundleException.Code code, String message) {
        return new TwMapBundleException(code, message);
    }
}
