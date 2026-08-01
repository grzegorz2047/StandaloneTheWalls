package pl.grzegorz2047.standalonethewalls.protocol.identity;

import java.util.Optional;

/** Adapter boundary for persistent TOFU and explicit replacement records. */
public interface ServerTrustStore {
    Optional<ServerTrustRecord> find(ServerReference reference) throws ServerTrustStoreException;

    boolean saveIfAbsent(ServerTrustRecord record) throws ServerTrustStoreException;

    boolean replace(ServerTrustRecord expected, ServerTrustRecord replacement)
            throws ServerTrustStoreException;
}
