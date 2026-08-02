package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.io.IOException;
import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolException;

/** Package boundary used to test asynchronous ownership without replacing TLS production code. */
interface ReliableEnvelopeStream {
    long send(MessageType messageType, byte[] payload) throws IOException, ProtocolException;

    Optional<ProtocolEnvelope> receive() throws IOException, ProtocolException;

    boolean isOpen();

    void close() throws IOException;
}
