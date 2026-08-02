package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityChallenge;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityChallengePayload;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityChallengeService;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityPayloadCodec;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityPayloadException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityProof;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityResultPayload;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityResultStatus;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityVerification;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerIdentity;

/** Bounded challenge-proof-result state machine over one bootstrapped reliable session. */
public final class IdentityExchange {
    private static final AtomicLong EXCHANGE_IDS = new AtomicLong();

    private IdentityExchange() {
        throw new AssertionError("No instances");
    }

    public static CompletionStage<AuthenticatedReliableSession> authenticateServer(
            BootstrappedReliableSession session,
            IdentityChallengeService challengeService,
            IdentityExchangeConfig config) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(challengeService, "challengeService");
        Objects.requireNonNull(config, "config");
        return start(session, "server", () -> runServer(session, challengeService, config), config);
    }

    public static CompletionStage<AuthenticatedReliableSession> authenticateClient(
            BootstrappedReliableSession session,
            PlayerIdentity identity,
            CanonicalHandle handle,
            Clock clock,
            IdentityExchangeConfig config) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(config, "config");
        return start(
                session,
                "client",
                () -> runClient(session, identity, handle, clock, config),
                config);
    }

    private static CompletionStage<AuthenticatedReliableSession> start(
            BootstrappedReliableSession session,
            String role,
            ExchangeOperation operation,
            IdentityExchangeConfig config) {
        CompletableFuture<AuthenticatedReliableSession> result = new CompletableFuture<>();
        if (!session.claimIdentityExchange()) {
            IdentityExchangeException failure =
                    new IdentityExchangeException(
                            IdentityExchangeException.Code.EXCHANGE_ALREADY_STARTED,
                            "identity exchange was already started for this session");
            closeAsynchronously(session, failure, result);
            return result.minimalCompletionStage();
        }

        long exchangeId = EXCHANGE_IDS.incrementAndGet();
        try {
            Thread.ofVirtual()
                    .name("sunderfront-identity-" + role + '-' + exchangeId)
                    .start(
                            () -> {
                                try {
                                    result.complete(operation.run());
                                } catch (IdentityExchangeException exception) {
                                    closeSynchronously(session, exception, config);
                                    result.completeExceptionally(exception);
                                } catch (RuntimeException exception) {
                                    IdentityExchangeException failure =
                                            new IdentityExchangeException(
                                                    IdentityExchangeException.Code.INTERNAL_ERROR,
                                                    "identity exchange failed internally",
                                                    exception);
                                    closeSynchronously(session, failure, config);
                                    result.completeExceptionally(failure);
                                }
                            });
        } catch (RuntimeException exception) {
            IdentityExchangeException failure =
                    new IdentityExchangeException(
                            IdentityExchangeException.Code.INTERNAL_ERROR,
                            "identity exchange thread could not be started",
                            exception);
            closeAsynchronously(session, failure, result);
        }
        return result.minimalCompletionStage();
    }

    private static AuthenticatedReliableSession runServer(
            BootstrappedReliableSession session,
            IdentityChallengeService challengeService,
            IdentityExchangeConfig config)
            throws IdentityExchangeException {
        long deadline = deadline(config);
        boolean challengeConsumed = false;
        try {
            IdentityChallenge challenge =
                    challengeService.issue(
                            session.security().serverId(),
                            session.sessionId(),
                            session.security().channelBinding());
            await(
                    session.reliableChannel()
                            .send(
                                    MessageType.IDENTITY_CHALLENGE,
                                    IdentityPayloadCodec.encodeChallenge(challenge)),
                    config,
                    deadline,
                    "send identity challenge");

            ProtocolEnvelope proofEnvelope =
                    receiveRequired(session, config, deadline, "receive identity proof");
            if (proofEnvelope.messageType() != MessageType.IDENTITY_PROOF) {
                IdentityExchangeException failure =
                        new IdentityExchangeException(
                                IdentityExchangeException.Code.UNEXPECTED_MESSAGE,
                                "server expected exactly one identity proof");
                sendFailureResult(
                        session,
                        new IdentityResultPayload(IdentityResultStatus.UNEXPECTED_MESSAGE),
                        config,
                        deadline,
                        failure);
                throw failure;
            }

            IdentityProof proof;
            try {
                proof = IdentityPayloadCodec.decodeProof(proofEnvelope.payload());
            } catch (IdentityPayloadException exception) {
                IdentityExchangeException failure =
                        new IdentityExchangeException(
                                IdentityExchangeException.Code.MALFORMED_PAYLOAD,
                                "identity proof payload is malformed",
                                exception);
                sendFailureResult(
                        session,
                        new IdentityResultPayload(IdentityResultStatus.MALFORMED_PROOF),
                        config,
                        deadline,
                        failure);
                throw failure;
            }

            IdentityVerification verification = challengeService.verify(session.sessionId(), proof);
            challengeConsumed = true;
            IdentityResultStatus status =
                    IdentityResultStatus.fromVerification(verification.status());
            await(
                    session.reliableChannel()
                            .send(
                                    MessageType.IDENTITY_RESULT,
                                    IdentityPayloadCodec.encodeResult(
                                            new IdentityResultPayload(status))),
                    config,
                    deadline,
                    "send identity result");
            if (!verification.isAccepted()) {
                throw new IdentityExchangeException(
                        IdentityExchangeException.Code.REJECTED,
                        "identity proof was rejected",
                        status);
            }
            return new AuthenticatedReliableSession(
                    session,
                    verification.playerId().orElseThrow(),
                    verification.handle().orElseThrow());
        } finally {
            if (!challengeConsumed) {
                challengeService.discard(session.sessionId());
            }
        }
    }

    private static AuthenticatedReliableSession runClient(
            BootstrappedReliableSession session,
            PlayerIdentity identity,
            CanonicalHandle handle,
            Clock clock,
            IdentityExchangeConfig config)
            throws IdentityExchangeException {
        long deadline = deadline(config);
        ProtocolEnvelope challengeEnvelope =
                receiveRequired(session, config, deadline, "receive identity challenge");
        if (challengeEnvelope.messageType() != MessageType.IDENTITY_CHALLENGE) {
            throw new IdentityExchangeException(
                    IdentityExchangeException.Code.UNEXPECTED_MESSAGE,
                    "client expected identity challenge as the first identity message");
        }

        IdentityChallengePayload challengePayload;
        try {
            challengePayload = IdentityPayloadCodec.decodeChallenge(challengeEnvelope.payload());
        } catch (IdentityPayloadException exception) {
            throw new IdentityExchangeException(
                    IdentityExchangeException.Code.MALFORMED_PAYLOAD,
                    "identity challenge payload is malformed",
                    exception);
        }
        if (!clock.instant().isBefore(challengePayload.expiresAt())) {
            throw new IdentityExchangeException(
                    IdentityExchangeException.Code.EXPIRED_CHALLENGE,
                    "identity challenge expired before proof creation");
        }

        IdentityChallenge challenge =
                new IdentityChallenge(
                        session.security().serverId(),
                        session.sessionId(),
                        challengePayload.nonce(),
                        session.security().channelBinding(),
                        challengePayload.expiresAt());
        IdentityProof proof;
        try {
            proof =
                    IdentityProof.create(
                            identity,
                            pl.grzegorz2047.standalonethewalls.protocol.ProtocolVersion.CURRENT,
                            challenge,
                            handle);
        } catch (IdentityException exception) {
            throw new IdentityExchangeException(
                    IdentityExchangeException.Code.SIGNING_FAILED,
                    "identity proof could not be created",
                    exception);
        }

        await(
                session.reliableChannel()
                        .send(MessageType.IDENTITY_PROOF, IdentityPayloadCodec.encodeProof(proof)),
                config,
                deadline,
                "send identity proof");
        ProtocolEnvelope resultEnvelope =
                receiveRequired(session, config, deadline, "receive identity result");
        if (resultEnvelope.messageType() != MessageType.IDENTITY_RESULT) {
            throw new IdentityExchangeException(
                    IdentityExchangeException.Code.UNEXPECTED_MESSAGE,
                    "client expected exactly one identity result");
        }

        IdentityResultPayload resultPayload;
        try {
            resultPayload = IdentityPayloadCodec.decodeResult(resultEnvelope.payload());
        } catch (IdentityPayloadException exception) {
            throw new IdentityExchangeException(
                    IdentityExchangeException.Code.MALFORMED_PAYLOAD,
                    "identity result payload is malformed",
                    exception);
        }
        if (!resultPayload.isAccepted()) {
            throw new IdentityExchangeException(
                    IdentityExchangeException.Code.REJECTED,
                    "server rejected identity proof",
                    resultPayload.status());
        }
        return new AuthenticatedReliableSession(session, identity.playerId(), handle);
    }

    private static ProtocolEnvelope receiveRequired(
            BootstrappedReliableSession session,
            IdentityExchangeConfig config,
            long deadline,
            String operation)
            throws IdentityExchangeException {
        Optional<ProtocolEnvelope> received =
                await(session.reliableChannel().receive(), config, deadline, operation);
        return received.orElseThrow(
                () ->
                        new IdentityExchangeException(
                                IdentityExchangeException.Code.CLEAN_EOF,
                                "peer ended the reliable stream during identity exchange"));
    }

    private static void sendFailureResult(
            BootstrappedReliableSession session,
            IdentityResultPayload payload,
            IdentityExchangeConfig config,
            long deadline,
            IdentityExchangeException primary) {
        try {
            await(
                    session.reliableChannel()
                            .send(
                                    MessageType.IDENTITY_RESULT,
                                    IdentityPayloadCodec.encodeResult(payload)),
                    config,
                    deadline,
                    "send identity failure result");
        } catch (IdentityExchangeException sendFailure) {
            primary.addSuppressed(sendFailure);
        }
    }

    private static <T> T await(
            CompletionStage<T> stage,
            IdentityExchangeConfig config,
            long deadline,
            String operation)
            throws IdentityExchangeException {
        Objects.requireNonNull(stage, "stage");
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0L) {
            throw timeout(operation);
        }
        long timeoutNanos = Math.min(config.stepTimeout().toNanos(), remaining);
        try {
            return stage.toCompletableFuture().get(timeoutNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            throw new IdentityExchangeException(
                    IdentityExchangeException.Code.TIMEOUT,
                    operation + " exceeded its bounded timeout",
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IdentityExchangeException(
                    IdentityExchangeException.Code.CHANNEL_FAILURE,
                    operation + " was interrupted",
                    exception);
        } catch (ExecutionException | CompletionException exception) {
            throw new IdentityExchangeException(
                    IdentityExchangeException.Code.CHANNEL_FAILURE,
                    operation + " failed on the reliable channel",
                    unwrap(exception));
        }
    }

    private static long deadline(IdentityExchangeConfig config) {
        return System.nanoTime() + config.overallTimeout().toNanos();
    }

    private static IdentityExchangeException timeout(String operation) {
        return new IdentityExchangeException(
                IdentityExchangeException.Code.TIMEOUT,
                operation + " exceeded the overall identity exchange deadline");
    }

    private static void closeSynchronously(
            BootstrappedReliableSession session,
            IdentityExchangeException primary,
            IdentityExchangeConfig config) {
        try {
            session.closeAsync()
                    .toCompletableFuture()
                    .get(config.closeTimeout().toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            primary.addSuppressed(
                    new IdentityExchangeException(
                            IdentityExchangeException.Code.CLOSE_FAILURE,
                            "identity session close was interrupted",
                            exception));
        } catch (TimeoutException exception) {
            primary.addSuppressed(
                    new IdentityExchangeException(
                            IdentityExchangeException.Code.CLOSE_FAILURE,
                            "identity session close exceeded its bounded timeout",
                            exception));
        } catch (ExecutionException | CompletionException exception) {
            primary.addSuppressed(
                    new IdentityExchangeException(
                            IdentityExchangeException.Code.CLOSE_FAILURE,
                            "identity session close failed",
                            unwrap(exception)));
        }
    }

    private static void closeAsynchronously(
            BootstrappedReliableSession session,
            IdentityExchangeException primary,
            CompletableFuture<AuthenticatedReliableSession> result) {
        session.closeAsync()
                .whenComplete(
                        (unused, closeFailure) -> {
                            if (closeFailure != null) {
                                primary.addSuppressed(unwrap(closeFailure));
                            }
                            result.completeExceptionally(primary);
                        });
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof ExecutionException || current instanceof CompletionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @FunctionalInterface
    private interface ExchangeOperation {
        AuthenticatedReliableSession run() throws IdentityExchangeException;
    }
}
