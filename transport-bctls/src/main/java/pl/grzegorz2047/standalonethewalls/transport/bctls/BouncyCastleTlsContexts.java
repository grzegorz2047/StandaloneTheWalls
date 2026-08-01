package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.SecureRandom;
import java.util.Objects;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;

/** Creates isolated BCJSSE contexts without mutating the process-wide provider list. */
public final class BouncyCastleTlsContexts {
    private final Provider cryptoProvider;
    private final Provider jsseProvider;

    public BouncyCastleTlsContexts() {
        this(new BouncyCastleProvider());
    }

    BouncyCastleTlsContexts(Provider cryptoProvider) {
        this.cryptoProvider = Objects.requireNonNull(cryptoProvider, "cryptoProvider");
        this.jsseProvider = new BouncyCastleJsseProvider(cryptoProvider);
    }

    public SSLContext create(
            KeyManager[] keyManagers, TrustManager[] trustManagers, SecureRandom secureRandom)
            throws TlsTransportException {
        Objects.requireNonNull(secureRandom, "secureRandom");
        try {
            SSLContext context = SSLContext.getInstance("TLS", jsseProvider);
            context.init(keyManagers, trustManagers, secureRandom);
            return context;
        } catch (GeneralSecurityException exception) {
            throw new TlsTransportException(
                    TlsTransportException.Code.CONTEXT_INITIALIZATION_FAILED,
                    "unable to initialize the BCJSSE TLS context",
                    exception);
        }
    }

    Provider cryptoProvider() {
        return cryptoProvider;
    }

    Provider jsseProvider() {
        return jsseProvider;
    }
}
