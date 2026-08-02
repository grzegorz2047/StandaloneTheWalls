package pl.grzegorz2047.standalonethewalls.server.administration.identity;

/** Stable outcomes for local registry verification and reload operations. */
public enum RegistryAdministrationResultCode {
    VERIFIED,
    ACTIVATED,
    UNCHANGED,
    PROVIDER_FAILURE,
    SNAPSHOT_REJECTED
}
