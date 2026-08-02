package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.util.Set;
import java.util.Vector;
import org.bouncycastle.tls.CipherSuite;
import org.bouncycastle.tls.ProtocolName;
import org.bouncycastle.tls.ProtocolVersion;

/** Shared TLS 1.3, ALPN, and cipher-suite policy for low-level Bouncy Castle peers. */
final class Tls13ProtocolPolicy {
    static final String APPLICATION_PROTOCOL = "sunderfront/1";
    static final ProtocolName APPLICATION_PROTOCOL_NAME =
            ProtocolName.asUtf8Encoding(APPLICATION_PROTOCOL);

    private static final int[] CIPHER_SUITES = {
        CipherSuite.TLS_AES_128_GCM_SHA256,
        CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
        CipherSuite.TLS_AES_256_GCM_SHA384
    };
    private static final Set<Integer> ALLOWED_CIPHER_SUITES =
            Set.of(
                    CipherSuite.TLS_AES_128_GCM_SHA256,
                    CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
                    CipherSuite.TLS_AES_256_GCM_SHA384);

    private Tls13ProtocolPolicy() {
        throw new AssertionError("No instances");
    }

    static ProtocolVersion[] supportedVersions() {
        return ProtocolVersion.TLSv13.only();
    }

    static int[] supportedCipherSuites() {
        return CIPHER_SUITES.clone();
    }

    static Vector<ProtocolName> protocolNames() {
        Vector<ProtocolName> names = new Vector<>(1);
        names.add(APPLICATION_PROTOCOL_NAME);
        return names;
    }

    static boolean isAllowedCipherSuite(int cipherSuite) {
        return ALLOWED_CIPHER_SUITES.contains(cipherSuite);
    }

    static String cipherSuiteName(int cipherSuite) {
        return switch (cipherSuite) {
            case CipherSuite.TLS_AES_128_GCM_SHA256 -> "TLS_AES_128_GCM_SHA256";
            case CipherSuite.TLS_CHACHA20_POLY1305_SHA256 -> "TLS_CHACHA20_POLY1305_SHA256";
            case CipherSuite.TLS_AES_256_GCM_SHA384 -> "TLS_AES_256_GCM_SHA384";
            default -> "UNKNOWN_0x" + Integer.toHexString(cipherSuite);
        };
    }
}
