package pl.grzegorz2047.standalonethewalls.client.network;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCommandResult;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;

/** Public terminal resolution for one submitted lobby command without raw exception details. */
public sealed interface LobbyCommandResolution
        permits LobbyCommandResolution.Completed, LobbyCommandResolution.Failed {

    record Completed(LobbyCommandResult result, LobbySnapshot snapshot)
            implements LobbyCommandResolution {
        public Completed {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(snapshot, "snapshot");
            if (result.revision() != snapshot.revision()) {
                throw new IllegalArgumentException(
                        "command result and snapshot must have the same revision");
            }
        }
    }

    record Failed(DirectConnectFailure failure) implements LobbyCommandResolution {
        public Failed {
            Objects.requireNonNull(failure, "failure");
        }
    }
}
