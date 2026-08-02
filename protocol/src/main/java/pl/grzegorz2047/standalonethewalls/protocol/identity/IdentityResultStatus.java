package pl.grzegorz2047.standalonethewalls.protocol.identity;

import java.util.Optional;

/** Stable public identity-exchange result catalog. */
public enum IdentityResultStatus {
    ACCEPTED(1, "accepted"),
    UNSUPPORTED_VERSION(2, "unsupported_version"),
    INVALID_PUBLIC_KEY(3, "invalid_public_key"),
    PLAYER_ID_MISMATCH(4, "player_id_mismatch"),
    INVALID_SIGNATURE(5, "invalid_signature"),
    MISSING_CHALLENGE(6, "missing_challenge"),
    EXPIRED_CHALLENGE(7, "expired_challenge"),
    CRYPTOGRAPHY_FAILURE(8, "cryptography_failure"),
    MALFORMED_PROOF(9, "malformed_proof"),
    UNEXPECTED_MESSAGE(10, "unexpected_message"),
    INTERNAL_ERROR(11, "internal_error");

    private final int wireId;
    private final String publicCode;

    IdentityResultStatus(int wireId, String publicCode) {
        this.wireId = wireId;
        this.publicCode = publicCode;
    }

    public int wireId() {
        return wireId;
    }

    public String publicCode() {
        return publicCode;
    }

    public boolean isAccepted() {
        return this == ACCEPTED;
    }

    public static IdentityResultStatus fromVerification(IdentityVerification.Status status) {
        return switch (status) {
            case ACCEPTED -> ACCEPTED;
            case UNSUPPORTED_VERSION -> UNSUPPORTED_VERSION;
            case INVALID_PUBLIC_KEY -> INVALID_PUBLIC_KEY;
            case PLAYER_ID_MISMATCH -> PLAYER_ID_MISMATCH;
            case INVALID_SIGNATURE -> INVALID_SIGNATURE;
            case MISSING_CHALLENGE -> MISSING_CHALLENGE;
            case EXPIRED_CHALLENGE -> EXPIRED_CHALLENGE;
            case CRYPTOGRAPHY_FAILURE -> CRYPTOGRAPHY_FAILURE;
        };
    }

    public static Optional<IdentityResultStatus> fromWireId(int wireId) {
        for (IdentityResultStatus status : values()) {
            if (status.wireId == wireId) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }
}
