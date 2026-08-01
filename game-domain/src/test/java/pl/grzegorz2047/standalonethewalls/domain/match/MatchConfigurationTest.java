package pl.grzegorz2047.standalonethewalls.domain.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class MatchConfigurationTest {
    @Test
    void convertsDocumentedDefaultsToTicks() {
        MatchConfiguration configuration = MatchConfiguration.defaults(20);

        assertThat(configuration.minimumPlayers()).isEqualTo(2);
        assertThat(configuration.startCountdownTicks()).isEqualTo(1_200L);
        assertThat(configuration.preparationTicks()).isEqualTo(12_000L);
        assertThat(configuration.openCombatTicks()).isEqualTo(8_400L);
        assertThat(configuration.deathmatchTicks()).isEqualTo(6_000L);
    }

    @Test
    void rejectsNonPositiveTimingAndPlayerLimits() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MatchConfiguration(0, 1, 1, 1, 1, 1, 1, 1, 1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MatchConfiguration(1, 0, 1, 1, 1, 1, 1, 1, 1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MatchConfiguration.defaults(0));
    }

    @Test
    void onlyTimedPhasesHaveConfiguredDurations() {
        MatchConfiguration configuration =
                new MatchConfiguration(2, 2, 3, 4, 5, 6, 7, 8, 9);

        assertThat(configuration.durationFor(MatchPhase.BOOT)).isZero();
        assertThat(configuration.durationFor(MatchPhase.LOADING_MAP)).isZero();
        assertThat(configuration.durationFor(MatchPhase.WAITING_FOR_PLAYERS)).isZero();
        assertThat(configuration.durationFor(MatchPhase.START_COUNTDOWN)).isEqualTo(2L);
        assertThat(configuration.durationFor(MatchPhase.RESETTING)).isEqualTo(9L);
    }
}
