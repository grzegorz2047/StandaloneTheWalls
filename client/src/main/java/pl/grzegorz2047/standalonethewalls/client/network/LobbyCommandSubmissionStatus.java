package pl.grzegorz2047.standalonethewalls.client.network;

/** Immediate local decision for attempting to submit one lobby command. */
public enum LobbyCommandSubmissionStatus {
    SUBMITTED,
    SESSION_CLOSED,
    COMMAND_IN_FLIGHT
}
