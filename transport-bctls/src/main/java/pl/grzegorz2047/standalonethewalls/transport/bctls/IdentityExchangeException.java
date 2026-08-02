package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.util.Objects;
import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityResultStatus;

/** Safe terminal failure for one bounded identity exchange. */
public final class IdentityExchangeException extends Exception {
    private final Code code;
    private final Optional<IdentityResultStatus> resultStatus;

    public IdentityExchangeException(Code code, String message) {
        this(code, message, null, Optional.empty());
    }

    public IdentityExchangeException(Code code, String message, Throwable cause) {
        this(code, message, cause, Optional.empty());
    }

    public IdentityExchangeException(Code code, String message, IdentityResultStatus resultStatus) {
        this(
                code,
                message,
                null,
                Optional.of(Objects.requireNonNull(resultStatus, "resultStatus")));
    }

    private IdentityExchangeException(
            Code code,
            String message,
            Throwable cause,
            Optional<IdentityResultStatus> resultStatus) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
        this.resultStatus = Objects.requireNonNull(resultStatus, "resultStatus");
    }

    public Code code() {
        return code;
    }

    public Optional<IdentityResultStatus> resultStatus() {
        return resultStatus;
    }

    public enum Code {
        EXCHANGE_ALREADY_STARTED,
        TIMEOUT,
        CLEAN_EOF,
        UNEXPECTED_MESSAGE,
        MALFORMED_PAYLOAD,
        REJECTED,
        EXPIRED_CHALLENGE,
        SIGNING_FAILED,
        CHANNEL_FAILURE,
        CLOSE_FAILURE,
        POST_AUTH_IDENTITY_MESSAGE,
        INTERNAL_ERROR
    }
}
