package pl.grzegorz2047.standalonethewalls.domain.lobby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;

class LobbyRosterValueTest {
    @Test
    void exposesTheFourTeamFortyPlayerStandardConfiguration() {
        LobbyConfiguration configuration = LobbyConfiguration.standard();

        assertThat(configuration.enabledTeamsInOrder())
                .containsExactly(TeamId.GREEN, TeamId.BLUE, TeamId.RED, TeamId.YELLOW);
        assertThat(configuration.maximumPlayers()).isEqualTo(40);
        assertThat(configuration.maximumTeamSize()).isEqualTo(10);
        assertThat(configuration.minimumReadyPlayers()).isEqualTo(2);
        assertThatThrownBy(() -> configuration.enabledTeams().remove(TeamId.GREEN))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsConfigurationsWithoutEnoughTeamsOrCapacity() {
        assertThatThrownBy(() -> new LobbyConfiguration(Set.of(TeamId.GREEN), 2, 2, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two teams");
        assertThatThrownBy(() -> new LobbyConfiguration(Set.of(TeamId.GREEN, TeamId.BLUE), 5, 2, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot hold");
        assertThatThrownBy(() -> new LobbyConfiguration(EnumSet.allOf(TeamId.class), 40, 10, 41))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minimumReadyPlayers");
    }

    @Test
    void validatesParticipantIdentifiersAndReadyInvariant() {
        assertThatThrownBy(() -> new LobbyParticipantId(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LobbyParticipantId("contains space"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("visible canonical ASCII");
        assertThatThrownBy(
                        () ->
                                new LobbyParticipantId(
                                        "x".repeat(LobbyParticipantId.MAXIMUM_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new LobbyParticipantState(
                                        new LobbyParticipantId("player"), Optional.empty(), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must have a team");
    }

    @Test
    void reportsTeamSizesAndParticipantLookupFromImmutableState() {
        LobbyParticipantId alice = new LobbyParticipantId("alice");
        LobbyParticipantId bob = new LobbyParticipantId("bob");
        LobbyRosterState state =
                new LobbyRosterState(
                        4L,
                        List.of(
                                new LobbyParticipantState(alice, Optional.of(TeamId.GREEN), true),
                                new LobbyParticipantState(bob, Optional.of(TeamId.BLUE), false)));

        assertThat(state.participant(alice)).isPresent();
        assertThat(state.participant(new LobbyParticipantId("carol"))).isEmpty();
        assertThat(state.teamSize(TeamId.GREEN)).isEqualTo(1);
        assertThat(state.teamSize(TeamId.RED)).isZero();
        assertThat(state.readyCount()).isEqualTo(1);
        assertThatThrownBy(() -> state.participants().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
