package pl.grzegorz2047.standalonethewalls.assets;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Downloads, verifies, extracts, and atomically activates immutable runtime asset packs. */
public final class AssetPackSynchronizer {
    private static final int BUFFER_BYTES = 64 * 1024;
    private static final int MAXIMUM_POINTER_BYTES = 1024;
    private static final String TREE_DIRECTORY = "tree";
    private static final String COMPLETE_MARKER = ".complete";

    private final Path cacheRoot;
    private final AssetPackProvider provider;
    private final ArchiveLimits limits;

    public AssetPackSynchronizer(Path cacheRoot, AssetPackProvider provider) {
        this(cacheRoot, provider, ArchiveLimits.DEFAULT);
    }

    public AssetPackSynchronizer(
            Path cacheRoot, AssetPackProvider provider, ArchiveLimits limits) {
        Path root = Objects.requireNonNull(cacheRoot, "cacheRoot").toAbsolutePath().normalize();
        if (root.getFileName() == null || root.getParent() == null) {
            throw new IllegalArgumentException("asset cache root must identify a directory");
        }
        this.cacheRoot = root;
        this.provider = Objects.requireNonNull(provider, "provider");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public Path sync(AssetPackReference reference) throws AssetPackSyncException {
        AssetPackReference locked = Objects.requireNonNull(reference, "reference");
        Path workDirectory = cacheRoot.resolve("work");
        Path archive = null;
        Path staging = null;
        boolean stagingMoved = false;
        try {
            prepareDirectory(cacheRoot);
            prepareDirectory(workDirectory);
            archive = Files.createTempFile(workDirectory, locked.id() + '-', ".zip.part");
            staging = Files.createTempDirectory(workDirectory, locked.id() + "-stage-");
            downloadArchive(locked, archive);

            Path tree = staging.resolve(TREE_DIRECTORY);
            Files.createDirectory(tree);
            extractArchive(archive, tree);
            verifyTree(locked, tree);
            writeNewFile(staging.resolve(COMPLETE_MARKER), completeMarker(locked));

            Path target = targetDirectory(locked);
            prepareDirectory(target.getParent());
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                verifyCommittedTarget(locked, target);
            } else {
                try {
                    Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
                    stagingMoved = true;
                } catch (FileAlreadyExistsException exception) {
                    verifyCommittedTarget(locked, target);
                } catch (AtomicMoveNotSupportedException exception) {
                    throw new AssetPackSyncException(
                            AssetPackSyncException.Code.CACHE_IO,
                            "asset cache filesystem does not support atomic directory activation",
                            exception);
                }
            }
            updateActivePointer(locked);
            return target.resolve(TREE_DIRECTORY);
        } catch (AssetPackSyncException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new AssetPackSyncException(
                    AssetPackSyncException.Code.CACHE_IO,
                    "asset cache operation failed",
                    exception);
        } finally {
            deleteIfExists(archive);
            if (!stagingMoved) {
                deleteRecursively(staging);
            }
        }
    }

    public Path resolveOffline(AssetPackReference reference) throws AssetPackSyncException {
        AssetPackReference locked = Objects.requireNonNull(reference, "reference");
        Path pointer = activePointer(locked.id());
        if (!Files.exists(pointer, LinkOption.NOFOLLOW_LINKS)) {
            throw new AssetPackSyncException(
                    AssetPackSyncException.Code.CACHE_MISSING,
                    "locked asset pack is not available in the offline cache");
        }
        byte[] actual;
        try {
            actual = readBoundedRegularFile(pointer, MAXIMUM_POINTER_BYTES, "asset cache pointer");
        } catch (IOException | IllegalArgumentException exception) {
            throw new AssetPackSyncException(
                    AssetPackSyncException.Code.CACHE_CONFLICT,
                    "asset cache pointer is invalid",
                    exception);
        }
        if (!MessageDigest.isEqual(actual, activePointerContent(locked))) {
            throw new AssetPackSyncException(
                    AssetPackSyncException.Code.CACHE_STALE,
                    "offline asset cache does not match the current lock");
        }
        Path target = targetDirectory(locked);
        verifyCommittedTarget(locked, target);
        return target.resolve(TREE_DIRECTORY);
    }

    private void downloadArchive(AssetPackReference reference, Path archive)
            throws AssetPackSyncException {
        MessageDigest digest = sha256();
        long total = 0L;
        try (InputStream input = provider.open(reference);
                OutputStream output =
                        Files.newOutputStream(
                                archive,
                                StandardOpenOption.TRUNCATE_EXISTING,
                                StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[BUFFER_BYTES];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                if (total > reference.size() - read) {
                    throw new AssetPackSyncException(
                            AssetPackSyncException.Code.ARCHIVE_OVERSIZED,
                            "asset provider returned more bytes than the lock permits");
                }
                output.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                total += read;
            }
        } catch (AssetPackSyncException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new AssetPackSyncException(
                    AssetPackSyncException.Code.PROVIDER_FAILED,
                    "asset provider failed before a complete archive was verified",
                    exception);
        }
        if (total != reference.size()) {
            throw new AssetPackSyncException(
                    AssetPackSyncException.Code.ARCHIVE_TRUNCATED,
                    "asset provider returned fewer bytes than the lock requires");
        }
        String actualDigest = HexFormat.of().formatHex(digest.digest());
        if (!actualDigest.equals(reference.sha256())) {
            throw new AssetPackSyncException(
                    AssetPackSyncException.Code.ARCHIVE_HASH_MISMATCH,
                    "asset archive SHA-256 does not match the lock");
        }
    }

    private void extractArchive(Path archive, Path destination) throws AssetPackSyncException {
        CentralDirectory centralDirectory = CentralDirectory.read(archive, limits.maximumEntries());
        Set<String> extractedPaths = new HashSet<>();
        long totalUncompressed = 0L;
        int count = 0;
        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                count++;
                if (count > limits.maximumEntries()) {
                    throw invalidArchive("asset archive contains too many entries");
                }
                CentralEntry metadata = centralDirectory.entries().get(entry.getName());
                if (metadata == null) {
                    throw invalidArchive("asset archive central directory disagrees with ZIP entries");
                }
                if (metadata.symbolicLink() || metadata.specialFile()) {
                    throw invalidArchive("asset archive contains a symbolic link or special file");
                }
                if (metadata.encrypted()) {
                    throw invalidArchive("asset archive contains an encrypted entry");
                }
                if (entry.getMethod() != ZipEntry.STORED && entry.getMethod() != ZipEntry.DEFLATED) {
                    throw invalidArchive("asset archive uses an unsupported compression method");
                }
                if (entry.getMethod() != metadata.method()
                        || entry.getSize() != metadata.uncompressedSize()
                        || entry.getCompressedSize() != metadata.compressedSize()) {
                    throw invalidArchive("asset archive entry metadata is inconsistent");
                }

                boolean directory = entry.isDirectory();
                String rawName = entry.getName();
                if (directory != rawName.endsWith("/")) {
                    throw invalidArchive("asset archive directory encoding is not canonical");
                }
                String pathText = directory ? rawName.substring(0, rawName.length() - 1) : rawName;
                if (pathText.isEmpty()) {
                    throw invalidArchive("asset archive contains an empty path");
                }
                try {
                    AssetPackReference.requireRelativePath(pathText, "archive path");
                } catch (IllegalArgumentException exception) {
                    throw invalidArchive("asset archive contains an unsafe path", exception);
                }
                if (!pathText.equals(pathText.toLowerCase(Locale.ROOT))) {
                    throw invalidArchive("asset archive paths must use lowercase ASCII");
                }
                if (!extractedPaths.add(pathText)) {
                    throw invalidArchive("asset archive contains a duplicate normalized path");
                }

                long uncompressed = metadata.uncompressedSize();
                long compressed = metadata.compressedSize();
                if (uncompressed < 0L
                        || compressed < 0L
                        || uncompressed > limits.maximumEntryBytes()) {
                    throw invalidArchive("asset archive entry exceeds its size limit");
                }
                if (directory && (uncompressed != 0L || compressed != 0L)) {
                    throw invalidArchive("asset archive directory has non-zero content");
                }
                if (!directory && exceedsCompressionRatio(uncompressed, compressed)) {
                    throw invalidArchive("asset archive entry exceeds the compression ratio limit");
                }
                try {
                    totalUncompressed = Math.addExact(totalUncompressed, uncompressed);
                } catch (ArithmeticException exception) {
                    throw invalidArchive("asset archive total size overflowed", exception);
                }
                if (totalUncompressed > limits.maximumTotalBytes()) {
                    throw invalidArchive("asset archive exceeds the total uncompressed size limit");
                }

                Path target = destination.resolve(pathText).normalize();
                if (!target.startsWith(destination)) {
                    throw invalidArchive("asset archive path escapes the extraction root");
                }
                if (directory) {
                    Files.createDirectories(target);
                    continue;
                }
                Path parent = target.getParent();
                if (parent == null) {
                    throw invalidArchive("asset archive file has no extraction parent");
                }
                Files.createDirectories(parent);
                try (InputStream input = zip.getInputStream(entry);
                        OutputStream output =
                                Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                    copyExactEntry(input, output, uncompressed);
                }
            }
        } catch (AssetPackSyncException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw invalidArchive("asset archive cannot be extracted safely", exception);
        }
        if (count != centralDirectory.entries().size()) {
            throw invalidArchive("asset archive entry count is inconsistent");
        }
    }

    private boolean exceedsCompressionRatio(long uncompressed, long compressed) {
        if (uncompressed == 0L) {
            return false;
        }
        if (compressed <= 0L) {
            return true;
        }
        if (compressed > Long.MAX_VALUE / limits.maximumCompressionRatio()) {
            return false;
        }
        return uncompressed > compressed * limits.maximumCompressionRatio();
    }

    private static void copyExactEntry(InputStream input, OutputStream output, long expected)
            throws IOException, AssetPackSyncException {
        byte[] buffer = new byte[BUFFER_BYTES];
        long total = 0L;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) {
                continue;
            }
            if (total > expected - read) {
                throw invalidArchive("asset archive entry expands beyond its declared size");
            }
            output.write(buffer, 0, read);
            total += read;
        }
        if (total != expected) {
            throw invalidArchive("asset archive entry is truncated");
        }
    }

    private void verifyCommittedTarget(AssetPackReference reference, Path target)
            throws AssetPackSyncException {
        if (Files.isSymbolicLink(target)
                || !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new AssetPackSyncException(
                    AssetPackSyncException.Code.CACHE_CONFLICT,
                    "asset cache target is missing or is not a regular directory");
        }
        Path marker = target.resolve(COMPLETE_MARKER);
        byte[] actual;
        try {
            actual = readBoundedRegularFile(marker, MAXIMUM_POINTER_BYTES, "asset completion marker");
        } catch (IOException | IllegalArgumentException exception) {
            throw new AssetPackSyncException(
                    AssetPackSyncException.Code.CACHE_CONFLICT,
                    "asset cache completion marker is invalid",
                    exception);
        }
        if (!MessageDigest.isEqual(actual, completeMarker(reference))) {
            throw new AssetPackSyncException(
                    AssetPackSyncException.Code.CACHE_CONFLICT,
                    "asset cache target conflicts with the immutable lock");
        }
        verifyTree(reference, target.resolve(TREE_DIRECTORY));
    }

    private static void verifyTree(AssetPackReference reference, Path tree)
            throws AssetPackSyncException {
        if (Files.isSymbolicLink(tree) || !Files.isDirectory(tree, LinkOption.NOFOLLOW_LINKS)) {
            throw new AssetPackSyncException(
                    AssetPackSyncException.Code.MANIFEST_INVALID,
                    "asset tree is missing or invalid");
        }
        Path manifestPath = tree.resolve(reference.manifestPath()).normalize();
        if (!manifestPath.startsWith(tree)) {
            throw new AssetPackSyncException(
                    AssetPackSyncException.Code.MANIFEST_INVALID,
                    "asset manifest path escapes the verified tree");
        }

        byte[] manifestBytes;
        try {
            manifestBytes =
                    readBoundedRegularFile(
                            manifestPath,
                            AssetPackManifestCodec.MAXIMUM_MANIFEST_BYTES,
                            "asset manifest");
        } catch (IOException | IllegalArgumentException exception) {
            throw new AssetPackSyncException(
                    AssetPackSyncException.Code.MANIFEST_INVALID,
                    "asset manifest is missing or unreadable",
                    exception);
        }
        if (!sha256Hex(manifestBytes).equals(reference.manifestSha256())) {
            throw new AssetPackSyncException(
                    AssetPackSyncException.Code.MANIFEST_INVALID,
                    "asset manifest SHA-256 does not match the lock");
        }

        AssetPackManifest manifest;
        try {
            manifest = AssetPackManifestCodec.decode(manifestBytes);
        } catch (AssetPackLockException exception) {
            throw new AssetPackSyncException(
                    AssetPackSyncException.Code.MANIFEST_INVALID,
                    "asset manifest is not canonical or valid",
                    exception);
        }
        if (!manifest.packId().equals(reference.id())
                || !manifest.version().equals(reference.version())) {
            throw new AssetPackSyncException(
                    AssetPackSyncException.Code.MANIFEST_INVALID,
                    "asset manifest identity differs from the lock");
        }
        if (manifest.files().stream()
                .anyMatch(file -> file.path().equals(reference.manifestPath()))) {
            throw new AssetPackSyncException(
                    AssetPackSyncException.Code.MANIFEST_INVALID,
                    "asset manifest cannot recursively list itself");
        }

        Map<String, Path> actualFiles = listTreeFiles(tree, reference.manifestPath());
        Set<String> expectedPaths = new HashSet<>();
        for (AssetPackFile file : manifest.files()) {
            expectedPaths.add(file.path());
            Path actual = actualFiles.get(file.path());
            if (actual == null) {
                throw new AssetPackSyncException(
                        AssetPackSyncException.Code.MANIFEST_INVALID,
                        "asset manifest references a missing file");
            }
            try {
                if (Files.size(actual) != file.size()) {
                    throw new AssetPackSyncException(
                            AssetPackSyncException.Code.MANIFEST_INVALID,
                            "asset file size differs from the manifest");
                }
                if (!sha256Hex(actual).equals(file.sha256())) {
                    throw new AssetPackSyncException(
                            AssetPackSyncException.Code.MANIFEST_INVALID,
                            "asset file SHA-256 differs from the manifest");
                }
            } catch (IOException exception) {
                throw new AssetPackSyncException(
                        AssetPackSyncException.Code.MANIFEST_INVALID,
                        "asset file cannot be verified",
                        exception);
            }
        }
        if (!actualFiles.keySet().equals(expectedPaths)) {
            throw new AssetPackSyncException(
                    AssetPackSyncException.Code.MANIFEST_INVALID,
                    "asset tree contains an orphan file not covered by the manifest");
        }
    }

    private static Map<String, Path> listTreeFiles(Path tree, String manifestPath)
            throws AssetPackSyncException {
        Map<String, Path> files = new HashMap<>();
        try {
            Files.walkFileTree(
                    tree,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(
                                Path directory, BasicFileAttributes attributes) throws IOException {
                            if (attributes.isSymbolicLink() || !attributes.isDirectory()) {
                                throw new IOException("asset tree contains an invalid directory");
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(
                                Path file, BasicFileAttributes attributes) throws IOException {
                            if (attributes.isSymbolicLink() || !attributes.isRegularFile()) {
                                throw new IOException("asset tree contains a special file");
                            }
                            String relative =
                                    tree.relativize(file)
                                            .toString()
                                            .replace(file.getFileSystem().getSeparator(), "/");
                            try {
                                AssetPackReference.requireRelativePath(relative, "cached asset path");
                            } catch (IllegalArgumentException exception) {
                                throw new IOException("asset tree contains an unsafe path", exception);
                            }
                            if (!relative.equals(manifestPath)
                                    && files.putIfAbsent(relative, file) != null) {
                                throw new IOException("asset tree contains a duplicate file path");
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException exception) {
            throw new AssetPackSyncException(
                    AssetPackSyncException.Code.MANIFEST_INVALID,
                    "asset tree cannot be enumerated safely",
                    exception);
        }
        return Map.copyOf(files);
    }

    private void updateActivePointer(AssetPackReference reference) throws AssetPackSyncException {
        Path activeDirectory = cacheRoot.resolve("active");
        Path temporary = null;
        try {
            prepareDirectory(activeDirectory);
            temporary = Files.createTempFile(activeDirectory, reference.id() + '-', ".pointer.part");
            Files.write(
                    temporary,
                    activePointerContent(reference),
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            try {
                Files.move(
                        temporary,
                        activePointer(reference.id()),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
                temporary = null;
            } catch (AtomicMoveNotSupportedException exception) {
                throw new AssetPackSyncException(
                        AssetPackSyncException.Code.CACHE_IO,
                        "asset cache filesystem does not support atomic pointer replacement",
                        exception);
            }
        } catch (AssetPackSyncException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new AssetPackSyncException(
                    AssetPackSyncException.Code.CACHE_IO,
                    "asset active pointer could not be replaced",
                    exception);
        } finally {
            deleteIfExists(temporary);
        }
    }

    private Path targetDirectory(AssetPackReference reference) {
        return cacheRoot
                .resolve("packs")
                .resolve(reference.id())
                .resolve(reference.version())
                .resolve(reference.sha256());
    }

    private Path activePointer(String id) {
        return cacheRoot.resolve("active").resolve(id + ".pointer");
    }

    private static byte[] activePointerContent(AssetPackReference reference) {
        return ("schema=1\nid="
                        + reference.id()
                        + "\nversion="
                        + reference.version()
                        + "\narchiveSha256="
                        + reference.sha256()
                        + "\n")
                .getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] completeMarker(AssetPackReference reference) {
        return ("schema=1\nid="
                        + reference.id()
                        + "\nversion="
                        + reference.version()
                        + "\narchiveSha256="
                        + reference.sha256()
                        + "\nmanifestSha256="
                        + reference.manifestSha256()
                        + "\n")
                .getBytes(StandardCharsets.US_ASCII);
    }

    private static void writeNewFile(Path path, byte[] content) throws IOException {
        Files.write(path, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static void prepareDirectory(Path path) throws IOException {
        Files.createDirectories(path);
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("asset cache path is not a regular directory");
        }
    }

    private static byte[] readBoundedRegularFile(Path path, int maximumBytes, String label)
            throws IOException {
        if (Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(label + " must be a regular non-symbolic-link file");
        }
        byte[] bytes;
        try (InputStream input = Files.newInputStream(path)) {
            bytes = input.readNBytes(maximumBytes + 1);
        }
        if (bytes.length == 0 || bytes.length > maximumBytes) {
            throw new IllegalArgumentException(label + " is empty or exceeds its byte limit");
        }
        return bytes;
    }

    private static String sha256Hex(Path path) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[BUFFER_BYTES];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha256Hex(byte[] bytes) {
        return HexFormat.of().formatHex(sha256().digest(bytes));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static AssetPackSyncException invalidArchive(String message) {
        return new AssetPackSyncException(AssetPackSyncException.Code.ARCHIVE_INVALID, message);
    }

    private static AssetPackSyncException invalidArchive(String message, Throwable cause) {
        return new AssetPackSyncException(
                AssetPackSyncException.Code.ARCHIVE_INVALID, message, cause);
    }

    private static void deleteIfExists(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup cannot change the verified active pointer.
        }
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            Files.walkFileTree(
                    root,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(
                                Path file, BasicFileAttributes attributes) throws IOException {
                            Files.deleteIfExists(file);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path directory, IOException failure)
                                throws IOException {
                            if (failure != null) {
                                throw failure;
                            }
                            Files.deleteIfExists(directory);
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException ignored) {
            // Staging cleanup is best-effort and never mutates the active cache pointer.
        }
    }

    public record ArchiveLimits(
            int maximumEntries,
            long maximumEntryBytes,
            long maximumTotalBytes,
            long maximumCompressionRatio) {
        public static final ArchiveLimits DEFAULT =
                new ArchiveLimits(10_000, 512L * 1024L * 1024L, 2L * 1024L * 1024L * 1024L, 200L);

        public ArchiveLimits {
            if (maximumEntries < 1 || maximumEntries > AssetPackManifest.MAXIMUM_FILES + 1) {
                throw new IllegalArgumentException("maximumEntries is outside the safe range");
            }
            if (maximumEntryBytes < 1L
                    || maximumEntryBytes > AssetPackManifest.MAXIMUM_TOTAL_BYTES) {
                throw new IllegalArgumentException("maximumEntryBytes is outside the safe range");
            }
            if (maximumTotalBytes < maximumEntryBytes
                    || maximumTotalBytes > AssetPackManifest.MAXIMUM_TOTAL_BYTES) {
                throw new IllegalArgumentException("maximumTotalBytes is outside the safe range");
            }
            if (maximumCompressionRatio < 1L || maximumCompressionRatio > 10_000L) {
                throw new IllegalArgumentException(
                        "maximumCompressionRatio is outside the safe range");
            }
        }
    }

    private record CentralEntry(
            String name,
            int method,
            long compressedSize,
            long uncompressedSize,
            boolean encrypted,
            boolean symbolicLink,
            boolean specialFile) {}

    private record CentralDirectory(Map<String, CentralEntry> entries) {
        private static final int EOCD_MINIMUM_BYTES = 22;
        private static final int MAXIMUM_COMMENT_BYTES = 65_535;
        private static final long EOCD_SIGNATURE = 0x06054b50L;
        private static final long CENTRAL_SIGNATURE = 0x02014b50L;

        private CentralDirectory {
            entries = Map.copyOf(entries);
        }

        private static CentralDirectory read(Path archive, int maximumEntries)
                throws AssetPackSyncException {
            try (RandomAccessFile file = new RandomAccessFile(archive.toFile(), "r")) {
                long length = file.length();
                if (length < EOCD_MINIMUM_BYTES) {
                    throw invalidArchive("asset archive is too short to contain a ZIP directory");
                }
                int tailLength =
                        Math.toIntExact(
                                Math.min(length, EOCD_MINIMUM_BYTES + MAXIMUM_COMMENT_BYTES));
                byte[] tail = new byte[tailLength];
                file.seek(length - tailLength);
                file.readFully(tail);
                int eocdIndex = findEndOfCentralDirectory(tail);
                if (eocdIndex < 0) {
                    throw invalidArchive("asset archive has no canonical ZIP end record");
                }
                int commentLength = unsignedShort(tail, eocdIndex + 20);
                if (eocdIndex + EOCD_MINIMUM_BYTES + commentLength != tail.length) {
                    throw invalidArchive("asset archive has trailing bytes after the ZIP end record");
                }
                if (unsignedShort(tail, eocdIndex + 4) != 0
                        || unsignedShort(tail, eocdIndex + 6) != 0) {
                    throw invalidArchive("multi-disk asset archives are not supported");
                }
                int entriesOnDisk = unsignedShort(tail, eocdIndex + 8);
                int totalEntries = unsignedShort(tail, eocdIndex + 10);
                long centralSize = unsignedInt(tail, eocdIndex + 12);
                long centralOffset = unsignedInt(tail, eocdIndex + 16);
                if (entriesOnDisk == 0xffff
                        || totalEntries == 0xffff
                        || centralSize == 0xffffffffL
                        || centralOffset == 0xffffffffL) {
                    throw invalidArchive("ZIP64 asset archives are not supported");
                }
                if (entriesOnDisk != totalEntries || totalEntries > maximumEntries) {
                    throw invalidArchive("asset archive entry count is outside the safe range");
                }
                long eocdOffset = length - tailLength + eocdIndex;
                if (centralOffset > eocdOffset
                        || centralSize > eocdOffset - centralOffset
                        || centralOffset + centralSize != eocdOffset) {
                    throw invalidArchive("asset archive central directory bounds are invalid");
                }

                file.seek(centralOffset);
                Map<String, CentralEntry> entries = new LinkedHashMap<>();
                for (int index = 0; index < totalEntries; index++) {
                    byte[] header = new byte[46];
                    file.readFully(header);
                    if (unsignedInt(header, 0) != CENTRAL_SIGNATURE) {
                        throw invalidArchive("asset archive central entry signature is invalid");
                    }
                    int versionMadeBy = unsignedShort(header, 4);
                    int platform = (versionMadeBy >>> 8) & 0xff;
                    int flags = unsignedShort(header, 8);
                    int method = unsignedShort(header, 10);
                    long compressedSize = unsignedInt(header, 20);
                    long uncompressedSize = unsignedInt(header, 24);
                    int nameLength = unsignedShort(header, 28);
                    int extraLength = unsignedShort(header, 30);
                    int entryCommentLength = unsignedShort(header, 32);
                    long externalAttributes = unsignedInt(header, 38);
                    long localOffset = unsignedInt(header, 42);
                    if (compressedSize == 0xffffffffL
                            || uncompressedSize == 0xffffffffL
                            || localOffset == 0xffffffffL) {
                        throw invalidArchive("ZIP64 asset entries are not supported");
                    }
                    if ((flags & 1) != 0) {
                        throw invalidArchive("asset archive contains an encrypted entry");
                    }
                    if ((flags & (1 << 11)) == 0) {
                        throw invalidArchive("asset archive entry names must be UTF-8");
                    }
                    if (nameLength < 1) {
                        throw invalidArchive("asset archive contains an empty entry name");
                    }
                    byte[] nameBytes = new byte[nameLength];
                    file.readFully(nameBytes);
                    String name = decodeUtf8(nameBytes);
                    skipFully(file, extraLength + entryCommentLength);

                    int mode = (int) ((externalAttributes >>> 16) & 0xffffL);
                    int type = mode & 0170000;
                    boolean symbolicLink = platform == 3 && type == 0120000;
                    boolean specialFile =
                            platform == 3 && type != 0 && type != 0100000 && type != 0040000;
                    CentralEntry entry =
                            new CentralEntry(
                                    name,
                                    method,
                                    compressedSize,
                                    uncompressedSize,
                                    (flags & 1) != 0,
                                    symbolicLink,
                                    specialFile);
                    if (entries.putIfAbsent(name, entry) != null) {
                        throw invalidArchive("asset archive contains a duplicate central path");
                    }
                }
                if (file.getFilePointer() != centralOffset + centralSize) {
                    throw invalidArchive("asset archive central directory size is inconsistent");
                }
                return new CentralDirectory(entries);
            } catch (AssetPackSyncException exception) {
                throw exception;
            } catch (EOFException exception) {
                throw invalidArchive("asset archive central directory is truncated", exception);
            } catch (IOException | RuntimeException exception) {
                throw invalidArchive("asset archive central directory cannot be read", exception);
            }
        }

        private static int findEndOfCentralDirectory(byte[] tail) {
            for (int index = tail.length - EOCD_MINIMUM_BYTES; index >= 0; index--) {
                if (unsignedInt(tail, index) == EOCD_SIGNATURE) {
                    return index;
                }
            }
            return -1;
        }

        private static void skipFully(RandomAccessFile file, int bytes) throws IOException {
            long target = file.getFilePointer() + bytes;
            if (target < file.getFilePointer() || target > file.length()) {
                throw new EOFException("ZIP central entry metadata is truncated");
            }
            file.seek(target);
        }

        private static String decodeUtf8(byte[] bytes) throws AssetPackSyncException {
            try {
                return StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString();
            } catch (CharacterCodingException exception) {
                throw invalidArchive("asset archive entry name is not valid UTF-8", exception);
            }
        }

        private static int unsignedShort(byte[] bytes, int offset) {
            return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
        }

        private static long unsignedInt(byte[] bytes, int offset) {
            return Integer.toUnsignedLong(
                    (bytes[offset] & 0xff)
                            | ((bytes[offset + 1] & 0xff) << 8)
                            | ((bytes[offset + 2] & 0xff) << 16)
                            | ((bytes[offset + 3] & 0xff) << 24));
        }
    }
}
