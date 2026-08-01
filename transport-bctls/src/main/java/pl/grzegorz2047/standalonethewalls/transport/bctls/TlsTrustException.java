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
    private final ServerReference reference;
    private final ServerId presentedServerId;
    private final ServerFingerprint fingerprint;

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
        this.reference = reference;
        this.presentedServerId = presentedServerId;
        this.fingerprint = fingerprint;
    }

    public ServerTrustDecision.Status status() {
        return status;
    }

    public ServerReference reference() {
        return reference;
    }

    public ServerId presentedServerId() {
        return presentedServerId;
    }

    public ServerFingerprint fingerprint() {
        return fingerprint;
    }
}
