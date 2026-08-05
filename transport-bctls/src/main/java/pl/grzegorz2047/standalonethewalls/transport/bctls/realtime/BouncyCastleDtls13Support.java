package pl.grzegorz2047.standalonethewalls.transport.bctls.realtime;

import java.security.Provider;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.transport.bctls.BouncyCastleTlsCryptoFactory;

/** Fail-closed capability gate for the pinned Bouncy Castle low-level DTLS provider. */
public final class BouncyCastleDtls13Support {
    public static final String REVIEWED_PROVIDER_VERSION = "1.84";
    private static final String PROVIDER_ID =
            "org.bouncycastle:bcprov-jdk18on+org.bouncycastle:bctls-jdk18on";

    private BouncyCastleDtls13Support() {
        throw new AssertionError("No instances");
    }

    public static RealtimeTransportCapability current() {
        Provider provider = BouncyCastleTlsCryptoFactory.provider();
        return evaluate(provider.getVersionStr());
    }

    static RealtimeTransportCapability evaluate(String providerVersion) {
        String version = Objects.requireNonNull(providerVersion, "providerVersion");
        RealtimeTransportCapability.Reason reason =
                REVIEWED_PROVIDER_VERSION.equals(version)
                        ? RealtimeTransportCapability.Reason.DTLS_1_3_NOT_IMPLEMENTED
                        : RealtimeTransportCapability.Reason.PROVIDER_VERSION_REQUIRES_REVIEW;
        return RealtimeTransportCapability.unavailable(PROVIDER_ID, version, reason);
    }
}
