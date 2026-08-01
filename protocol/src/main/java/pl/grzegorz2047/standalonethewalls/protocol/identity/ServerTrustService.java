package pl.grzegorz2047.standalonethewalls.protocol.identity;

import java.util.Objects;
import java.util.Optional;

/** Deterministic TOFU and expected-pin policy with explicit persistence operations. */
public final class ServerTrustService {
    private final ServerTrustStore store;

    public ServerTrustService(ServerTrustStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public ServerTrustDecision inspect(
            ServerReference reference, ServerId presented, Optional<ServerId> expectedPin)
            throws ServerTrustStoreException {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(presented, "presented");
        Objects.requireNonNull(expectedPin, "expectedPin");

        Optional<ServerTrustRecord> existing = store.find(reference);
        if (expectedPin.isPresent()) {
            return new ServerTrustDecision(
                    expectedPin.orElseThrow().equals(presented)
                            ? ServerTrustDecision.Status.TRUSTED
                            : ServerTrustDecision.Status.EXPECTED_PIN_MISMATCH,
                    existing);
        }
        if (existing.isEmpty()) {
            return new ServerTrustDecision(
                    ServerTrustDecision.Status.FIRST_USE_REQUIRES_CONFIRMATION,
                    Optional.empty());
        }
        return new ServerTrustDecision(
                existing.orElseThrow().serverId().equals(presented)
                        ? ServerTrustDecision.Status.TRUSTED
                        : ServerTrustDecision.Status.CHANGED_IDENTITY,
                existing);
    }

    public ServerTrustRecord confirmFirstUse(
            ServerReference reference,
            ServerId presented,
            Optional<ServerId> expectedPin,
            String reason)
            throws ServerTrustStoreException {
        ServerTrustDecision decision = inspect(reference, presented, expectedPin);
        if (expectedPin.isPresent()) {
            throw new IllegalStateException("expected pins cannot be persisted as TOFU");
        }
        if (decision.status() != ServerTrustDecision.Status.FIRST_USE_REQUIRES_CONFIRMATION) {
            throw new IllegalStateException(
                    "server reference is not awaiting first-use confirmation");
        }
        ServerTrustRecord record =
                new ServerTrustRecord(
                        reference, presented, ServerTrustRecord.Source.TOFU, reason);
        if (!store.saveIfAbsent(record)) {
            throw new IllegalStateException(
                    "server trust changed during first-use confirmation");
        }
        return record;
    }

    public ServerTrustRecord replace(
            ServerTrustRecord expectedCurrent, ServerId replacement, String reason)
            throws ServerTrustStoreException {
        Objects.requireNonNull(expectedCurrent, "expectedCurrent");
        Objects.requireNonNull(replacement, "replacement");
        ServerTrustRecord replacementRecord =
                new ServerTrustRecord(
                        expectedCurrent.reference(),
                        replacement,
                        ServerTrustRecord.Source.EXPLICIT_REPLACEMENT,
                        reason);
        if (!store.replace(expectedCurrent, replacementRecord)) {
            throw new IllegalStateException(
                    "server trust changed before explicit replacement");
        }
        return replacementRecord;
    }
}
