package pl.grzegorz2047.standalonethewalls.client.network;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableChannel;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableSendResult;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerFingerprint;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;
import pl.grzegorz2047.standalonethewalls.transport.bctls.AuthenticatedReliableSession;
import pl.grzegorz2047.standalonethewalls.transport.bctls.AuthenticatedReliableSessionTestFactory;

/** Test-only bridge for network-owned Direct Connect value and session types. */
public final class DirectConnectUiTestFixtures {
    private static final PlayerId SELF_ID = new PlayerId("sf1_" + "a".repeat(52));
    private static final PlayerId OTHER_ID = new PlayerId("sf1_" + "b".repeat(52));
    private static final CanonicalHandle SELF_HANDLE = new CanonicalHandle("player_one");

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
        AuthenticatedReliableSession authenticated =
                AuthenticatedReliableSessionTestFactory.create(channel, SELF_ID, SELF_HANDLE);
        LobbySnapshot snapshot =
                new LobbySnapshot(
                        1L,
                        List.of(
                                new LobbyMember(SELF_ID, SELF_HANDLE),
                                new LobbyMember(OTHER_ID, new CanonicalHandle("other_player"))));
        ConnectedLobbySession session =
                new ConnectedLobbySession(authenticated, snapshot, ignored -> {});
        if (!session.startReceiving()) {
            throw new IllegalStateException("test lobby receiver did not start");
        }
        return session;
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
}
