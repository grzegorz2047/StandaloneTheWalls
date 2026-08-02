package pl.grzegorz2047.standalonethewalls.registry;

import java.util.Arrays;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** One resolved global handle state with a canonical Ed25519 player key. */
public final class RegistrySnapshotEntry {
    private final CanonicalHandle handle;
    private final PlayerId playerId;
    private final byte[] publicKey;
    private final RegistryEntryStatus status;

    private RegistrySnapshotEntry(
            CanonicalHandle handle,
            PlayerId playerId,
            byte[] publicKey,
            RegistryEntryStatus status) {
        this.handle = handle;
        this.playerId = playerId;
        this.publicKey = publicKey;
        this.status = status;
    }

    public static RegistrySnapshotEntry create(
            CanonicalHandle handle,
            PlayerId playerId,
            byte[] subjectPublicKeyInfo,
            RegistryEntryStatus status)
            throws RegistrySnapshotException {
        CanonicalHandle canonicalHandle = Objects.requireNonNull(handle, "handle");
        PlayerId declaredPlayerId = Objects.requireNonNull(playerId, "playerId");
        RegistryEntryStatus entryStatus = Objects.requireNonNull(status, "status");
        byte[] canonicalKey = RegistryCrypto.decodeEd25519(subjectPublicKeyInfo).getEncoded();
        PlayerId derived;
        try {
            derived = PlayerId.fromPublicKey(canonicalKey);
        } catch (IdentityException exception) {
            throw new RegistrySnapshotException(
                    RegistrySnapshotException.Code.INVALID_PUBLIC_KEY,
                    "registry player key could not be decoded",
                    exception);
        }
        if (!declaredPlayerId.equals(derived)) {
            throw new RegistrySnapshotException(
                    RegistrySnapshotException.Code.PLAYER_ID_MISMATCH,
                    "registry player ID does not match its public key");
        }
        return new RegistrySnapshotEntry(
                canonicalHandle, declaredPlayerId, canonicalKey, entryStatus);
    }

    public CanonicalHandle handle() {
        return handle;
    }

    public PlayerId playerId() {
        return playerId;
    }

    public byte[] publicKey() {
        return publicKey.clone();
    }

    public RegistryEntryStatus status() {
        return status;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RegistrySnapshotEntry entry
                && handle.equals(entry.handle)
                && playerId.equals(entry.playerId)
                && Arrays.equals(publicKey, entry.publicKey)
                && status == entry.status;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(handle, playerId, status);
        return 31 * result + Arrays.hashCode(publicKey);
    }

    @Override
    public String toString() {
        return "RegistrySnapshotEntry[handle="
                + handle
                + ", playerId="
                + playerId
                + ", publicKeyBytes="
                + publicKey.length
                + ", status="
                + status
                + ']';
    }
}
