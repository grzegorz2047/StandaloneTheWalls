package pl.grzegorz2047.standalonethewalls.protocol.lobby;

import java.util.Objects;

/** Confirmation that the server transferred the authenticated session into lobby ownership. */
public record LobbyJoined(long revision, LobbyMember self) {
    public LobbyJoined {
        if (revision < 1L) {
            throw new IllegalArgumentException("revision must be positive");
        }
        Objects.requireNonNull(self, "self");
    }
}
