package pl.grzegorz2047.standalonethewalls.protocol.identity;

import java.security.KeyPair;
import java.util.Optional;

/** Adapter boundary for an OS keychain or encrypted local storage implementation. */
public interface PlayerIdentityStore {
    Optional<KeyPair> load() throws IdentityException;

    void save(KeyPair keyPair) throws IdentityException;
}
