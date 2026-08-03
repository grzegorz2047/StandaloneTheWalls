package pl.grzegorz2047.standalonethewalls.domain.lobby;

/** Stable expected domain rejections that leave the authoritative roster unchanged. */
public enum LobbyRosterRejection {
    LOBBY_FULL,
    DUPLICATE_PARTICIPANT,
    UNKNOWN_PARTICIPANT,
    TEAM_DISABLED,
    TEAM_FULL,
    TEAM_IMBALANCE,
    TEAM_REQUIRED
}
