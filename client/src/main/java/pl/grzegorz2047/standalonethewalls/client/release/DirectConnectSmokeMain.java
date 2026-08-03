package pl.grzegorz2047.standalonethewalls.client.release;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.grzegorz2047.standalonethewalls.client.identity.ClientIdentityStorage;
import pl.grzegorz2047.standalonethewalls.client.network.ConnectedLobbySession;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectAttempt;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectEndpoint;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectEndpointException;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectResult;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectService;
import pl.grzegorz2047.standalonethewalls.client.network.FirstUseConfirmation;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionStatus;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerFingerprint;

/** Distribution-level smoke harness over the production Direct Connect composition. */
public final class DirectConnectSmokeMain {
    public static final int EXIT_OK = 0;
    public static final int EXIT_VERIFICATION_FAILED = 1;
    public static final int EXIT_USAGE = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger(DirectConnectSmokeMain.class);
    private static final Duration RESULT_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(10);

    private DirectConnectSmokeMain() {
        throw new AssertionError("No instances");
    }

    public static void main(String[] arguments) {
        int exitCode = run(arguments);
        if (exitCode != EXIT_OK) {
            System.exit(exitCode);
        }
    }

    public static int run(String[] arguments) {
        Objects.requireNonNull(arguments, "arguments");
        try {
            Options options = Options.parse(arguments);
            VerifiedFirstConnection first = connectFirst(options);
            connectReturning(options, first.playerId());
            LOGGER.info("Direct Connect distribution smoke completed successfully.");
            return EXIT_OK;
        } catch (IllegalArgumentException | DirectConnectEndpointException exception) {
            LOGGER.error(
                    "Usage: sunderfront-direct-connect-smoke --endpoint <host:port> --handle <handle> --expected-fingerprint <fingerprint> --data-dir <directory> --require-first-use");
            return EXIT_USAGE;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.error("Direct Connect distribution smoke was interrupted.");
            return EXIT_VERIFICATION_FAILED;
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            LOGGER.error("Direct Connect distribution smoke failed with a bounded public result.");
            return EXIT_VERIFICATION_FAILED;
        }
    }

    private static VerifiedFirstConnection connectFirst(Options options)
            throws InterruptedException, ExecutionException, TimeoutException {
        try (DirectConnectService service =
                new DirectConnectService(new ClientIdentityStorage(options.dataDirectory()))) {
            DirectConnectResult initial = await(service.connect(options.endpoint(), options.handle()));
            DirectConnectResult connectedResult;
            boolean confirmedFirstUse = false;
            if (initial instanceof DirectConnectResult.ConfirmationRequired required) {
                FirstUseConfirmation confirmation = required.confirmation();
                if (!confirmation.fingerprint().equals(options.expectedFingerprint())) {
                    throw new IllegalStateException("server fingerprint did not match expectation");
                }
                confirmedFirstUse = true;
                connectedResult = await(service.confirmFirstUse(confirmation));
            } else {
                connectedResult = initial;
            }
            if (options.requireFirstUse() && !confirmedFirstUse) {
                throw new IllegalStateException("fresh data directory did not require first use");
            }
            DirectConnectResult.Connected connected = requireConnected(connectedResult);
            if (confirmedFirstUse
                    && connected.admissionStatus()
                            != PlayerSessionAdmissionStatus.LOCAL_FIRST_USE_ACCEPTED) {
                throw new IllegalStateException("first-use admission status was not accepted");
            }
            PlayerId playerId = connected.session().playerId();
            requireExactSelf(connected.session(), options.handle(), playerId);
            close(connected.session());
            return new VerifiedFirstConnection(playerId);
        }
    }

    private static void connectReturning(Options options, PlayerId expectedPlayerId)
            throws InterruptedException, ExecutionException, TimeoutException {
        try (DirectConnectService service =
                new DirectConnectService(new ClientIdentityStorage(options.dataDirectory()))) {
            DirectConnectResult.Connected returning =
                    requireConnected(await(service.connect(options.endpoint(), options.handle())));
            if (returning.admissionStatus()
                    != PlayerSessionAdmissionStatus.LOCAL_RETURNING_ACCEPTED) {
                throw new IllegalStateException("returning identity was not accepted");
            }
            if (!returning.session().playerId().equals(expectedPlayerId)) {
                throw new IllegalStateException("player identity changed after restart");
            }
            requireExactSelf(returning.session(), options.handle(), expectedPlayerId);
            close(returning.session());
        }
    }

    private static DirectConnectResult await(DirectConnectAttempt attempt)
            throws InterruptedException, ExecutionException, TimeoutException {
        return attempt.result()
                .toCompletableFuture()
                .get(RESULT_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS);
    }

    private static DirectConnectResult.Connected requireConnected(DirectConnectResult result) {
        if (result instanceof DirectConnectResult.Connected connected) {
            return connected;
        }
        throw new IllegalStateException("Direct Connect did not return a connected result");
    }

    private static void requireExactSelf(
            ConnectedLobbySession session, CanonicalHandle handle, PlayerId playerId) {
        boolean present =
                session.currentSnapshot().members().stream()
                        .anyMatch(
                                member ->
                                        member.playerId().equals(playerId)
                                                && member.handle().equals(handle));
        if (!present) {
            throw new IllegalStateException("lobby snapshot did not contain exact self");
        }
    }

    private static void close(ConnectedLobbySession session)
            throws InterruptedException, ExecutionException, TimeoutException {
        session.closeAsync()
                .toCompletableFuture()
                .get(CLOSE_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS);
    }

    private record VerifiedFirstConnection(PlayerId playerId) {
        private VerifiedFirstConnection {
            Objects.requireNonNull(playerId, "playerId");
        }
    }

    private record Options(
            DirectConnectEndpoint endpoint,
            CanonicalHandle handle,
            ServerFingerprint expectedFingerprint,
            Path dataDirectory,
            boolean requireFirstUse) {
        private Options {
            Objects.requireNonNull(endpoint, "endpoint");
            Objects.requireNonNull(handle, "handle");
            Objects.requireNonNull(expectedFingerprint, "expectedFingerprint");
            dataDirectory =
                    Objects.requireNonNull(dataDirectory, "dataDirectory")
                            .toAbsolutePath()
                            .normalize();
        }

        private static Options parse(String[] arguments) throws DirectConnectEndpointException {
            String endpoint = null;
            String handle = null;
            String fingerprint = null;
            String dataDirectory = null;
            boolean requireFirstUse = false;
            for (int index = 0; index < arguments.length; index++) {
                String argument = Objects.requireNonNull(arguments[index], "argument");
                switch (argument) {
                    case "--endpoint" -> endpoint = requireValue(arguments, ++index, argument, endpoint);
                    case "--handle" -> handle = requireValue(arguments, ++index, argument, handle);
                    case "--expected-fingerprint" ->
                            fingerprint = requireValue(arguments, ++index, argument, fingerprint);
                    case "--data-dir" ->
                            dataDirectory = requireValue(arguments, ++index, argument, dataDirectory);
                    case "--require-first-use" -> {
                        if (requireFirstUse) {
                            throw new IllegalArgumentException("duplicate first-use flag");
                        }
                        requireFirstUse = true;
                    }
                    default -> throw new IllegalArgumentException("unknown smoke argument");
                }
            }
            if (endpoint == null || handle == null || fingerprint == null || dataDirectory == null) {
                throw new IllegalArgumentException("missing smoke argument");
            }
            return new Options(
                    DirectConnectEndpoint.parse(endpoint),
                    new CanonicalHandle(handle),
                    new ServerFingerprint(fingerprint),
                    Path.of(dataDirectory),
                    requireFirstUse);
        }

        private static String requireValue(
                String[] arguments, int index, String option, String previous) {
            if (previous != null
                    || index >= arguments.length
                    || arguments[index] == null
                    || arguments[index].isBlank()
                    || arguments[index].startsWith("--")) {
                throw new IllegalArgumentException("invalid value for " + option);
            }
            return arguments[index];
        }
    }
}
