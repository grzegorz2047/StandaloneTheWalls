import com.wolfssl.WolfSSL;
import com.wolfssl.WolfSSLContext;
import com.wolfssl.WolfSSLSession;
import com.wolfssl.WolfSSLPskClientCallback;
import com.wolfssl.WolfSSLPskServerCallback;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.IssuedRealtimeTicket;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.OneTimeRealtimeTicketStore;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeChannelBindingDigest;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimePreSharedKey;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTicketContext;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTicketIdentity;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTicketRedemption;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTicketStoreConfig;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTicketStoreException;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RedeemedRealtimeTicket;

/** Non-product Linux proof for one-time external-PSK DTLS 1.3 through wolfSSL JNI. */
public final class PskDtls13Loopback {
    private static final int SOCKET_TIMEOUT_MILLIS = 5_000;
    private static final int HANDSHAKE_TIMEOUT_SECONDS = 10;
    private static final byte[] PAYLOAD = "sunderfront-dtls13-psk".getBytes(StandardCharsets.UTF_8);

    private PskDtls13Loopback() {}

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 0) {
            throw new IllegalArgumentException("this spike accepts no arguments");
        }

        WolfSSL.loadLibrary();
        new WolfSSL();

        RealtimeTicketStoreConfig config =
                new RealtimeTicketStoreConfig(4, Duration.ofSeconds(30));
        byte[] capturedClientKey = null;
        try (OneTimeRealtimeTicketStore store =
                        OneTimeRealtimeTicketStore.createProduction(config);
                IssuedRealtimeTicket issued =
                        store.issue(testContext(), Duration.ofSeconds(30))) {
            byte[] identityBytes = issued.identity().copyBytes();
            capturedClientKey = issued.preSharedKey().copyBytes();
            String wireIdentity = HexFormat.of().formatHex(identityBytes);
            Arrays.fill(identityBytes, (byte) 0);

            boolean firstHandshake = runHandshake(store, wireIdentity, capturedClientKey, true);
            if (!firstHandshake) {
                throw new IllegalStateException("first DTLS 1.3 PSK handshake failed");
            }

            boolean replayHandshake = runHandshake(store, wireIdentity, capturedClientKey, false);
            if (replayHandshake) {
                throw new IllegalStateException("replayed ticket unexpectedly completed a handshake");
            }
            if (store.activeTicketCount() != 0) {
                throw new IllegalStateException("ticket store retained state after redeem and replay");
            }

            System.out.println(
                    "wolfSSL DTLS 1.3 external-PSK loopback passed; replay rejected; secrets redacted");
        } finally {
            if (capturedClientKey != null) {
                Arrays.fill(capturedClientKey, (byte) 0);
            }
        }
    }

    private static boolean runHandshake(
            OneTimeRealtimeTicketStore store,
            String wireIdentity,
            byte[] clientKey,
            boolean exchangeApplicationData)
            throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        RedeemingServerCallback serverCallback =
                new RedeemingServerCallback(store, wireIdentity);
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

            serverContext = new WolfSSLContext(WolfSSL.DTLSv1_3_ServerMethod());
            clientContext = new WolfSSLContext(WolfSSL.DTLSv1_3_ClientMethod());
            serverContext.setPskServerCb(serverCallback);
            clientContext.setPskClientCb(new FixedClientCallback(wireIdentity, clientKey));

            serverSession = new WolfSSLSession(serverContext);
            clientSession = new WolfSSLSession(clientContext);
            requireSuccess(serverSession.setFd(serverSocket), "server setFd");
            requireSuccess(clientSession.setFd(clientSocket), "client setFd");
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
            Future<Integer> serverResult = executor.submit(ownedServerSession::accept);
            Future<Integer> clientResult = executor.submit(ownedClientSession::connect);

            boolean successful =
                    awaitSuccess(serverResult, clientResult, serverSocket, clientSocket);
            if (!successful) {
                return false;
            }

            if (exchangeApplicationData) {
                requireSuccess(
                        clientSession.write(PAYLOAD, PAYLOAD.length), "client application write");
                byte[] received = new byte[PAYLOAD.length];
                int read = serverSession.read(received, received.length);
                if (read != PAYLOAD.length
                        || !Arrays.equals(PAYLOAD, Arrays.copyOf(received, read))) {
                    throw new IllegalStateException("DTLS application payload mismatch");
                }
                Arrays.fill(received, (byte) 0);
            }
            return true;
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
                // Best-effort cleanup in an isolated process that terminates after the spike.
            }
        }
    }

    private static void freeContext(WolfSSLContext context) {
        if (context != null) {
            try {
                context.free();
            } catch (Exception ignored) {
                // Best-effort cleanup in an isolated process that terminates after the spike.
            }
        }
    }

    private static final class FixedClientCallback implements WolfSSLPskClientCallback {
        private final String identity;
        private final byte[] key;

        private FixedClientCallback(String identity, byte[] key) {
            this.identity = identity;
            this.key = key.clone();
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
                    || key.length > keyMaximumLength
                    || outputKey.length < key.length) {
                return 0L;
            }
            outputIdentity.append(identity);
            System.arraycopy(key, 0, outputKey, 0, key.length);
            return key.length;
        }
    }

    private static final class RedeemingServerCallback
            implements WolfSSLPskServerCallback, AutoCloseable {
        private final OneTimeRealtimeTicketStore store;
        private final String expectedIdentity;
        private final AtomicReference<RedeemedRealtimeTicket> redeemed = new AtomicReference<>();

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

        @Override
        public void close() {
            RedeemedRealtimeTicket ticket = redeemed.getAndSet(null);
            if (ticket != null) {
                ticket.close();
            }
        }
    }
}
