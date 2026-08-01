package pl.grzegorz2047.standalonethewalls.protocol;

import java.util.UUID;

/** One logical authenticated session exposing both transport classes. */
public interface TransportSession {
    UUID sessionId();

    ReliableChannel reliableChannel();

    RealtimeChannel realtimeChannel();

    boolean isOpen();

    void close();
}
