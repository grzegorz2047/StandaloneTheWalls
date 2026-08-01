package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.security.cert.CertificateException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerFingerprint;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerReference;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustDecision;

/** Certificate rejection carrying only public, bounded trust-decision metadata. */
public final class TlsTrustException extends CertificateException {
    private static final long serialVersionUID = 1L;

    private final ServerTrustDecision.Status status;
    private final String referenceValue;
    private final String presentedServerIdValue;
    private final String fingerprintValue;

    TlsTrustException(
            ServerTrustDecision.Status status,
            ServerReference reference,
            ServerId presentedServerId,
            ServerFingerprint fingerprint) {
        super(
                "server trust rejected: status="
                        + status
                        + ", reference="
                        + reference.value()
                        + ", fingerprint="
                        + fingerprint.value());
        this.status = status;
        this.referenceValue = reference.value();
        this.presentedServerIdValue = presentedServerId.value();
        this.fingerprintValue = fingerprint.value();
    }

    public ServerTrustDecision.Status status() {
        return status;
    }

    public ServerReference reference() {
        return new ServerReference(referenceValue);
    }

    public ServerId presentedServerId() {
        return new ServerId(presentedServerIdValue);
    }

    public ServerFingerprint fingerprint() {
        return new ServerFingerprint(fingerprintValue);
    }
}
