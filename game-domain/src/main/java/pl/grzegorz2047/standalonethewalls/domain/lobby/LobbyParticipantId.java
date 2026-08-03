package pl.grzegorz2047.standalonethewalls.domain.lobby;

import java.util.Objects;

/** Stable renderer- and transport-independent identity used by the lobby roster domain. */
public record LobbyParticipantId(String value) implements Comparable<LobbyParticipantId> {
    public static final int MAXIMUM_LENGTH = 128;

    public LobbyParticipantId {
        Objects.requireNonNull(value, "value");
        if (value.isEmpty() || value.length() > MAXIMUM_LENGTH) {
            throw new IllegalArgumentException(
                    "lobby participant id length is outside the accepted range");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 0x21 || character > 0x7e) {
                throw new IllegalArgumentException(
                        "lobby participant id must use visible canonical ASCII");
            }
        }
    }

    @Override
    public int compareTo(LobbyParticipantId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }
}
