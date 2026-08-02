package pl.grzegorz2047.standalonethewalls.registry.file;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotArtifact;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotPolicy;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotProvider;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotProviderException;
import pl.grzegorz2047.standalonethewalls.registry.VerifiedRegistrySnapshot;

/**
 * Local single-file snapshot source and atomic cache. The file is never trusted until the core
 * verifier accepts the returned artifact.
 */
public final class RegistrySnapshotBundleFile implements RegistrySnapshotProvider {
    public static final int FORMAT_VERSION = 1;

    private static final byte[] MAGIC = new byte[] {'S', 'F', 'R', 'B'};
    private static final int RESERVED_BYTES = 3;
    private static final int HEADER_BYTES =
            MAGIC.length
                    + 1
                    + RESERVED_BYTES
                    + Integer.BYTES
                    + RegistrySnapshotArtifact.DIGEST_BYTES
                    + RegistrySnapshotArtifact.SIGNATURE_BYTES;

    private final Path path;
    private final Path parent;
    private final int maximumJsonBytes;

    public RegistrySnapshotBundleFile(Path path) {
        this(path, RegistrySnapshotPolicy.ABSOLUTE_MAXIMUM_JSON_BYTES);
    }

    public RegistrySnapshotBundleFile(Path path, int maximumJsonBytes) {
        Path normalizedPath = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        Path parentPath = normalizedPath.getParent();
        if (normalizedPath.getFileName() == null || parentPath == null) {
            throw new IllegalArgumentException("path must identify a bundle file");
        }
        if (maximumJsonBytes < 1
                || maximumJsonBytes > RegistrySnapshotPolicy.ABSOLUTE_MAXIMUM_JSON_BYTES) {
            throw new IllegalArgumentException("maximumJsonBytes is outside the safe range");
        }
        this.path = normalizedPath;
        this.parent = parentPath;
        this.maximumJsonBytes = maximumJsonBytes;
    }

    @Override
    public RegistrySnapshotArtifact load() throws RegistrySnapshotProviderException {
        try {
            return readBundle();
        } catch (IOException
                | IllegalArgumentException
                | SecurityException
                | UnsupportedOperationException exception) {
            throw new RegistrySnapshotProviderException(
                    "registry snapshot bundle is unavailable or invalid", exception);
        }
    }

    public void storeVerified(
            RegistrySnapshotArtifact artifact, VerifiedRegistrySnapshot verifiedSnapshot)
            throws RegistrySnapshotProviderException {
        RegistrySnapshotArtifact candidate = Objects.requireNonNull(artifact, "artifact");
        VerifiedRegistrySnapshot verified =
                Objects.requireNonNull(verifiedSnapshot, "verifiedSnapshot");
        if (!verified.matchesArtifact(candidate)) {
            throw new IllegalArgumentException(
                    "registry snapshot artifact does not match the verified snapshot");
        }
        byte[] canonicalJson = candidate.canonicalJson();
        if (canonicalJson.length > maximumJsonBytes) {
            throw new IllegalArgumentException(
                    "registry snapshot JSON exceeds the configured file limit");
        }

        Path temporary = null;
        try {
            Files.createDirectories(parent);
            if (Files.isSymbolicLink(path)) {
                throw new IOException("registry snapshot bundle cannot be a symbolic link");
            }
            temporary = Files.createTempFile(parent, temporaryPrefix(), ".tmp");
            writeBundle(temporary, candidate, canonicalJson);
            try {
                Files.move(
                        temporary,
                        path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException(
                        "registry snapshot cache requires an atomic filesystem move", exception);
            }
        } catch (IOException | SecurityException | UnsupportedOperationException exception) {
            deleteAfterFailure(temporary, exception);
            throw new RegistrySnapshotProviderException(
                    "verified registry snapshot bundle could not be stored atomically", exception);
        }
    }

    public Path path() {
        return path;
    }

    private RegistrySnapshotArtifact readBundle() throws IOException {
        BasicFileAttributes attributes =
                Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes.isSymbolicLink() || !attributes.isRegularFile()) {
            throw new IOException("registry snapshot bundle must be a regular file");
        }
        long size = attributes.size();
        long maximumSize = HEADER_BYTES + (long) maximumJsonBytes;
        if (size < HEADER_BYTES + 1L || size > maximumSize) {
            throw new IOException("registry snapshot bundle size is outside the safe range");
        }

        try (SeekableByteChannel channel =
                        Files.newByteChannel(
                                path, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
                DataInputStream input = new DataInputStream(Channels.newInputStream(channel))) {
            byte[] magic = readExactly(input, MAGIC.length);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new IOException("registry snapshot bundle magic is invalid");
            }
            int version = input.readUnsignedByte();
            if (version != FORMAT_VERSION) {
                throw new IOException("registry snapshot bundle version is unsupported");
            }
            byte[] reserved = readExactly(input, RESERVED_BYTES);
            if (!Arrays.equals(reserved, new byte[RESERVED_BYTES])) {
                throw new IOException("registry snapshot bundle reserved bytes are non-zero");
            }
            int jsonLength = input.readInt();
            if (jsonLength < 1 || jsonLength > maximumJsonBytes) {
                throw new IOException("registry snapshot JSON length is outside the safe range");
            }
            if (size != HEADER_BYTES + (long) jsonLength) {
                throw new IOException("registry snapshot bundle length does not match its header");
            }
            byte[] digest = readExactly(input, RegistrySnapshotArtifact.DIGEST_BYTES);
            byte[] signature = readExactly(input, RegistrySnapshotArtifact.SIGNATURE_BYTES);
            byte[] canonicalJson = readExactly(input, jsonLength);
            if (input.read() != -1) {
                throw new IOException("registry snapshot bundle contains trailing data");
            }
            return new RegistrySnapshotArtifact(canonicalJson, digest, signature);
        } catch (EOFException exception) {
            throw new IOException("registry snapshot bundle is truncated", exception);
        }
    }

    private static void writeBundle(
            Path temporary, RegistrySnapshotArtifact artifact, byte[] canonicalJson)
            throws IOException {
        try (FileChannel channel =
                        FileChannel.open(
                                temporary,
                                StandardOpenOption.WRITE,
                                StandardOpenOption.TRUNCATE_EXISTING,
                                LinkOption.NOFOLLOW_LINKS);
                DataOutputStream output = new DataOutputStream(Channels.newOutputStream(channel))) {
            output.write(MAGIC);
            output.writeByte(FORMAT_VERSION);
            output.write(new byte[RESERVED_BYTES]);
            output.writeInt(canonicalJson.length);
            output.write(artifact.digest());
            output.write(artifact.signature());
            output.write(canonicalJson);
            output.flush();
            channel.force(true);
        }
    }

    private static byte[] readExactly(DataInputStream input, int length) throws IOException {
        byte[] value = input.readNBytes(length);
        if (value.length != length) {
            throw new EOFException("registry snapshot bundle ended before the declared length");
        }
        return value;
    }

    private String temporaryPrefix() {
        String prefix = "." + path.getFileName();
        return prefix.length() >= 3 ? prefix : prefix + "___";
    }

    private static void deleteAfterFailure(Path temporary, Throwable failure) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }
}
