package pl.grzegorz2047.standalonethewalls.protocol.lobby;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Complete bounded membership view at one monotonic lobby revision. */
public record LobbySnapshot(long revision, List<LobbyMember> members) {
    public static final int MAXIMUM_MEMBERS = 40;

    public LobbySnapshot {
        if (revision < 1L) {
            throw new IllegalArgumentException("revision must be positive");
        }
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        if (members.size() > MAXIMUM_MEMBERS) {
            throw new IllegalArgumentException("members exceed the supported lobby capacity");
        }
        Set<PlayerId> playerIds = new HashSet<>();
        PlayerId previous = null;
        for (LobbyMember member : members) {
            LobbyMember current = Objects.requireNonNull(member, "member");
            if (!playerIds.add(current.playerId())) {
                throw new IllegalArgumentException("members contain a duplicate playerId");
            }
            if (previous != null
                    && previous.value().compareTo(current.playerId().value()) >= 0) {
                throw new IllegalArgumentException("members must be strictly sorted by playerId");
            }
            previous = current.playerId();
        }
    }
}
