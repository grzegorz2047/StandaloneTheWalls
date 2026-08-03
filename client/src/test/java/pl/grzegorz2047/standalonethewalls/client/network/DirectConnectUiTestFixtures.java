package pl.grzegorz2047.standalonethewalls.client.network;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolVersion;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableChannel;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableSendResult;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionStatus;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerFingerprint;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCommandResult;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCountdownCancellationReason;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhase;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhaseSnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.transport.bctls.AuthenticatedReliableSession;
import pl.grzegorz2047.standalonethewalls.transport.bctls.AuthenticatedReliableSessionTestFactory;

/** Test-only bridge for network-owned Direct Connect value and session types. */
public final class DirectConnectUiTestFixtures {
    public static final PlayerId SELF_ID = new PlayerId("sf1_" + "a".repeat(52));
    public static final PlayerId OTHER_ID = new PlayerId("sf1_" + "b".repeat(52));
    public static final CanonicalHandle SELF_HANDLE = new CanonicalHandle("player_one");
    public static final CanonicalHandle OTHER_HANDLE = new CanonicalHandle("other_player");
    private static final UUID SESSION_ID = UUID.fromString("12345678-1234-4234-8234-1234567890ab");

    private DirectConnectUiTestFixtures() {
        throw new AssertionError("No instances");
    }

    public static FirstUseConfirmation confirmation() throws DirectConnectEndpointException {
        return new FirstUseConfirmation(
                DirectConnectEndpoint.parse("127.0.0.1:27420"),
                new ServerId("sfs1_" + "c".repeat(52)),
                new ServerFingerprint("0123-4567-89ab-cdef-0123"),
                Instant.parse("2030-01-01T00:00:00Z"),
                new DirectConnectConfirmationToken(new byte[32]));
    }

    public static ConnectedLobbySession openLobbySession() {
        BlockingReliableChannel channel = new BlockingReliableChannel();
        ConnectedLobbySession session = createSession(channel, initialSnapshot());
        start(session);
        return session;
    }

    public static ControlledLobby controlledLobby() {
        ControlledReliableChannel channel = new ControlledReliableChannel();
        ConnectedLobbySession session = createSession(channel, initialSnapshot());
        start(session);
        return new ControlledLobby(session, channel);
    }

    public static LobbySnapshot snapshot(
            long revision,
            LobbyTeam selfTeam,
            boolean selfReady,
            LobbyTeam otherTeam,
            boolean otherReady) {
        return new LobbySnapshot(
                revision,
                List.of(
                        new LobbyMember(SELF_ID, SELF_HANDLE, selfTeam, selfReady),
                        new LobbyMember(OTHER_ID, OTHER_HANDLE, otherTeam, otherReady)));
    }

    private static LobbySnapshot initialSnapshot() {
        return snapshot(1L, LobbyTeam.UNASSIGNED, false, LobbyTeam.BLUE, false);
    }

    private static ConnectedLobbySession createSession(
            ReliableChannel channel, LobbySnapshot initialSnapshot) {
        AuthenticatedReliableSession authenticated =
                AuthenticatedReliableSessionTestFactory.create(channel, SELF_ID, SELF_HANDLE);
        return new ConnectedLobbySession(
                authenticated,
                initialSnapshot,
                initialMatchSnapshot(initialSnapshot),
                ignored -> {});
    }

    private static LobbyMatchPhaseSnapshot initialMatchSnapshot(LobbySnapshot roster) {
        return new LobbyMatchPhaseSnapshot(
                1L,
                roster.revision(),
                LobbyMatchPhaseSnapshot.BEFORE_FIRST_TICK,
                LobbyMatchPhase.WAITING_FOR_PLAYERS,
                0L,
                roster.members().size(),
                1L,
                LobbyCountdownCancellationReason.NONE);
    }

    private static void start(ConnectedLobbySession session) {
        if (!session.startReceiving()) {
            throw new IllegalStateException("test lobby receiver did not start");
        }
    }

    public record SentCommand(MessageType messageType, byte[] payload) {
        public SentCommand {
            payload = Arrays.copyOf(payload, payload.length);
        }

        @Override
        public byte[] payload() {
            return Arrays.copyOf(payload, payload.length);
        }
    }

    public static final class ControlledLobby {
        private ConnectedLobbySession session;
        private final ControlledReliableChannel channel;

        private ControlledLobby(ConnectedLobbySession session, ControlledReliableChannel channel) {
            this.session = session;
            this.channel = channel;
        }

        public synchronized DirectConnectResult connectedResult(
                PlayerSessionAdmissionStatus admissionStatus) {
            Objects.requireNonNull(admissionStatus, "admissionStatus");
            ConnectedLobbySession transferred = session;
            if (transferred == null) {
                throw new IllegalStateException("controlled lobby session was already transferred");
            }
            session = null;
            return new DirectConnectResult.Connected(transferred, admissionStatus);
        }

        public List<SentCommand> sentCommands() {
            return channel.sentCommands();
        }

        public void deliverResult(LobbyCommandResult result, long sequence) {
            channel.deliver(
                    new ProtocolEnvelope(
                            ProtocolVersion.CURRENT,
                            MessageType.LOBBY_COMMAND_RESULT,
                            SESSION_ID,
                            sequence,
                            LobbyProtocolCodec.encodeCommandResult(result)));
        }

        public void deliverSnapshot(LobbySnapshot snapshot, long sequence) {
            channel.deliver(
                    new ProtocolEnvelope(
                            ProtocolVersion.CURRENT,
                            MessageType.LOBBY_SNAPSHOT,
                            SESSION_ID,
                            sequence,
                            LobbyProtocolCodec.encodeSnapshot(snapshot)));
        }

        public void deliverEof() {
            channel.deliverEof();
        }
    }

    private static final class BlockingReliableChannel implements ReliableChannel {
        private final AtomicBoolean open = new AtomicBoolean(true);
        private final CompletableFuture<Optional<ProtocolEnvelope>> receive =
                new CompletableFuture<>();

        @Override
        public CompletionStage<ReliableSendResult> send(MessageType messageType, byte[] payload) {
            return CompletableFuture.completedFuture(new ReliableSendResult(0L));
        }

        @Override
        public CompletionStage<Optional<ProtocolEnvelope>> receive() {
            return receive.minimalCompletionStage();
        }

        @Override
        public boolean isOpen() {
            return open.get();
        }

        @Override
        public CompletionStage<Void> close() {
            open.set(false);
            receive.complete(Optional.empty());
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class ControlledReliableChannel implements ReliableChannel {
        private final Queue<Optional<ProtocolEnvelope>> inbound = new ArrayDeque<>();
        private final List<SentCommand> sent = new ArrayList<>();
        private boolean open = true;
        private CompletableFuture<Optional<ProtocolEnvelope>> waitingReceive;

        @Override
        public synchronized CompletionStage<ReliableSendResult> send(
                MessageType messageType, byte[] payload) {
            if (!open) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("controlled channel is closed"));
            }
            sent.add(new SentCommand(messageType, payload));
            return CompletableFuture.completedFuture(new ReliableSendResult(sent.size()));
        }

        @Override
        public synchronized CompletionStage<Optional<ProtocolEnvelope>> receive() {
            if (!inbound.isEmpty()) {
                return CompletableFuture.completedFuture(inbound.remove());
            }
            if (!open) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            if (waitingReceive != null) {
                throw new IllegalStateException("only one controlled receive may be pending");
            }
            waitingReceive = new CompletableFuture<>();
            return waitingReceive.minimalCompletionStage();
        }

        @Override
        public synchronized boolean isOpen() {
            return open;
        }

        @Override
        public synchronized CompletionStage<Void> close() {
            open = false;
            if (waitingReceive != null) {
                waitingReceive.complete(Optional.empty());
                waitingReceive = null;
            }
            return CompletableFuture.completedFuture(null);
        }

        private synchronized List<SentCommand> sentCommands() {
            return List.copyOf(sent);
        }

        private synchronized void deliver(ProtocolEnvelope envelope) {
            if (!open) {
                throw new IllegalStateException("controlled channel is closed");
            }
            completeOrQueue(Optional.of(envelope));
        }

        private synchronized void deliverEof() {
            open = false;
            completeOrQueue(Optional.empty());
        }

        private void completeOrQueue(Optional<ProtocolEnvelope> envelope) {
            if (waitingReceive != null) {
                CompletableFuture<Optional<ProtocolEnvelope>> waiting = waitingReceive;
                waitingReceive = null;
                waiting.complete(envelope);
            } else {
                inbound.add(envelope);
            }
        }
    }
}
