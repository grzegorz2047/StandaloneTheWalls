package pl.grzegorz2047.standalonethewalls.registry;

import java.util.Objects;
import java.util.Optional;

/** Immutable runtime view of the last verified registry snapshot and its freshness. */
public record RegistrySnapshotAvailability(
        State state, Optional<VerifiedRegistrySnapshot> snapshot) {
    public enum State {
        ABSENT,
        FRESH,
        STALE
    }

    public RegistrySnapshotAvailability {
        state = Objects.requireNonNull(state, "state");
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
        if (state == State.ABSENT && snapshot.isPresent()) {
            throw new IllegalArgumentException("absent registry availability cannot contain a snapshot");
        }
        if (state != State.ABSENT && snapshot.isEmpty()) {
            throw new IllegalArgumentException("present registry availability requires a snapshot");
        }
    }

    public static RegistrySnapshotAvailability absent() {
        return new RegistrySnapshotAvailability(State.ABSENT, Optional.empty());
    }

    public static RegistrySnapshotAvailability fresh(VerifiedRegistrySnapshot snapshot) {
        return new RegistrySnapshotAvailability(
                State.FRESH, Optional.of(Objects.requireNonNull(snapshot, "snapshot")));
    }

    public static RegistrySnapshotAvailability stale(VerifiedRegistrySnapshot snapshot) {
        return new RegistrySnapshotAvailability(
                State.STALE, Optional.of(Objects.requireNonNull(snapshot, "snapshot")));
    }

    public VerifiedRegistrySnapshot requireSnapshot() {
        return snapshot.orElseThrow(
                () -> new IllegalStateException("registry availability does not contain a snapshot"));
    }
}
