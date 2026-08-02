package pl.grzegorz2047.standalonethewalls.protocol.lobby;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Exact big-endian wire codec for the first bounded minimal-lobby protocol. */
public final class LobbyProtocolCodec {
    private static final int SCHEMA_VERSION = 1;
    private static final int PLAYER_ID_BYTES = 56;
    private static final int MINIMUM_HANDLE_BYTES = 3;
    private static final int MAXIMUM_HANDLE_BYTES = 24;
    private static final int JOINED_FIXED_BYTES = 1 + Long.BYTES + PLAYER_ID_BYTES + 1;
    private static final int SNAPSHOT_FIXED_BYTES = 1 + Long.BYTES + 1;

    private LobbyProtocolCodec() {
        throw new AssertionError("No instances");
    }

    public static byte[] encodeJoined(LobbyJoined joined) {
        LobbyJoined message = Objects.requireNonNull(joined, "joined");
        byte[] handle = ascii(message.self().handle().value());
        ByteBuffer payload = ByteBuffer.allocate(JOINED_FIXED_BYTES + handle.length);
        payload.put((byte) SCHEMA_VERSION);
        payload.putLong(message.revision());
        putMember(payload, message.self(), handle);
        return payload.array();
    }

    public static LobbyJoined decodeJoined(byte[] payload) throws LobbyProtocolException {
        ByteBuffer input = requirePayload(payload, JOINED_FIXED_BYTES + MINIMUM_HANDLE_BYTES,
                MessageType.LOBBY_JOINED.maximumPayloadBytes());
        requireSchema(input);
        long revision = requireRevision(input);
        LobbyMember member = readMember(input);
        requireExhausted(input);
        return new LobbyJoined(revision, member);
    }

    public static byte[] encodeSnapshot(LobbySnapshot snapshot) {
        LobbySnapshot message = Objects.requireNonNull(snapshot, "snapshot");
        int size = SNAPSHOT_FIXED_BYTES;
        List<byte[]> handles = new ArrayList<>(message.members().size());
        for (LobbyMember member : message.members()) {
            byte[] handle = ascii(member.handle().value());
            handles.add(handle);
            size = Math.addExact(size, PLAYER_ID_BYTES + 1 + handle.length);
        }
        if (size > MessageType.LOBBY_SNAPSHOT.maximumPayloadBytes()) {
            throw new IllegalArgumentException("lobby snapshot exceeds the protocol payload bound");
        }

        ByteBuffer payload = ByteBuffer.allocate(size);
        payload.put((byte) SCHEMA_VERSION);
        payload.putLong(message.revision());
        payload.put((byte) message.members().size());
        for (int index = 0; index < message.members().size(); index++) {
            putMember(payload, message.members().get(index), handles.get(index));
        }
        return payload.array();
    }

    public static LobbySnapshot decodeSnapshot(byte[] payload) throws LobbyProtocolException {
        ByteBuffer input = requirePayload(payload, SNAPSHOT_FIXED_BYTES,
                MessageType.LOBBY_SNAPSHOT.maximumPayloadBytes());
        requireSchema(input);
        long revision = requireRevision(input);
        int count = Byte.toUnsignedInt(input.get());
        if (count > LobbySnapshot.MAXIMUM_MEMBERS) {
            throw new LobbyProtocolException(
                    LobbyProtocolException.Code.INVALID_MEMBER_COUNT,
                    "lobby snapshot member count exceeds the supported capacity");
        }

        List<LobbyMember> members = new ArrayList<>(count);
        Set<PlayerId> playerIds = new HashSet<>();
        PlayerId previous = null;
        for (int index = 0; index < count; index++) {
            LobbyMember member = readMember(input);
            if (!playerIds.add(member.playerId())) {
                throw new LobbyProtocolException(
                        LobbyProtocolException.Code.DUPLICATE_MEMBER,
                        "lobby snapshot contains a duplicate playerId");
            }
            if (previous != null
                    && previous.value().compareTo(member.playerId().value()) >= 0) {
                throw new LobbyProtocolException(
                        LobbyProtocolException.Code.NON_CANONICAL_ORDER,
                        "lobby snapshot members are not strictly sorted by playerId");
            }
            members.add(member);
            previous = member.playerId();
        }
        requireExhausted(input);
        return new LobbySnapshot(revision, members);
    }

    private static void putMember(ByteBuffer output, LobbyMember member, byte[] handle) {
        byte[] playerId = ascii(member.playerId().value());
        if (playerId.length != PLAYER_ID_BYTES) {
            throw new IllegalArgumentException("playerId has an invalid encoded length");
        }
        if (handle.length < MINIMUM_HANDLE_BYTES || handle.length > MAXIMUM_HANDLE_BYTES) {
            throw new IllegalArgumentException("handle has an invalid encoded length");
        }
        output.put(playerId);
        output.put((byte) handle.length);
        output.put(handle);
    }

    private static LobbyMember readMember(ByteBuffer input) throws LobbyProtocolException {
        if (input.remaining() < PLAYER_ID_BYTES + 1) {
            throw new LobbyProtocolException(
                    LobbyProtocolException.Code.INVALID_SIZE,
                    "lobby member is truncated before its handle");
        }
        byte[] playerIdBytes = new byte[PLAYER_ID_BYTES];
        input.get(playerIdBytes);
        int handleLength = Byte.toUnsignedInt(input.get());
        if (handleLength < MINIMUM_HANDLE_BYTES || handleLength > MAXIMUM_HANDLE_BYTES) {
            throw new LobbyProtocolException(
                    LobbyProtocolException.Code.INVALID_HANDLE,
                    "lobby member handle length is invalid");
        }
        if (input.remaining() < handleLength) {
            throw new LobbyProtocolException(
                    LobbyProtocolException.Code.INVALID_SIZE,
                    "lobby member handle is truncated");
        }
        byte[] handleBytes = new byte[handleLength];
        input.get(handleBytes);

        PlayerId playerId;
        try {
            playerId = new PlayerId(decodeAscii(playerIdBytes));
        } catch (IllegalArgumentException exception) {
            throw new LobbyProtocolException(
                    LobbyProtocolException.Code.INVALID_PLAYER_ID,
                    "lobby member playerId is invalid",
                    exception);
        }
        CanonicalHandle handle;
        try {
            handle = new CanonicalHandle(decodeAscii(handleBytes));
        } catch (IllegalArgumentException exception) {
            throw new LobbyProtocolException(
                    LobbyProtocolException.Code.INVALID_HANDLE,
                    "lobby member handle is invalid",
                    exception);
        }
        return new LobbyMember(playerId, handle);
    }

    private static ByteBuffer requirePayload(byte[] payload, int minimumBytes, int maximumBytes)
            throws LobbyProtocolException {
        Objects.requireNonNull(payload, "payload");
        if (payload.length < minimumBytes || payload.length > maximumBytes) {
            throw new LobbyProtocolException(
                    LobbyProtocolException.Code.INVALID_SIZE,
                    "lobby payload size is outside the accepted bound");
        }
        return ByteBuffer.wrap(payload);
    }

    private static void requireSchema(ByteBuffer input) throws LobbyProtocolException {
        if (Byte.toUnsignedInt(input.get()) != SCHEMA_VERSION) {
            throw new LobbyProtocolException(
                    LobbyProtocolException.Code.UNSUPPORTED_SCHEMA,
                    "lobby payload schema is unsupported");
        }
    }

    private static long requireRevision(ByteBuffer input) throws LobbyProtocolException {
        long revision = input.getLong();
        if (revision < 1L) {
            throw new LobbyProtocolException(
                    LobbyProtocolException.Code.INVALID_REVISION,
                    "lobby revision must be positive");
        }
        return revision;
    }

    private static void requireExhausted(ByteBuffer input) throws LobbyProtocolException {
        if (input.hasRemaining()) {
            throw new LobbyProtocolException(
                    LobbyProtocolException.Code.TRAILING_BYTES,
                    "lobby payload contains trailing bytes");
        }
    }

    private static byte[] ascii(String value) {
        byte[] encoded = value.getBytes(StandardCharsets.US_ASCII);
        if (!value.equals(new String(encoded, StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("lobby text must be canonical ASCII");
        }
        return encoded;
    }

    private static String decodeAscii(byte[] encoded) {
        String decoded = new String(encoded, StandardCharsets.US_ASCII);
        if (!Arrays.equals(encoded, decoded.getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("lobby text is not canonical ASCII");
        }
        return decoded;
    }
}
