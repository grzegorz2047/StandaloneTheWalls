package pl.grzegorz2047.standalonethewalls.client.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationPlayerSnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationWorldSnapshot;

class PreparationRemoteSnapshotInterpolatorTest {
    private static final PlayerId LOCAL = playerId('a');
    private static final PlayerId BRAVO = playerId('b');
    private static final PlayerId CHARLIE = playerId('c');

    @Test
    void interpolatesPositionAndHoldsTheNewestAuthoritativeTarget() {
        PreparationRemoteSnapshotInterpolator interpolator = interpolator();
        interpolator.offer(snapshot(10L, player(LOCAL, 0, 0), player(BRAVO, 0, 0)));
        interpolator.offer(snapshot(12L, player(LOCAL, 0, 0), player(BRAVO, 10_000, 9_000)));

        PreparationRemotePlayerPose halfway = only(interpolator.advance(0.05d));
        assertThat(halfway.xMetres()).isCloseTo(5.0d, within(0.000001d));
        assertThat(halfway.yawDegrees()).isCloseTo(45.0d, within(0.000001d));

        PreparationRemotePlayerPose target = only(interpolator.advance(0.05d));
        assertThat(target.xMetres()).isEqualTo(10.0d);
        assertThat(target.yawDegrees()).isEqualTo(90.0d);
        assertThat(interpolator.advance(5.0d)).containsExactly(target);
        assertThat(interpolator.latestAuthoritativeTick()).isEqualTo(12L);
    }

    @Test
    void crossesTheYawBoundaryUsingTheShortestArc() {
        PreparationRemoteSnapshotInterpolator interpolator = interpolator();
        interpolator.offer(snapshot(2L, player(LOCAL, 0, 0), player(BRAVO, 0, 17_900)));
        interpolator.offer(snapshot(4L, player(LOCAL, 0, 0), player(BRAVO, 0, -17_900)));

        PreparationRemotePlayerPose halfway = only(interpolator.advance(0.05d));

        assertThat(halfway.yawDegrees()).isEqualTo(-180.0d);
        assertThat(only(interpolator.advance(0.05d)).yawDegrees()).isEqualTo(-179.0d);
    }

    @Test
    void removesMissingPlayersImmediatelyAndIntroducesNewPlayersAuthoritatively() {
        PreparationRemoteSnapshotInterpolator interpolator = interpolator();
        interpolator.offer(
                snapshot(
                        20L,
                        player(LOCAL, 0, 0),
                        player(BRAVO, 1_000, 0),
                        player(CHARLIE, 2_000, 0)));
        PlayerId delta = playerId('d');

        interpolator.offer(
                snapshot(
                        22L,
                        player(LOCAL, 0, 0),
                        player(CHARLIE, 4_000, 0),
                        player(delta, 8_000, 0)));

        assertThat(interpolator.current())
                .extracting(PreparationRemotePlayerPose::playerId)
                .containsExactly(CHARLIE, delta);
        assertThat(interpolator.current().get(0).xMetres()).isEqualTo(2.0d);
        assertThat(interpolator.current().get(1).xMetres()).isEqualTo(8.0d);
        assertThat(interpolator.advance(0.1d).get(0).xMetres()).isEqualTo(4.0d);
    }

    @Test
    void startsAReplacementTargetFromTheCurrentlyPresentedPose() {
        PreparationRemoteSnapshotInterpolator interpolator = interpolator();
        interpolator.offer(snapshot(2L, player(LOCAL, 0, 0), player(BRAVO, 0, 0)));
        interpolator.offer(snapshot(4L, player(LOCAL, 0, 0), player(BRAVO, 10_000, 0)));
        assertThat(only(interpolator.advance(0.05d)).xMetres()).isEqualTo(5.0d);

        interpolator.offer(snapshot(6L, player(LOCAL, 0, 0), player(BRAVO, 20_000, 0)));

        assertThat(only(interpolator.current()).xMetres()).isEqualTo(5.0d);
        assertThat(only(interpolator.advance(0.05d)).xMetres()).isEqualTo(12.5d);
        assertThat(only(interpolator.advance(0.05d)).xMetres()).isEqualTo(20.0d);
    }

    @Test
    void rejectsWrongRoundsNonIncreasingTicksAndInvalidFrameTime() {
        PreparationRemoteSnapshotInterpolator interpolator = interpolator();
        PreparationWorldSnapshot first = snapshot(8L, player(LOCAL, 0, 0), player(BRAVO, 0, 0));
        interpolator.offer(first);

        assertThrows(
                IllegalArgumentException.class,
                () -> interpolator.offer(new PreparationWorldSnapshot(2L, 10L, first.players())));
        assertThrows(IllegalArgumentException.class, () -> interpolator.offer(first));
        assertThrows(
                IllegalArgumentException.class,
                () -> interpolator.offer(snapshot(7L, player(LOCAL, 0, 0), player(BRAVO, 0, 0))));
        assertThrows(IllegalArgumentException.class, () -> interpolator.advance(-0.01d));
        assertThrows(IllegalArgumentException.class, () -> interpolator.advance(Double.NaN));
    }

    @Test
    void hasAnEmptyBoundedPresentationBeforeTheFirstSnapshot() {
        PreparationRemoteSnapshotInterpolator interpolator = interpolator();

        assertThat(interpolator.current()).isEmpty();
        assertThat(interpolator.advance(0.01d)).isEmpty();
        assertThat(interpolator.latestAuthoritativeTick()).isEqualTo(-1L);
    }

    private static PreparationRemoteSnapshotInterpolator interpolator() {
        return new PreparationRemoteSnapshotInterpolator(1L, LOCAL, 0.1d);
    }

    private static PreparationWorldSnapshot snapshot(
            long tick, PreparationPlayerSnapshot... players) {
        return new PreparationWorldSnapshot(1L, tick, List.of(players));
    }

    private static PreparationPlayerSnapshot player(
            PlayerId playerId, int xMillimetres, int yawCentidegrees) {
        return new PreparationPlayerSnapshot(
                playerId, 0L, xMillimetres, 500, 0, yawCentidegrees, 0);
    }

    private static PreparationRemotePlayerPose only(List<PreparationRemotePlayerPose> players) {
        assertThat(players).hasSize(1);
        return players.getFirst();
    }

    private static PlayerId playerId(char first) {
        return new PlayerId("sf1_" + first + "a".repeat(51));
    }
}
