package pl.grzegorz2047.standalonethewalls.client.preparation;

import java.util.Objects;
import java.util.Optional;

/** Renderer-thread gate that enters one verified preparation scene at most once. */
public final class PreparationTransitionGate {
    private PreparationPlayerState enteredState;

    /**
     * Returns the newly entered state exactly once. Empty input or repeated polling does not
     * transition.
     */
    public Optional<PreparationPlayerState> poll(Optional<VerifiedPreparationScene> candidate) {
        Optional<VerifiedPreparationScene> verified =
                Objects.requireNonNull(candidate, "candidate");
        if (enteredState != null || verified.isEmpty()) {
            return Optional.empty();
        }
        enteredState = PreparationPlayerState.atAuthoritativeSpawn(verified.orElseThrow());
        return Optional.of(enteredState);
    }

    public Optional<PreparationPlayerState> currentState() {
        return Optional.ofNullable(enteredState);
    }
}
