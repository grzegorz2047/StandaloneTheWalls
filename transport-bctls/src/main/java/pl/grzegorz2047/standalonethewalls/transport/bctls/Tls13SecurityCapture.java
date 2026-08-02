package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.io.IOException;
import org.bouncycastle.tls.AlertDescription;
import org.bouncycastle.tls.ChannelBinding;
import org.bouncycastle.tls.ProtocolName;
import org.bouncycastle.tls.ProtocolVersion;
import org.bouncycastle.tls.SecurityParameters;
import org.bouncycastle.tls.TlsContext;
import org.bouncycastle.tls.TlsFatalAlert;
import pl.grzegorz2047.standalonethewalls.protocol.identity.SecureChannelBinding;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;

/** Captures immutable public session metadata while exporter material is still available. */
final class Tls13SecurityCapture {
    private Tls13SecurityCapture() {
        throw new AssertionError("No instances");
    }

    static Tls13SessionSecurity capture(TlsContext context, ServerId serverId) throws IOException {
        SecurityParameters parameters = context.getSecurityParametersConnection();
        if (!ProtocolVersion.TLSv13.equals(parameters.getNegotiatedVersion())) {
            throw new TlsFatalAlert(
                    AlertDescription.protocol_version, "Sunderfront requires TLS 1.3");
        }

        int cipherSuite = parameters.getCipherSuite();
        if (!Tls13ProtocolPolicy.isAllowedCipherSuite(cipherSuite)) {
            throw new TlsFatalAlert(
                    AlertDescription.handshake_failure,
                    "the negotiated TLS cipher suite is not allowed");
        }

        ProtocolName applicationProtocol = parameters.getApplicationProtocol();
        if (!Tls13ProtocolPolicy.APPLICATION_PROTOCOL_NAME.equals(applicationProtocol)) {
            throw new TlsFatalAlert(
                    AlertDescription.no_application_protocol,
                    "the Sunderfront ALPN protocol was not negotiated");
        }

        byte[] exporter = context.exportChannelBinding(ChannelBinding.tls_exporter);
        if (exporter == null || exporter.length != SecureChannelBinding.BYTES) {
            throw new TlsFatalAlert(
                    AlertDescription.internal_error,
                    "the TLS exporter channel binding is unavailable or invalid");
        }

        return new Tls13SessionSecurity(
                serverId,
                new SecureChannelBinding(exporter),
                Tls13ProtocolPolicy.cipherSuiteName(cipherSuite),
                applicationProtocol.getUtf8Decoding());
    }
}
