package pl.grzegorz2047.standalonethewalls.registry;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** In-memory atomic activation that preserves the last valid snapshot on every failure. */
public final class AtomicRegistrySnapshotStore {
    private VerifiedRegistrySnapshot active;

    public synchronized RegistryActivationResult activate(VerifiedRegistrySnapshot candidate)
            throws RegistrySnapshotException {
        VerifiedRegistrySnapshot next = Objects.requireNonNull(candidate, "candidate");
        if (active == null) {
            active = next;
            return RegistryActivationResult.ACTIVATED;
        }
        if (next.sequence() < active.sequence()) {
            throw new RegistrySnapshotException(
                    RegistrySnapshotException.Code.ROLLBACK,
                    "registry snapshot sequence would roll back active state");
        }
        if (next.sequence() == active.sequence()) {
            if (MessageDigest.isEqual(next.digest(), active.digest())) {
                return RegistryActivationResult.UNCHANGED;
            }
            throw new RegistrySnapshotException(
                    RegistrySnapshotException.Code.EQUIVOCATION,
                    "registry snapshots with the same sequence have different digests");
        }
        active = next;
        return RegistryActivationResult.ACTIVATED;
    }

    public synchronized Optional<VerifiedRegistrySnapshot> active() {
        return Optional.ofNullable(active);
    }

    public synchronized RegistrySnapshotAvailability availability(
            Clock clock, RegistrySnapshotPolicy policy) {
        Clock timeSource = Objects.requireNonNull(clock, "clock");
        RegistrySnapshotPolicy acceptance = Objects.requireNonNull(policy, "policy");
        if (active == null) {
            return RegistrySnapshotAvailability.absent();
        }
        Duration age = Duration.between(active.generatedAt(), timeSource.instant());
        return age.compareTo(acceptance.maximumAge()) > 0
                ? RegistrySnapshotAvailability.stale(active)
                : RegistrySnapshotAvailability.fresh(active);
    }
}
