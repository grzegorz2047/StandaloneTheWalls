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
    void detectsStaticBodyOverlapAndHonoursVerticalSeparation() {
        PreparationObstacleMap map =
                new PreparationObstacleMap(
                        List.of(box("WallCollision", -0.5d, 0.0d, -5.0d, 0.5d, 5.0d, 5.0d)));

        assertThat(map.overlapsPlayerBody(0.84d, 0.5d, 0.0d)).isTrue();
        assertThat(map.overlapsPlayerBody(0.86d, 0.5d, 0.0d)).isFalse();
        assertThat(map.overlapsPlayerBody(0.0d, 5.36d, 0.0d)).isFalse();
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
