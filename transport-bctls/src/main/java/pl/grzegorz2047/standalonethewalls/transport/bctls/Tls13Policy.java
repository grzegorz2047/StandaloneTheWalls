package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

/** Strict TLS 1.3 and ALPN policy shared by client and server socket adapters. */
public final class Tls13Policy {
    public static final String APPLICATION_PROTOCOL = "sunderfront/1";
    public static final String PROTOCOL = "TLSv1.3";

    private static final List<String> CIPHER_PREFERENCE =
            List.of(
                    "TLS_AES_128_GCM_SHA256",
                    "TLS_CHACHA20_POLY1305_SHA256",
                    "TLS_AES_256_GCM_SHA384");
    private static final Set<String> ALLOWED_CIPHERS = Set.copyOf(CIPHER_PREFERENCE);

    private Tls13Policy() {
        throw new AssertionError("No instances");
    }

    public static void configureClient(SSLSocket socket, ServerAuthentication authentication)
            throws TlsTransportException {
        socket.setUseClientMode(true);
        SSLParameters parameters = socket.getSSLParameters();
        applyCommon(parameters, socket.getSupportedCipherSuites());
        parameters.setEndpointIdentificationAlgorithm(
                authentication == ServerAuthentication.PUBLIC_DNS ? "HTTPS" : null);
        socket.setSSLParameters(parameters);
    }

    public static void configureServer(SSLServerSocket socket) throws TlsTransportException {
        socket.setUseClientMode(false);
        socket.setNeedClientAuth(false);
        SSLParameters parameters = socket.getSSLParameters();
        applyCommon(parameters, socket.getSupportedCipherSuites());
        socket.setSSLParameters(parameters);
    }

    public static void configureAcceptedServerSocket(SSLSocket socket)
            throws TlsTransportException {
        socket.setUseClientMode(false);
        socket.setNeedClientAuth(false);
        SSLParameters parameters = socket.getSSLParameters();
        applyCommon(parameters, socket.getSupportedCipherSuites());
        socket.setSSLParameters(parameters);
    }

    public static void verifyNegotiated(SSLSocket socket) throws TlsTransportException {
        SSLSession session = socket.getSession();
        if (!PROTOCOL.equals(session.getProtocol())) {
            throw new TlsTransportException(
                    TlsTransportException.Code.NEGOTIATED_PROTOCOL_REJECTED,
                    "the secure channel did not negotiate TLS 1.3");
        }
        if (!ALLOWED_CIPHERS.contains(session.getCipherSuite())) {
            throw new TlsTransportException(
                    TlsTransportException.Code.NEGOTIATED_CIPHER_REJECTED,
                    "the secure channel negotiated a disallowed cipher suite");
        }
        if (!APPLICATION_PROTOCOL.equals(socket.getApplicationProtocol())) {
            throw new TlsTransportException(
                    TlsTransportException.Code.NEGOTIATED_APPLICATION_PROTOCOL_REJECTED,
                    "the secure channel did not negotiate the Sunderfront ALPN protocol");
        }
    }

    private static void applyCommon(SSLParameters parameters, String[] supportedCipherSuites)
            throws TlsTransportException {
        List<String> supported = Arrays.asList(supportedCipherSuites);
        List<String> enabled = new ArrayList<>(CIPHER_PREFERENCE.size());
        for (String preferred : CIPHER_PREFERENCE) {
            if (supported.contains(preferred)) {
                enabled.add(preferred);
            }
        }
        if (enabled.isEmpty()) {
            throw new TlsTransportException(
                    TlsTransportException.Code.NO_ALLOWED_CIPHER_SUITE,
                    "the JSSE provider exposes no allowed TLS 1.3 cipher suite");
        }
        parameters.setProtocols(new String[] {PROTOCOL});
        parameters.setCipherSuites(enabled.toArray(String[]::new));
        parameters.setApplicationProtocols(new String[] {APPLICATION_PROTOCOL});
    }

    public enum ServerAuthentication {
        PINNED_IDENTITY,
        PUBLIC_DNS
    }
}
