package pl.grzegorz2047.standalonethewalls.client.network;

import java.util.Objects;
import java.util.Optional;

/** Immediate bounded result of attempting to submit one lobby command. */
public record LobbyCommandSubmission(
        LobbyCommandSubmissionStatus status, Optional<LobbyCommandHandle> handle) {
    public LobbyCommandSubmission {
        Objects.requireNonNull(status, "status");
        handle = Objects.requireNonNull(handle, "handle");
        if ((status == LobbyCommandSubmissionStatus.SUBMITTED) != handle.isPresent()) {
            throw new IllegalArgumentException(
                    "only a submitted lobby command may contain a command handle");
        }
    }

    public static LobbyCommandSubmission submitted(LobbyCommandHandle handle) {
        return new LobbyCommandSubmission(
                LobbyCommandSubmissionStatus.SUBMITTED,
                Optional.of(Objects.requireNonNull(handle, "handle")));
    }

    public static LobbyCommandSubmission rejected(LobbyCommandSubmissionStatus status) {
        LobbyCommandSubmissionStatus rejection = Objects.requireNonNull(status, "status");
        if (rejection == LobbyCommandSubmissionStatus.SUBMITTED) {
            throw new IllegalArgumentException("submitted status requires a command handle");
        }
        return new LobbyCommandSubmission(rejection, Optional.empty());
    }
}
