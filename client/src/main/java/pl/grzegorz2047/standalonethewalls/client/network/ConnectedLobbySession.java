package pl.grzegorz2047.standalonethewalls.client.network;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolException;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;
import pl.grzegorz2047.standalonethewalls.transport.bctls.AuthenticatedReliableSession;

/** Owns one admitted reliable session and its monotonic immutable lobby snapshots. */
public final class ConnectedLobbySession implements AutoCloseable {
    private final AuthenticatedReliableSession session;
    private final PlayerId playerId;
    private final CanonicalHandle handle;
    private final AtomicReference<LobbySnapshot> snapshot;
    private final AtomicReference<DirectConnectFailure> terminalFailure = new AtomicReference<>();
    private final AtomicBoolean receiverStarted = new AtomicBoolean();
    private final AtomicBoolean closing = new AtomicBoolean();
    private final CompletableFuture<Void> closeFuture = new CompletableFuture<>();
    private final CompletableFuture<Optional<DirectConnectFailure>> termination =
            new CompletableFuture<>();
    private final Consumer<ConnectedLobbySession> ownershipReleased;

    ConnectedLobbySession(
            AuthenticatedReliableSession session,
            LobbySnapshot initialSnapshot,
            Consumer<ConnectedLobbySession> ownershipReleased) {
        this.session = Objects.requireNonNull(session, "session");
        playerId = session.playerId();
        handle = session.handle();
        snapshot =
                new AtomicReference<>(Objects.requireNonNull(initialSnapshot, "initialSnapshot"));
        this.ownershipReleased = Objects.requireNonNull(ownershipReleased, "ownershipReleased");
        requireExactSelf(initialSnapshot, playerId, handle);
    }

    public PlayerId playerId() {
        return playerId;
    }

    public CanonicalHandle handle() {
        return handle;
    }

    public LobbySnapshot currentSnapshot() {
        return snapshot.get();
    }

    public boolean isOpen() {
        return receiverStarted.get() && !closing.get() && session.isOpen();
    }

    public Optional<DirectConnectFailure> terminalFailure() {
        return Optional.ofNullable(terminalFailure.get());
    }

    public CompletionStage<Optional<DirectConnectFailure>> termination() {
        return termination.minimalCompletionStage();
    }

    public CompletionStage<Void> closeAsync() {
        finish(Optional.empty());
        return closeFuture.minimalCompletionStage();
    }

    @Override
    public void close() {
        closeAsync();
    }

    void startReceiving() {
        if (!receiverStarted.compareAndSet(false, true)) {
            throw new IllegalStateException("lobby receiver can be started only once");
        }
        if (closing.get()) {
            return;
        }
        try {
            Thread.ofVirtual()
                    .name("sunderfront-direct-connect-lobby-receiver")
                    .start(this::receiveLoop);
        } catch (RuntimeException exception) {
            finish(Optional.of(DirectConnectFailure.of(DirectConnectFailureCode.INTERNAL_FAILURE)));
            throw exception;
        }
    }

    static boolean containsExactSelf(
            LobbySnapshot candidate, PlayerId expectedPlayerId, CanonicalHandle expectedHandle) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(expectedPlayerId, "expectedPlayerId");
        Objects.requireNonNull(expectedHandle, "expectedHandle");
        for (LobbyMember member : candidate.members()) {
            if (member.playerId().equals(expectedPlayerId)) {
                return member.handle().equals(expectedHandle);
            }
        }
        return false;
    }

    private void receiveLoop() {
        Optional<DirectConnectFailure> failure = Optional.empty();
        try {
            while (!closing.get()) {
                Optional<ProtocolEnvelope> received =
                        Objects.requireNonNull(
                                        session.reliableChannel().receive(), "lobby receive stage")
                                .toCompletableFuture()
                                .get();
                if (received.isEmpty()) {
                    if (!closing.get()) {
                        failure =
                                Optional.of(
                                        DirectConnectFailure.of(
                                                DirectConnectFailureCode.CONNECTION_CLOSED));
                    }
                    break;
                }
                ProtocolEnvelope envelope = received.orElseThrow();
                if (envelope.messageType() != MessageType.LOBBY_SNAPSHOT) {
                    failure =
                            Optional.of(
                                    DirectConnectFailure.of(
                                            DirectConnectFailureCode.UNEXPECTED_MESSAGE));
                    break;
                }
                LobbySnapshot next;
                try {
                    next = LobbyProtocolCodec.decodeSnapshot(envelope.payload());
                } catch (LobbyProtocolException exception) {
                    failure =
                            Optional.of(
                                    DirectConnectFailure.of(
                                            DirectConnectFailureCode.LOBBY_SNAPSHOT_MALFORMED));
                    break;
                }
                LobbySnapshot current = snapshot.get();
                if (next.revision() <= current.revision()) {
                    failure =
                            Optional.of(
                                    DirectConnectFailure.of(
                                            DirectConnectFailureCode.LOBBY_SNAPSHOT_STALE));
                    break;
                }
                if (!containsExactSelf(next, playerId, handle)) {
                    failure =
                            Optional.of(
                                    DirectConnectFailure.of(
                                            DirectConnectFailureCode.LOBBY_IDENTITY_MISMATCH));
                    break;
                }
                snapshot.set(next);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (!closing.get()) {
                failure =
                        Optional.of(
                                DirectConnectFailure.of(
                                        DirectConnectFailureCode.CONNECTION_CLOSED));
            }
        } catch (ExecutionException | RuntimeException exception) {
            if (!closing.get()) {
                failure =
                        Optional.of(
                                DirectConnectFailure.of(
                                        DirectConnectFailureCode.CONNECTION_CLOSED));
            }
        } finally {
            finish(failure);
        }
    }

    private void finish(Optional<DirectConnectFailure> failure) {
        if (!closing.compareAndSet(false, true)) {
            return;
        }
        failure.ifPresent(terminalFailure::set);
        CompletionStage<Void> closeStage;
        try {
            closeStage = Objects.requireNonNull(session.closeAsync(), "session close stage");
        } catch (RuntimeException exception) {
            completeClose(failure, exception);
            return;
        }
        closeStage.whenComplete((ignored, closeFailure) -> completeClose(failure, closeFailure));
    }

    private void completeClose(Optional<DirectConnectFailure> failure, Throwable closeFailure) {
        try {
            ownershipReleased.accept(this);
        } catch (RuntimeException ignored) {
            // Ownership cleanup cannot expose callback details to presentation code.
        }
        if (closeFailure == null) {
            closeFuture.complete(null);
        } else {
            closeFuture.completeExceptionally(
                    new IllegalStateException("connected lobby session close failed"));
        }
        termination.complete(failure);
    }

    private static void requireExactSelf(
            LobbySnapshot initialSnapshot, PlayerId playerId, CanonicalHandle handle) {
        if (!containsExactSelf(initialSnapshot, playerId, handle)) {
            throw new IllegalArgumentException(
                    "initial lobby snapshot must contain the authenticated player");
        }
    }
}
