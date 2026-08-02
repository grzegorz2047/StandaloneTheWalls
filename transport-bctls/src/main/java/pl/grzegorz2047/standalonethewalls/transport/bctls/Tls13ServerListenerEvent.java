package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.net.SocketAddress;
import java.util.Objects;
import java.util.Optional;

/** Bounded listener event. Its string form deliberately excludes failure messages and secrets. */
public final class Tls13ServerListenerEvent {
    private final Code code;
    private final SocketAddress remoteAddress;
    private final Throwable failure;

    private Tls13ServerListenerEvent(
            Code code, SocketAddress remoteAddress, Throwable failure) {
        this.code = Objects.requireNonNull(code, "code");
        this.remoteAddress = remoteAddress;
        this.failure = failure;
    }

    public static Tls13ServerListenerEvent rejected(Code code, SocketAddress remoteAddress) {
        if (code != Code.ACTIVE_CONNECTION_LIMIT
                && code != Code.CONCURRENT_HANDSHAKE_LIMIT
                && code != Code.HANDSHAKE_EXECUTOR_REJECTED) {
            throw new IllegalArgumentException("code is not an admission rejection");
        }
        return new Tls13ServerListenerEvent(code, remoteAddress, null);
    }

    public static Tls13ServerListenerEvent failed(
            Code code, SocketAddress remoteAddress, Throwable failure) {
        if (code == Code.ACTIVE_CONNECTION_LIMIT || code == Code.CONCURRENT_HANDSHAKE_LIMIT) {
            throw new IllegalArgumentException("limit events do not carry failures");
        }
        return new Tls13ServerListenerEvent(
                code,
                remoteAddress,
                Objects.requireNonNull(failure, "failure"));
    }

    public Code code() {
        return code;
    }

    public Optional<SocketAddress> remoteAddress() {
        return Optional.ofNullable(remoteAddress);
    }

    public Optional<Throwable> failure() {
        return Optional.ofNullable(failure);
    }

    @Override
    public String toString() {
        return "Tls13ServerListenerEvent[code=" + code
                + ", remoteAddressPresent=" + (remoteAddress != null)
                + ", failureType=" + (failure == null ? "none" : failure.getClass().getName())
                + ']';
    }

    public enum Code {
        ACTIVE_CONNECTION_LIMIT,
        CONCURRENT_HANDSHAKE_LIMIT,
        HANDSHAKE_EXECUTOR_REJECTED,
        HANDSHAKE_FAILED,
        HANDLER_FAILED,
        ACCEPT_LOOP_FAILED,
        SHUTDOWN_FAILED
    }
}
