package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.security.SecureRandom;
import java.util.Objects;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.tls.crypto.impl.jcajce.JcaTlsCrypto;
import org.bouncycastle.tls.crypto.impl.jcajce.JcaTlsCryptoProvider;

/** Creates isolated low-level Bouncy Castle TLS cryptography without global provider mutation. */
public final class BouncyCastleTlsCryptoFactory {
    private BouncyCastleTlsCryptoFactory() {
        throw new AssertionError("No instances");
    }

    public static JcaTlsCrypto create(SecureRandom secureRandom) {
        Objects.requireNonNull(secureRandom, "secureRandom");
        return new JcaTlsCryptoProvider()
                .setProvider(new BouncyCastleProvider())
                .create(secureRandom);
    }
}
