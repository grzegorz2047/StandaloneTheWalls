package pl.grzegorz2047.standalonethewalls.client.identity;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

final class SecureAtomicFile {
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS =
            EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> FILE_PERMISSIONS =
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    private static final ConcurrentMap<Path, ReentrantLock> PROCESS_LOCKS =
            new ConcurrentHashMap<>();

    private SecureAtomicFile() {}

    static Path requireAbsoluteFile(Path path, String name) {
        Objects.requireNonNull(path, name);
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.getParent() == null || normalized.getFileName() == null) {
            throw new IllegalArgumentException(name + " must name a file below a directory");
        }
        return normalized;
    }

    static Optional<byte[]> readIfPresent(Path path, int maximumBytes) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        BasicFileAttributes attributes =
                Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || Files.isSymbolicLink(path)) {
            throw new IOException("persistent state path is not a regular file");
        }
        long size = attributes.size();
        if (size < 1L || size > maximumBytes) {
            throw new IOException("persistent state file size is outside the accepted bounds");
        }

        ByteBuffer content = ByteBuffer.allocate(Math.toIntExact(size));
        try (FileChannel channel =
                FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            while (content.hasRemaining()) {
                if (channel.read(content) < 0) {
                    throw new IOException("persistent state file ended before its declared size");
                }
            }
            ByteBuffer extra = ByteBuffer.allocate(1);
            if (channel.read(extra) >= 0) {
                throw new IOException("persistent state file changed while being read");
            }
        }
        return Optional.of(content.array());
    }

    static <T> T withExclusiveLock(Path target, IoOperation<T> operation) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(operation, "operation");
        ensureParent(target);
        Path fileName = Objects.requireNonNull(target.getFileName(), "target file name");
        Path lockPath = target.resolveSibling('.' + fileName.toString() + ".lock");
        ReentrantLock processLock =
                PROCESS_LOCKS.computeIfAbsent(lockPath, ignored -> new ReentrantLock());
        processLock.lock();
        try (FileChannel lockChannel =
                        FileChannel.open(
                                lockPath,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.WRITE,
                                LinkOption.NOFOLLOW_LINKS);
                FileLock fileLock = lockChannel.lock()) {
            if (!fileLock.isValid()) {
                throw new IOException("persistent state lock is not valid");
            }
            restrictFile(lockPath);
            return operation.run();
        } finally {
            processLock.unlock();
        }
    }

    static void replaceAtomically(Path target, byte[] content) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(content, "content");
        ensureParent(target);
        Path fileName = Objects.requireNonNull(target.getFileName(), "target file name");
        Path temporary =
                target.resolveSibling('.' + fileName.toString() + '.' + UUID.randomUUID() + ".tmp");
        try {
            try (FileChannel channel =
                    FileChannel.open(
                            temporary,
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE,
                            LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            restrictFile(temporary);
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException(
                        "persistent state requires an atomic move on this filesystem", exception);
            }
            restrictFile(target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void ensureParent(Path target) throws IOException {
        Path parent = Objects.requireNonNull(target.getParent(), "target parent");
        Files.createDirectories(parent);
        if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("persistent state parent is not a regular directory");
        }
        PosixFileAttributeView view =
                Files.getFileAttributeView(
                        parent, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view != null) {
            Files.setPosixFilePermissions(parent, DIRECTORY_PERMISSIONS);
        }
    }

    private static void restrictFile(Path path) throws IOException {
        PosixFileAttributeView view =
                Files.getFileAttributeView(
                        path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view != null) {
            Files.setPosixFilePermissions(path, FILE_PERMISSIONS);
        }
    }

    @FunctionalInterface
    interface IoOperation<T> {
        T run() throws IOException;
    }
}
