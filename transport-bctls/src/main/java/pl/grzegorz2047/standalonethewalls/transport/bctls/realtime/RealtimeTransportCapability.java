package pl.grzegorz2047.standalonethewalls.transport.bctls.realtime;

import java.util.Objects;

/** Reviewed capability decision for one realtime secure-transport provider. */
public record RealtimeTransportCapability(
        boolean available, String providerId, String providerVersion, Reason reason) {
    private static final int MAXIMUM_PROVIDER_ID_LENGTH = 96;
    private static final int MAXIMUM_PROVIDER_VERSION_LENGTH = 32;

    public RealtimeTransportCapability {
        providerId = requireText(providerId, MAXIMUM_PROVIDER_ID_LENGTH, "providerId");
        providerVersion =
                requireText(providerVersion, MAXIMUM_PROVIDER_VERSION_LENGTH, "providerVersion");
        reason = Objects.requireNonNull(reason, "reason");
        if (available != (reason == Reason.AVAILABLE)) {
            throw new IllegalArgumentException(
                    "available capability must use AVAILABLE and unavailable capability must not");
        }
    }

    public static RealtimeTransportCapability available(String providerId, String providerVersion) {
        return new RealtimeTransportCapability(true, providerId, providerVersion, Reason.AVAILABLE);
    }

    public static RealtimeTransportCapability unavailable(
            String providerId, String providerVersion, Reason reason) {
        Reason unavailableReason = Objects.requireNonNull(reason, "reason");
        if (unavailableReason == Reason.AVAILABLE) {
            throw new IllegalArgumentException("unavailable capability requires a failure reason");
        }
        return new RealtimeTransportCapability(
                false, providerId, providerVersion, unavailableReason);
    }

    @Override
    public String toString() {
        return "RealtimeTransportCapability[available="
                + available
                + ", providerId="
                + providerId
                + ", providerVersion="
                + providerVersion
                + ", reason="
                + reason
                + ']';
    }

    private static String requireText(String value, int maximumLength, String field) {
        String text = Objects.requireNonNull(value, field).strip();
        if (text.isEmpty() || text.length() > maximumLength) {
            throw new IllegalArgumentException(field + " has an invalid length");
        }
        if (text.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " cannot contain control characters");
        }
        return text;
    }

    public enum Reason {
        AVAILABLE,
        DTLS_1_3_NOT_IMPLEMENTED,
        PROVIDER_VERSION_REQUIRES_REVIEW,
        EXPLICITLY_DISABLED
    }
}
