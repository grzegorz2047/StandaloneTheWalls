package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerReference;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustRecord;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustStore;

final class InMemoryServerTrustStore implements ServerTrustStore {
    private final Map<ServerReference, ServerTrustRecord> records = new ConcurrentHashMap<>();

    @Override
    public Optional<ServerTrustRecord> find(ServerReference reference) {
        return Optional.ofNullable(records.get(reference));
    }

    @Override
    public boolean saveIfAbsent(ServerTrustRecord record) {
        return records.putIfAbsent(record.reference(), record) == null;
    }

    @Override
    public boolean replace(ServerTrustRecord expected, ServerTrustRecord replacement) {
        return records.replace(expected.reference(), expected, replacement);
    }
}
