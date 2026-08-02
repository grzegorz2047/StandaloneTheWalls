package pl.grzegorz2047.standalonethewalls.protocol;

/** Bounded asynchronous reliable-channel failure that never includes payload bytes. */
public final class ReliableChannelException extends Exception {
    private static final long serialVersionUID = 1L;

    private final Code code;

    public ReliableChannelException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public ReliableChannelException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        CLOSED,
        FAILED,
        SEND_LIMIT_EXCEEDED,
        RECEIVE_IN_PROGRESS,
        EXECUTOR_REJECTED,
        CLOSE_TIMEOUT
    }
}
