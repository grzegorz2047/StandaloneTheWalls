package pl.grzegorz2047.standalonethewalls.domain.match;

/** Ordered phases of a Sunderfront match lifecycle. */
public enum MatchPhase {
    BOOT,
    LOADING_MAP,
    WAITING_FOR_PLAYERS,
    START_COUNTDOWN,
    PREPARATION,
    WALLS_OPENING,
    OPEN_COMBAT,
    DEATHMATCH_TRANSITION,
    DEATHMATCH,
    RESULTS,
    RESETTING
}
