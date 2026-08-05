import com.wolfssl.WolfSSL;
import com.wolfssl.WolfSSLContext;
import com.wolfssl.WolfSSLIORecvCallback;
import com.wolfssl.WolfSSLIOSendCallback;
import com.wolfssl.WolfSSLSession;
import com.wolfssl.WolfSSLPskClientCallback;
import com.wolfssl.WolfSSLPskServerCallback;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.IssuedRealtimeTicket;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.OneTimeRealtimeTicketStore;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeChannelBindingDigest;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimePreSharedKey;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTicketContext;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTicketEntropy;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTicketIdentity;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTicketRedemption;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTicketStoreConfig;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTicketStoreException;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RedeemedRealtimeTicket;

/** Non-product negative matrix for the pinned wolfSSL DTLS 1.3 external-PSK spike. */
public final class PskDtls13NegativeMatrix {
    private static final int SOCKET_TIMEOUT_MILLIS = 3_000;
    private static final int HANDSHAKE_TIMEOUT_SECONDS = 8;
    private static final int PARALLEL_TIMEOUT_SECONDS = 20;

    private PskDtls13NegativeMatrix() {}

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 0) {
            throw new IllegalArgumentException("this spike accepts no arguments");
        }

        WolfSSL.loadLibrary();
        new WolfSSL();

        verifyUnknownIdentityRejected();
        verifyExpiredIdentityRejected();
        verifyWrongPskConsumesTicket();
        verifyParallelReplayOnlyOneWins();
        verifyDtls12ClientCannotDowngrade();
        verifyBlockedAcceptCanBeInterruptedAndFreed();

        System.out.println(
                "wolfSSL DTLS 1.3 negative matrix passed; unknown expired bad-psk parallel-replay downgrade cleanup; secrets redacted");
    }

    private static void verifyUnknownIdentityRejected() throws Exception {
        RealtimeTicketStoreConfig config =
                new RealtimeTicketStoreConfig(4, Duration.ofSeconds(30));
        byte[] identity = new byte[RealtimeTicketIdentity.LENGTH_BYTES];
        byte[] key = new byte[RealtimePreSharedKey.LENGTH_BYTES];
        Arrays.fill(identity, (byte) 0x5a);
        Arrays.fill(key, (byte) 0x3c);
        try (OneTimeRealtimeTicketStore store =
                OneTimeRealtimeTicketStore.createProduction(config)) {
            HandshakeAttempt attempt =
                    runHandshake(
                            store,
                            HexFormat.of().formatHex(identity),
                            key,
                            WolfSSL.DTLSv1_3_ServerMethod(),
                            WolfSSL.DTLSv1_3_ClientMethod());
            requireFailedWith(
                    attempt,
                    RealtimeTicketRedemption.Status.UNKNOWN_OR_REPLAYED,
                    "unknown identity");
            requireActiveTickets(store, 0, "unknown identity");
        } finally {
            Arrays.fill(identity, (byte) 0);
            Arrays.fill(key, (byte) 0);
        }
    }

    private static void verifyExpiredIdentityRejected() throws Exception {
        MutableClock clock =
                new MutableClock(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC);
        RealtimeTicketStoreConfig config =
                new RealtimeTicketStoreConfig(4, Duration.ofSeconds(30));
        try (OneTimeRealtimeTicketStore store =
                        new OneTimeRealtimeTicketStore(clock, new PatternEntropy(), config);
                CapturedTicket ticket = issue(store, Duration.ofSeconds(1))) {
            clock.advance(Duration.ofSeconds(2));
            HandshakeAttempt attempt =
                    runHandshake(
                            store,
                            ticket.wireIdentity(),
                            ticket.key(),
                            WolfSSL.DTLSv1_3_ServerMethod(),
                            WolfSSL.DTLSv1_3_ClientMethod());
            requireFailedWith(
                    attempt, RealtimeTicketRedemption.Status.EXPIRED, "expired identity");
            requireActiveTickets(store, 0, "expired identity");
        }
    }

    private static void verifyWrongPskConsumesTicket() throws Exception {
        RealtimeTicketStoreConfig config =
                new RealtimeTicketStoreConfig(4, Duration.ofSeconds(30));
        try (OneTimeRealtimeTicketStore store =
                        OneTimeRealtimeTicketStore.createProduction(config);
                CapturedTicket ticket = issue(store, Duration.ofSeconds(30))) {
            byte[] wrongKey = ticket.key().clone();
            try {
                wrongKey[0] ^= 0x01;
                HandshakeAttempt wrongBinder =
                        runHandshake(
                                store,
                                ticket.wireIdentity(),
                                wrongKey,
                                WolfSSL.DTLSv1_3_ServerMethod(),
                                WolfSSL.DTLSv1_3_ClientMethod());
                requireFailedWith(
                        wrongBinder,
                        RealtimeTicketRedemption.Status.REDEEMED,
                        "wrong PSK binder");

                HandshakeAttempt retry =
                        runHandshake(
                                store,
                                ticket.wireIdentity(),
                                ticket.key(),
                                WolfSSL.DTLSv1_3_ServerMethod(),
                                WolfSSL.DTLSv1_3_ClientMethod());
                requireFailedWith(
                        retry,
                        RealtimeTicketRedemption.Status.UNKNOWN_OR_REPLAYED,
                        "retry after wrong PSK binder");
                requireActiveTickets(store, 0, "wrong PSK binder");
            } finally {
                Arrays.fill(wrongKey, (byte) 0);
            }
        }
    }

    private static void verifyParallelReplayOnlyOneWins() throws Exception {
        RealtimeTicketStoreConfig config =
                new RealtimeTicketStoreConfig(4, Duration.ofSeconds(30));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try (OneTimeRealtimeTicketStore store =
                        OneTimeRealtimeTicketStore.createProduction(config);
                CapturedTicket ticket = issue(store, Duration.ofSeconds(30))) {
            Callable<HandshakeAttempt> attempt =
                    () -> {
                        start.await();
                        return runHandshake(
                                store,
                                ticket.wireIdentity(),
                                ticket.key(),
                                WolfSSL.DTLSv1_3_ServerMethod(),
                                WolfSSL.DTLSv1_3_ClientMethod());
                    };
            Future<HandshakeAttempt> first = executor.submit(attempt);
            Future<HandshakeAttempt> second = executor.submit(attempt);
            start.countDown();

            HandshakeAttempt firstResult =
                    first.get(PARALLEL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            HandshakeAttempt secondResult =
                    second.get(PARALLEL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            int successful =
                    (firstResult.successful() ? 1 : 0) + (secondResult.successful() ? 1 : 0);
            int redeemed =
                    countStatus(firstResult, RealtimeTicketRedemption.Status.REDEEMED)
                            + countStatus(secondResult, RealtimeTicketRedemption.Status.REDEEMED);
            int replayed =
                    countStatus(firstResult, RealtimeTicketRedemption.Status.UNKNOWN_OR_REPLAYED)
                            + countStatus(
                                    secondResult,
                                    RealtimeTicketRedemption.Status.UNKNOWN_OR_REPLAYED);
            if (successful != 1 || redeemed != 1 || replayed != 1) {
                throw new IllegalStateException(
                        "parallel replay did not produce exactly one successful redemption");
            }
            requireActiveTickets(store, 0, "parallel replay");
        } finally {
            executor.shutdownNow();
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("parallel replay executor did not terminate");
            }
        }
    }

    private static void verifyDtls12ClientCannotDowngrade() throws Exception {
        RealtimeTicketStoreConfig config =
                new RealtimeTicketStoreConfig(4, Duration.ofSeconds(30));
        try (OneTimeRealtimeTicketStore store =
                        OneTimeRealtimeTicketStore.createProduction(config);
                CapturedTicket ticket = issue(store, Duration.ofSeconds(30))) {
            HandshakeAttempt attempt =
                    runHandshake(
                            store,
                            ticket.wireIdentity(),
                            ticket.key(),
                            WolfSSL.DTLSv1_3_ServerMethod(),
                            WolfSSL.DTLSv1_2_ClientMethod());
            if (attempt.successful()) {
                throw new IllegalStateException("DTLS 1.2 client established a downgraded session");
            }
            int active = store.activeTicketCount();
            if (active == 1) {
                if (!store.revoke(new RealtimeTicketIdentity(ticket.identityBytes()))) {
                    throw new IllegalStateException("DTLS 1.2 rejection left an unrevokable ticket");
                }
            } else if (active != 0
                    || attempt.redemptionStatus()
                            != RealtimeTicketRedemption.Status.REDEEMED) {
                throw new IllegalStateException("DTLS 1.2 rejection produced an invalid ticket state");
            }
        }
    }

    private static void verifyBlockedAcceptCanBeInterruptedAndFreed() throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        DatagramCallbacks callbacks = new DatagramCallbacks();
        RealtimeTicketStoreConfig config =
                new RealtimeTicketStoreConfig(1, Duration.ofSeconds(30));
        WolfSSLContext context = null;
        WolfSSLSession session = null;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (OneTimeRealtimeTicketStore store =
                        OneTimeRealtimeTicketStore.createProduction(config);
                DatagramSocket serverSocket = new DatagramSocket(0, loopback);
                DatagramSocket silentPeer = new DatagramSocket(0, loopback)) {
            serverSocket.connect(loopback, silentPeer.getLocalPort());
            serverSocket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
            try (RedeemingServerCallback serverCallback =
                    new RedeemingServerCallback(store, "00".repeat(RealtimeTicketIdentity.LENGTH_BYTES))) {
                context = new WolfSSLContext(WolfSSL.DTLSv1_3_ServerMethod());
                context.setPskServerCb(serverCallback);
                session = new WolfSSLSession(context, false);
                session.setIORecv(callbacks);
                session.setIOSend(callbacks);
                session.setIOReadCtx(serverSocket);
                session.setIOWriteCtx(serverSocket);
                requireSuccess(
                        session.dtlsSetPeer(
                                new InetSocketAddress(loopback, silentPeer.getLocalPort())),
                        "blocked server dtlsSetPeer");

                WolfSSLSession ownedSession = session;
                Future<Integer> blocked = executor.submit((Callable<Integer>) ownedSession::accept);
                Thread.sleep(100L);
                closeSocket(serverSocket);
                try {
                    blocked.get(HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (ExecutionException exception) {
                    // Native handshake failure after socket interruption is an expected result.
                }
            }
        } finally {
            executor.shutdownNow();
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("blocked accept executor did not terminate");
            }
            freeSession(session);
            freeContext(context);
        }
    }

    private static int countStatus(
            HandshakeAttempt attempt, RealtimeTicketRedemption.Status status) {
        return attempt.redemptionStatus() == status ? 1 : 0;
    }

    private static void requireFailedWith(
            HandshakeAttempt attempt,
            RealtimeTicketRedemption.Status expected,
            String scenario) {
        if (attempt.successful() || attempt.redemptionStatus() != expected) {
            throw new IllegalStateException(scenario + " did not fail with the expected store status");
        }
    }

    private static void requireActiveTickets(
            OneTimeRealtimeTicketStore store, int expected, String scenario)
            throws RealtimeTicketStoreException {
        if (store.activeTicketCount() != expected) {
            throw new IllegalStateException(scenario + " left an unexpected ticket count");
        }
    }

    private static CapturedTicket issue(
            OneTimeRealtimeTicketStore store, Duration lifetime)
            throws RealtimeTicketStoreException {
        try (IssuedRealtimeTicket issued = store.issue(testContext(), lifetime)) {
            byte[] identity = issued.identity().copyBytes();
            byte[] key = issued.preSharedKey().copyBytes();
            try {
                return new CapturedTicket(identity, key);
            } finally {
                Arrays.fill(identity, (byte) 0);
                Arrays.fill(key, (byte) 0);
            }
        }
    }

    private static HandshakeAttempt runHandshake(
            OneTimeRealtimeTicketStore store,
            String wireIdentity,
            byte[] clientKey,
            long serverMethod,
            long clientMethod)
            throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        RedeemingServerCallback serverCallback =
                new RedeemingServerCallback(store, wireIdentity);
        DatagramCallbacks datagramCallbacks = new DatagramCallbacks();
        WolfSSLContext serverContext = null;
        WolfSSLContext clientContext = null;
        WolfSSLSession serverSession = null;
        WolfSSLSession clientSession = null;
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try (DatagramSocket serverSocket = new DatagramSocket(0, loopback);
                DatagramSocket clientSocket = new DatagramSocket(0, loopback)) {
            serverSocket.connect(loopback, clientSocket.getLocalPort());
            clientSocket.connect(loopback, serverSocket.getLocalPort());
            serverSocket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
            clientSocket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);

            serverContext = new WolfSSLContext(serverMethod);
            clientContext = new WolfSSLContext(clientMethod);
            serverContext.setPskServerCb(serverCallback);
            clientContext.setPskClientCb(new FixedClientCallback(wireIdentity, clientKey));

            serverSession = new WolfSSLSession(serverContext, false);
            clientSession = new WolfSSLSession(clientContext, false);
            serverSession.setIORecv(datagramCallbacks);
            serverSession.setIOSend(datagramCallbacks);
            clientSession.setIORecv(datagramCallbacks);
            clientSession.setIOSend(datagramCallbacks);
            serverSession.setIOReadCtx(serverSocket);
            serverSession.setIOWriteCtx(serverSocket);
            clientSession.setIOReadCtx(clientSocket);
            clientSession.setIOWriteCtx(clientSocket);
            requireSuccess(
                    serverSession.dtlsSetPeer(
                            new InetSocketAddress(loopback, clientSocket.getLocalPort())),
                    "server dtlsSetPeer");
            requireSuccess(
                    clientSession.dtlsSetPeer(
                            new InetSocketAddress(loopback, serverSocket.getLocalPort())),
                    "client dtlsSetPeer");

            WolfSSLSession ownedServerSession = serverSession;
            WolfSSLSession ownedClientSession = clientSession;
            Future<Integer> serverResult =
                    executor.submit((Callable<Integer>) ownedServerSession::accept);
            Future<Integer> clientResult =
                    executor.submit((Callable<Integer>) ownedClientSession::connect);

            boolean successful =
                    awaitSuccess(serverResult, clientResult, serverSocket, clientSocket);
            return new HandshakeAttempt(successful, serverCallback.redemptionStatus());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
            serverCallback.close();
            freeSession(clientSession);
            freeSession(serverSession);
            freeContext(clientContext);
            freeContext(serverContext);
        }
    }

    private static boolean awaitSuccess(
            Future<Integer> serverResult,
            Future<Integer> clientResult,
            DatagramSocket serverSocket,
            DatagramSocket clientSocket)
            throws InterruptedException {
        try {
            int client = clientResult.get(HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            int server = serverResult.get(HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return client == WolfSSL.SSL_SUCCESS && server == WolfSSL.SSL_SUCCESS;
        } catch (ExecutionException | TimeoutException exception) {
            serverSocket.close();
            clientSocket.close();
            return false;
        }
    }

    private static RealtimeTicketContext testContext() {
        return new RealtimeTicketContext(
                new ServerId("sfs1_" + "a".repeat(52)),
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                new PlayerId("sf1_" + "b".repeat(52)),
                new RealtimeChannelBindingDigest(new byte[32]),
                1L);
    }

    private static void closeSocket(DatagramSocket socket) {
        socket.close();
    }

    private static void requireSuccess(int result, String operation) {
        if (result != WolfSSL.SSL_SUCCESS) {
            throw new IllegalStateException(operation + " failed with redacted result " + result);
        }
    }

    private static void freeSession(WolfSSLSession session) {
        if (session != null) {
            try {
                session.freeSSL();
            } catch (Exception ignored) {
                // Best-effort cleanup in an isolated research process.
            }
        }
    }

    private static void freeContext(WolfSSLContext context) {
        if (context != null) {
            try {
                context.free();
            } catch (Exception ignored) {
                // Best-effort cleanup in an isolated research process.
            }
        }
    }

    private record HandshakeAttempt(
            boolean successful, RealtimeTicketRedemption.Status redemptionStatus) {}

    private static final class CapturedTicket implements AutoCloseable {
        private final byte[] identity;
        private final String wireIdentity;
        private final byte[] key;

        private CapturedTicket(byte[] identity, byte[] key) {
            this.identity = identity.clone();
            this.wireIdentity = HexFormat.of().formatHex(identity);
            this.key = key.clone();
        }

        private byte[] identityBytes() {
            return identity.clone();
        }

        private String wireIdentity() {
            return wireIdentity;
        }

        private byte[] key() {
            return key;
        }

        @Override
        public void close() {
            Arrays.fill(identity, (byte) 0);
            Arrays.fill(key, (byte) 0);
        }
    }

    private static final class PatternEntropy implements RealtimeTicketEntropy {
        private final AtomicInteger sequence = new AtomicInteger(1);

        @Override
        public byte[] randomBytes(int length) {
            byte[] bytes = new byte[length];
            int start = sequence.getAndAdd(length);
            for (int index = 0; index < bytes.length; index++) {
                bytes[index] = (byte) (start + index);
            }
            return bytes;
        }
    }

    private static final class MutableClock extends Clock {
        private Instant current;
        private final ZoneId zone;

        private MutableClock(Instant current, ZoneId zone) {
            this.current = current;
            this.zone = zone;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId newZone) {
            return zone.equals(newZone) ? this : new MutableClock(current, newZone);
        }

        @Override
        public Instant instant() {
            return current;
        }
    }

    private static final class DatagramCallbacks
            implements WolfSSLIORecvCallback, WolfSSLIOSendCallback {
        @Override
        public int receiveCallback(WolfSSLSession session, byte[] buffer, int size, Object context) {
            DatagramSocket socket = requireSocket(context);
            DatagramPacket packet = new DatagramPacket(buffer, Math.min(size, buffer.length));
            try {
                socket.receive(packet);
                return packet.getLength();
            } catch (SocketTimeoutException exception) {
                return WolfSSL.WOLFSSL_CBIO_ERR_TIMEOUT;
            } catch (SocketException exception) {
                return socket.isClosed()
                        ? WolfSSL.WOLFSSL_CBIO_ERR_CONN_CLOSE
                        : WolfSSL.WOLFSSL_CBIO_ERR_CONN_RST;
            } catch (IOException exception) {
                return WolfSSL.WOLFSSL_CBIO_ERR_GENERAL;
            }
        }

        @Override
        public int sendCallback(WolfSSLSession session, byte[] buffer, int size, Object context) {
            DatagramSocket socket = requireSocket(context);
            int length = Math.min(size, buffer.length);
            DatagramPacket packet =
                    new DatagramPacket(buffer, length, socket.getRemoteSocketAddress());
            try {
                socket.send(packet);
                return length;
            } catch (SocketException exception) {
                return socket.isClosed()
                        ? WolfSSL.WOLFSSL_CBIO_ERR_CONN_CLOSE
                        : WolfSSL.WOLFSSL_CBIO_ERR_CONN_RST;
            } catch (IOException exception) {
                return WolfSSL.WOLFSSL_CBIO_ERR_GENERAL;
            }
        }

        private static DatagramSocket requireSocket(Object context) {
            if (context instanceof DatagramSocket socket) {
                return socket;
            }
            throw new IllegalArgumentException("wolfSSL datagram callback context is invalid");
        }
    }

    private static final class FixedClientCallback implements WolfSSLPskClientCallback {
        private final String identity;
        private final byte[] borrowedKey;

        private FixedClientCallback(String identity, byte[] borrowedKey) {
            this.identity = identity;
            this.borrowedKey = borrowedKey;
        }

        @Override
        public long pskClientCallback(
                WolfSSLSession session,
                String identityHint,
                StringBuffer outputIdentity,
                long identityMaximumLength,
                byte[] outputKey,
                long keyMaximumLength) {
            if (identity.length() > identityMaximumLength
                    || borrowedKey.length > keyMaximumLength
                    || outputKey.length < borrowedKey.length) {
                return 0L;
            }
            outputIdentity.append(identity);
            System.arraycopy(borrowedKey, 0, outputKey, 0, borrowedKey.length);
            return borrowedKey.length;
        }
    }

    private static final class RedeemingServerCallback
            implements WolfSSLPskServerCallback, AutoCloseable {
        private final OneTimeRealtimeTicketStore store;
        private final String expectedIdentity;
        private final AtomicReference<RedeemedRealtimeTicket> redeemed = new AtomicReference<>();
        private final AtomicReference<RealtimeTicketRedemption.Status> redemptionStatus =
                new AtomicReference<>();

        private RedeemingServerCallback(
                OneTimeRealtimeTicketStore store, String expectedIdentity) {
            this.store = store;
            this.expectedIdentity = expectedIdentity;
        }

        @Override
        public long pskServerCallback(
                WolfSSLSession session,
                String identity,
                byte[] outputKey,
                long keyMaximumLength) {
            if (!expectedIdentity.equals(identity)) {
                return 0L;
            }

            RedeemedRealtimeTicket ticket = redeemed.get();
            if (ticket == null) {
                ticket = redeem(identity);
                if (ticket == null) {
                    return 0L;
                }
                if (!redeemed.compareAndSet(null, ticket)) {
                    ticket.close();
                    ticket = redeemed.get();
                }
            }

            byte[] key = null;
            try {
                key = ticket.preSharedKey().copyBytes();
                if (key.length > keyMaximumLength || outputKey.length < key.length) {
                    return 0L;
                }
                System.arraycopy(key, 0, outputKey, 0, key.length);
                return key.length;
            } finally {
                if (key != null) {
                    Arrays.fill(key, (byte) 0);
                }
            }
        }

        private RedeemedRealtimeTicket redeem(String identity) {
            byte[] identityBytes = null;
            try {
                identityBytes = HexFormat.of().parseHex(identity);
                if (identityBytes.length != RealtimeTicketIdentity.LENGTH_BYTES) {
                    return null;
                }
                RealtimeTicketRedemption result =
                        store.redeem(new RealtimeTicketIdentity(identityBytes));
                redemptionStatus.compareAndSet(null, result.status());
                if (result.status() != RealtimeTicketRedemption.Status.REDEEMED) {
                    return null;
                }
                return result.ticket().orElseThrow();
            } catch (IllegalArgumentException | RealtimeTicketStoreException exception) {
                return null;
            } finally {
                if (identityBytes != null) {
                    Arrays.fill(identityBytes, (byte) 0);
                }
            }
        }

        private RealtimeTicketRedemption.Status redemptionStatus() {
            return redemptionStatus.get();
        }

        @Override
        public void close() {
            RedeemedRealtimeTicket ticket = redeemed.getAndSet(null);
            if (ticket != null) {
                ticket.close();
            }
        }
    }
}
