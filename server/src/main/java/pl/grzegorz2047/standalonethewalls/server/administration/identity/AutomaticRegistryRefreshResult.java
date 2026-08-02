package pl.grzegorz2047.standalonethewalls.server.administration.identity;

/** Bounded semantic outcome used by the automatic registry retry policy. */
public enum AutomaticRegistryRefreshResult {
    ACTIVATED(true),
    UNCHANGED(true),
    PROVIDER_FAILURE(false),
    SNAPSHOT_REJECTED(false),
    ROLLBACK_REJECTED(false),
    EQUIVOCATION_REJECTED(false),
    CACHE_FAILURE(false),
    INTERNAL_FAILURE(false);

    private final boolean successful;

    AutomaticRegistryRefreshResult(boolean successful) {
        this.successful = successful;
    }

    public boolean successful() {
        return successful;
    }
}
