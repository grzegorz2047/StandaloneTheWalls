package pl.grzegorz2047.standalonethewalls.mapformat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;

class PreparationObstacleMapTest {
    @Test
    void sortsBoxesAndRejectsDuplicateNames() {
        PreparationObstacleBox west =
                box("WestWallCollision", -1.0d, 0.0d, -5.0d, 0.0d, 5.0d, 5.0d);
        PreparationObstacleBox east = box("EastWallCollision", 4.0d, 0.0d, -5.0d, 5.0d, 5.0d, 5.0d);

        PreparationObstacleMap map = new PreparationObstacleMap(List.of(west, east));

        assertThat(map.boxes())
                .extracting(PreparationObstacleBox::name)
                .containsExactly("EastWallCollision", "WestWallCollision");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PreparationObstacleMap(List.of(west, west)))
                .withMessageContaining("unique");
    }

    @Test
    void detectsTheFullStandingBodyAndHonoursVerticalSeparation() {
        PreparationObstacleMap map =
                new PreparationObstacleMap(
                        List.of(box("WallCollision", -0.5d, 0.0d, -5.0d, 0.5d, 5.0d, 5.0d)));

        assertThat(map.overlapsPlayerBody(0.84d, 0.5d, 0.0d)).isTrue();
        assertThat(map.overlapsPlayerBody(0.86d, 0.5d, 0.0d)).isFalse();
        assertThat(map.overlapsPlayerBody(0.0d, 5.49d, 0.0d)).isTrue();
        assertThat(map.overlapsPlayerBody(0.0d, 5.5d, 0.0d)).isFalse();
    }

    @Test
    void permitsCrouchingButBlocksStandingInsideALowPassage() {
        PreparationObstacleMap map =
                new PreparationObstacleMap(
                        List.of(
                                box(
                                        "LowObstacleCollision",
                                        -2.0d,
                                        1.15d,
                                        -2.0d,
                                        2.0d,
                                        1.35d,
                                        2.0d)));

        assertThat(map.hasPlayerClearance(0.0d, 0.5d, 0.0d, false)).isFalse();
        assertThat(map.hasPlayerClearance(0.0d, 0.5d, 0.0d, true)).isTrue();
        assertThat(map.permitsMovement(-3.0d, 0.5d, 0.0d, 0.0d, 0.5d, 0.0d, false)).isFalse();
        assertThat(map.permitsMovement(-3.0d, 0.5d, 0.0d, 0.0d, 0.5d, 0.0d, true)).isTrue();
    }

    @Test
    void limitsUpwardMovementAtTheNearestCeilingWithoutTunnelling() {
        PreparationObstacleMap map =
                new PreparationObstacleMap(
                        List.of(
                                box(
                                        "CeilingObstacleCollision",
                                        -2.0d,
                                        2.0d,
                                        -2.0d,
                                        2.0d,
                                        2.2d,
                                        2.0d),
                                box(
                                        "HigherObstacleCollision",
                                        -2.0d,
                                        3.0d,
                                        -2.0d,
                                        2.0d,
                                        3.2d,
                                        2.0d)));

        assertThat(map.limitUpwardMovement(0.0d, 0.0d, 0.5d, 4.0d, false)).isEqualTo(0.7d);
        assertThat(map.limitUpwardMovement(3.0d, 0.0d, 0.5d, 4.0d, false)).isEqualTo(4.0d);
        assertThat(map.limitUpwardMovement(0.0d, 0.0d, 0.5d, 0.6d, false)).isEqualTo(0.6d);
    }

    @Test
    void blocksSweptMovementThroughAThinWallWithoutTunnelling() {
        PreparationObstacleMap map =
                new PreparationObstacleMap(
                        List.of(box("WallCollision", -0.05d, 0.0d, -5.0d, 0.05d, 5.0d, 5.0d)));

        assertThat(map.permitsMovement(-2.0d, 0.5d, 0.0d, 2.0d, 0.5d, 0.0d)).isFalse();
        assertThat(map.permitsMovement(-2.0d, 5.5d, 0.0d, 2.0d, 5.5d, 0.0d)).isTrue();
    }

    @Test
    void permitsParallelMovementOutsideTheBodyRadiusAndBlocksInsideIt() {
        PreparationObstacleMap map =
                new PreparationObstacleMap(
                        List.of(box("WallCollision", -0.5d, 0.0d, -5.0d, 0.5d, 5.0d, 5.0d)));

        assertThat(map.permitsMovement(0.86d, 0.5d, -4.0d, 0.86d, 0.5d, 4.0d)).isTrue();
        assertThat(map.permitsMovement(0.84d, 0.5d, -4.0d, 0.84d, 0.5d, 4.0d)).isFalse();
    }

    @Test
    void usesRoundedCircleCornersInsteadOfAnExpandedSquare() {
        PreparationObstacleMap map =
                new PreparationObstacleMap(
                        List.of(box("WallCollision", 0.0d, 0.0d, 0.0d, 1.0d, 5.0d, 1.0d)));

        assertThat(map.permitsMovement(-0.3d, 0.5d, -0.3d, -0.3d, 0.5d, -0.2d)).isTrue();
        assertThat(map.permitsMovement(-0.2d, 0.5d, -0.2d, -0.1d, 0.5d, -0.1d)).isFalse();
    }

    @Test
    void opensOnlyTheTwoExactCentralBarrierNamesForEveryQueryType() {
        PreparationObstacleMap map =
                new PreparationObstacleMap(
                        List.of(
                                box(
                                        PreparationObstacleMap.CENTRAL_WALL_X_NAME,
                                        -0.05d,
                                        0.0d,
                                        -2.0d,
                                        0.05d,
                                        5.0d,
                                        2.0d),
                                box(
                                        "PermanentObstacleCollision",
                                        3.95d,
                                        0.0d,
                                        -2.0d,
                                        4.05d,
                                        5.0d,
                                        2.0d),
                                box(
                                        "CentralWallXCollisionObstacleCollision",
                                        7.95d,
                                        0.0d,
                                        -2.0d,
                                        8.05d,
                                        5.0d,
                                        2.0d)));

        assertThat(map.centralBarrierCount()).isOne();
        assertThat(map.hasPlayerClearance(0.0d, 0.5d, 0.0d, false, PreparationBarrierPolicy.CLOSED))
                .isFalse();
        assertThat(map.hasPlayerClearance(0.0d, 0.5d, 0.0d, false, PreparationBarrierPolicy.OPEN))
                .isTrue();
        assertThat(
                        map.permitsMovement(
                                -1.0d,
                                0.5d,
                                0.0d,
                                1.0d,
                                0.5d,
                                0.0d,
                                false,
                                PreparationBarrierPolicy.CLOSED))
                .isFalse();
        assertThat(
                        map.permitsMovement(
                                -1.0d,
                                0.5d,
                                0.0d,
                                1.0d,
                                0.5d,
                                0.0d,
                                false,
                                PreparationBarrierPolicy.OPEN))
                .isTrue();
        assertThat(
                        map.limitUpwardMovement(
                                0.0d, 0.0d, 0.5d, 1.0d, false, PreparationBarrierPolicy.CLOSED))
                .isEqualTo(0.5d);
        assertThat(
                        map.limitUpwardMovement(
                                0.0d, 0.0d, 0.5d, 1.0d, false, PreparationBarrierPolicy.OPEN))
                .isEqualTo(1.0d);
        assertThat(
                        map.permitsMovement(
                                3.0d,
                                0.5d,
                                0.0d,
                                5.0d,
                                0.5d,
                                0.0d,
                                false,
                                PreparationBarrierPolicy.OPEN))
                .isFalse();
        assertThat(
                        map.permitsMovement(
                                7.0d,
                                0.5d,
                                0.0d,
                                9.0d,
                                0.5d,
                                0.0d,
                                false,
                                PreparationBarrierPolicy.OPEN))
                .isFalse();
    }

    @Test
    void rejectsMoreThanTheBoundedObstacleCount() {
        List<PreparationObstacleBox> boxes =
                java.util.stream.IntStream.rangeClosed(0, PreparationObstacleMap.MAXIMUM_BOXES)
                        .mapToObj(
                                index ->
                                        box(
                                                "Wall" + index + "Collision",
                                                index * 2.0d,
                                                0.0d,
                                                0.0d,
                                                index * 2.0d + 1.0d,
                                                1.0d,
                                                1.0d))
                        .toList();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PreparationObstacleMap(boxes))
                .withMessageContaining("64");
    }

    private static PreparationObstacleBox box(
            String name,
            double minimumX,
            double minimumY,
            double minimumZ,
            double maximumX,
            double maximumY,
            double maximumZ) {
        return new PreparationObstacleBox(
                name,
                new MapVector3(minimumX, minimumY, minimumZ),
                new MapVector3(maximumX, maximumY, maximumZ));
    }
}
