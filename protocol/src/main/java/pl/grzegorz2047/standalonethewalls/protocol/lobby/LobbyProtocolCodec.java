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

/** Exact big-endian wire codec for bounded reliable lobby membership and roster commands. */
public final class LobbyProtocolCodec {
    private static final int JOINED_SCHEMA_VERSION = 1;
    private static final int LEGACY_SNAPSHOT_SCHEMA_VERSION = 1;
    private static final int SNAPSHOT_SCHEMA_VERSION = 2;
    private static final int COMMAND_SCHEMA_VERSION = 1;
    private static final int PLAYER_ID_BYTES = 56;
    private static final int MINIMUM_HANDLE_BYTES = 3;
    private static final int MAXIMUM_HANDLE_BYTES = 24;
    private static final int JOINED_FIXED_BYTES = 1 + Long.BYTES + PLAYER_ID_BYTES + 1;
    private static final int SNAPSHOT_FIXED_BYTES = 1 + Long.BYTES + 1;
    private static final int COMMAND_BYTES = 1 + Long.BYTES + 1;
    private static final int COMMAND_RESULT_BYTES = 1 + Long.BYTES + Long.BYTES + 1;

    private LobbyProtocolCodec() {
        throw new AssertionError("No instances");
    }

    public static byte[] encodeJoined(LobbyJoined joined) {
        LobbyJoined message = Objects.requireNonNull(joined, "joined");
        byte[] handle = ascii(message.self().handle().value());
        ByteBuffer payload = ByteBuffer.allocate(JOINED_FIXED_BYTES + handle.length);
        payload.put((byte) JOINED_SCHEMA_VERSION);
        payload.putLong(message.revision());
        putIdentity(payload, message.self(), handle);
        return payload.array();
    }

    public static LobbyJoined decodeJoined(byte[] payload) throws LobbyProtocolException {
        ByteBuffer input =
                requirePayload(
                        payload,
                        JOINED_FIXED_BYTES + MINIMUM_HANDLE_BYTES,
                        MessageType.LOBBY_JOINED.maximumPayloadBytes());
        requireSchema(input, JOINED_SCHEMA_VERSION);
        long revision = requireRevision(input);
        LobbyMember member = readIdentity(input);
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
            size = Math.addExact(size, PLAYER_ID_BYTES + 1 + handle.length + 2);
        }
        if (size > MessageType.LOBBY_SNAPSHOT.maximumPayloadBytes()) {
            throw new IllegalArgumentException("lobby snapshot exceeds the protocol payload bound");
        }

        ByteBuffer payload = ByteBuffer.allocate(size);
        payload.put((byte) SNAPSHOT_SCHEMA_VERSION);
        payload.putLong(message.revision());
        payload.put((byte) message.members().size());
        for (int index = 0; index < message.members().size(); index++) {
            LobbyMember member = message.members().get(index);
            putIdentity(payload, member, handles.get(index));
            payload.put((byte) member.team().wireCode());
            payload.put(canonicalBoolean(member.ready()));
        }
        return payload.array();
    }

    public static LobbySnapshot decodeSnapshot(byte[] payload) throws LobbyProtocolException {
        ByteBuffer input =
                requirePayload(
                        payload,
                        SNAPSHOT_FIXED_BYTES,
                        MessageType.LOBBY_SNAPSHOT.maximumPayloadBytes());
        int schema = Byte.toUnsignedInt(input.get());
        if (schema != LEGACY_SNAPSHOT_SCHEMA_VERSION && schema != SNAPSHOT_SCHEMA_VERSION) {
            throw new LobbyProtocolException(
                    LobbyProtocolException.Code.UNSUPPORTED_SCHEMA,
                    "lobby snapshot schema is unsupported");
        }
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
            LobbyMember identity = readIdentity(input);
            LobbyMember member =
                    schema == SNAPSHOT_SCHEMA_VERSION ? readRosterState(input, identity) : identity;
            if (!playerIds.add(member.playerId())) {
                throw new LobbyProtocolException(
                        LobbyProtocolException.Code.DUPLICATE_MEMBER,
                        "lobby snapshot contains a duplicate playerId");
            }
            if (previous != null && previous.value().compareTo(member.playerId().value()) >= 0) {
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

    public static byte[] encodeSelectTeam(LobbySelectTeamCommand command) {
        LobbySelectTeamCommand message = Objects.requireNonNull(command, "command");
        return ByteBuffer.allocate(COMMAND_BYTES)
                .put((byte) COMMAND_SCHEMA_VERSION)
                .putLong(message.requestId())
                .put((byte) message.team().wireCode())
                .array();
    }

    public static LobbySelectTeamCommand decodeSelectTeam(byte[] payload)
            throws LobbyProtocolException {
        ByteBuffer input =
                requirePayload(
                        payload,
                        COMMAND_BYTES,
                        MessageType.LOBBY_SELECT_TEAM.maximumPayloadBytes());
        requireSchema(input, COMMAND_SCHEMA_VERSION);
        long requestId = requireRequestId(input);
        LobbyTeam team = readTeam(input, false);
        requireExhausted(input);
        return new LobbySelectTeamCommand(requestId, team);
    }

    public static byte[] encodeSetReady(LobbySetReadyCommand command) {
        LobbySetReadyCommand message = Objects.requireNonNull(command, "command");
        return ByteBuffer.allocate(COMMAND_BYTES)
                .put((byte) COMMAND_SCHEMA_VERSION)
                .putLong(message.requestId())
                .put(canonicalBoolean(message.ready()))
                .array();
    }

    public static LobbySetReadyCommand decodeSetReady(byte[] payload)
            throws LobbyProtocolException {
        ByteBuffer input =
                requirePayload(
                        payload, COMMAND_BYTES, MessageType.LOBBY_SET_READY.maximumPayloadBytes());
        requireSchema(input, COMMAND_SCHEMA_VERSION);
        long requestId = requireRequestId(input);
        boolean ready = readCanonicalBoolean(input);
        requireExhausted(input);
        return new LobbySetReadyCommand(requestId, ready);
    }

    public static byte[] encodeCommandResult(LobbyCommandResult result) {
        LobbyCommandResult message = Objects.requireNonNull(result, "result");
        return ByteBuffer.allocate(COMMAND_RESULT_BYTES)
                .put((byte) COMMAND_SCHEMA_VERSION)
                .putLong(message.requestId())
                .putLong(message.revision())
                .put((byte) message.outcome().wireCode())
                .array();
    }

    public static LobbyCommandResult decodeCommandResult(byte[] payload)
            throws LobbyProtocolException {
        ByteBuffer input =
                requirePayload(
                        payload,
                        COMMAND_RESULT_BYTES,
                        MessageType.LOBBY_COMMAND_RESULT.maximumPayloadBytes());
        requireSchema(input, COMMAND_SCHEMA_VERSION);
        long requestId = requireRequestId(input);
        long revision = requireRevision(input);
        LobbyCommandOutcome outcome =
                LobbyCommandOutcome.fromWireCode(Byte.toUnsignedInt(input.get()))
                        .orElseThrow(
                                () ->
                                        new LobbyProtocolException(
                                                LobbyProtocolException.Code.INVALID_OUTCOME,
                                                "lobby command outcome is unknown"));
        requireExhausted(input);
        return new LobbyCommandResult(requestId, revision, outcome);
    }

    private static void putIdentity(ByteBuffer output, LobbyMember member, byte[] handle) {
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

    private static LobbyMember readIdentity(ByteBuffer input) throws LobbyProtocolException {
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
                    LobbyProtocolException.Code.INVALID_SIZE, "lobby member handle is truncated");
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

    private static LobbyMember readRosterState(ByteBuffer input, LobbyMember identity)
            throws LobbyProtocolException {
        if (input.remaining() < 2) {
            throw new LobbyProtocolException(
                    LobbyProtocolException.Code.INVALID_SIZE,
                    "lobby member is truncated before team and ready state");
        }
        LobbyTeam team = readTeam(input, true);
        boolean ready = readCanonicalBoolean(input);
        try {
            return new LobbyMember(identity.playerId(), identity.handle(), team, ready);
        } catch (IllegalArgumentException exception) {
            throw new LobbyProtocolException(
                    LobbyProtocolException.Code.INVALID_READY_STATE,
                    "lobby member readiness is inconsistent with its team",
                    exception);
        }
    }

    private static LobbyTeam readTeam(ByteBuffer input, boolean allowUnassigned)
            throws LobbyProtocolException {
        LobbyTeam team =
                LobbyTeam.fromWireCode(Byte.toUnsignedInt(input.get()))
                        .orElseThrow(
                                () ->
                                        new LobbyProtocolException(
                                                LobbyProtocolException.Code.INVALID_TEAM,
                                                "lobby team code is unknown"));
        if (!allowUnassigned && team == LobbyTeam.UNASSIGNED) {
            throw new LobbyProtocolException(
                    LobbyProtocolException.Code.INVALID_TEAM,
                    "select-team command requires a concrete team");
        }
        return team;
    }

    private static boolean readCanonicalBoolean(ByteBuffer input) throws LobbyProtocolException {
        int value = Byte.toUnsignedInt(input.get());
        if (value == 0) {
            return false;
        }
        if (value == 1) {
            return true;
        }
        throw new LobbyProtocolException(
                LobbyProtocolException.Code.INVALID_BOOLEAN, "lobby boolean is not canonical");
    }

    private static byte canonicalBoolean(boolean value) {
        return (byte) (value ? 1 : 0);
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

    private static void requireSchema(ByteBuffer input, int expected)
            throws LobbyProtocolException {
        if (Byte.toUnsignedInt(input.get()) != expected) {
            throw new LobbyProtocolException(
                    LobbyProtocolException.Code.UNSUPPORTED_SCHEMA,
                    "lobby payload schema is unsupported");
        }
    }

    private static long requireRequestId(ByteBuffer input) throws LobbyProtocolException {
        long requestId = input.getLong();
        if (requestId < 1L) {
            throw new LobbyProtocolException(
                    LobbyProtocolException.Code.INVALID_REQUEST_ID,
                    "lobby requestId must be positive");
        }
        return requestId;
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
