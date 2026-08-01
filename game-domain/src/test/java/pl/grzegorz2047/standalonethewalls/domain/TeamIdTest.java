package pl.grzegorz2047.standalonethewalls.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TeamIdTest {
    @Test
    void exposesExactlyTheFourCanonicalTeams() {
        assertThat(TeamId.values())
                .containsExactly(TeamId.GREEN, TeamId.BLUE, TeamId.RED, TeamId.YELLOW);
    }
}
