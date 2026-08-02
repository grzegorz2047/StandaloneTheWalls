package pl.grzegorz2047.standalonethewalls.identity.policy;

/** Server policy for assigning canonical handles to authenticated player identities. */
public enum HandleAuthorizationMode {
    LOCAL_TOFU,
    GLOBAL_ONLY,
    HYBRID
}
