package pl.grzegorz2047.standalonethewalls.server.administration.identity;

/** Stable top-level outcomes returned to a console or remote administration adapter. */
public enum IdentityAdministrationResponseCode {
    PERMISSION_DENIED,
    HANDLES_LISTED,
    BANS_LISTED,
    HANDLE_INSPECTED,
    BAN_INSPECTED,
    HANDLE_MUTATION_COMPLETED,
    BAN_MUTATION_COMPLETED,
    REGISTRY_OPERATION_COMPLETED
}
