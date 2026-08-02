package pl.grzegorz2047.standalonethewalls.identity.policy;

import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Adapter boundary for one atomic local handle-to-player binding operation. */
@FunctionalInterface
public interface LocalHandleBindingStore {
    LocalHandleBindingResult bindOrVerify(CanonicalHandle handle, PlayerId playerId);
}
