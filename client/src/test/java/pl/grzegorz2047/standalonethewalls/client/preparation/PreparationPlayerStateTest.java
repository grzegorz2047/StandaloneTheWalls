package pl.grzegorz2047.standalonethewalls.client.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;

class PreparationPlayerStateTest {
    @Test
    void startsExactlyAtTheAuthoritativeVerifiedSpawn() throws PreparationSceneLoadException {
        VerifiedPreparationScene scene = verifiedGreenScene();

        PreparationPlayerState player = PreparationPlayerState.atAuthoritativeSpawn(scene);

        assertThat(player.scene()).isSameAs(scene);
        assertThat(player.position()).isEqualTo(new MapVector3(-15.0d, 0.5d, -14.0d));
        assertThat(player.yawDegrees()).isEqualTo(45.0d);
        assertThat(scene.region().contains(player.position())).isTrue();
    }

    @Test
    void movesHorizontallyInsideTheRegionWithoutChangingHeight()
            throws PreparationSceneLoadException {
        PreparationPlayerState original =
                PreparationPlayerState.atAuthoritativeSpawn(verifiedGreenScene());

        PreparationPlayerState moved = original.moveHorizontal(1.5d, 2.0d);

        assertThat(moved).isNotSameAs(original);
        assertThat(original.position()).isEqualTo(new MapVector3(-15.0d, 0.5d, -14.0d));
        assertThat(moved.position()).isEqualTo(new MapVector3(-13.5d, 0.5d, -12.0d));
        assertThat(moved.scene().region().contains(moved.position())).isTrue();
        assertThat(moved.yawDegrees()).isEqualTo(original.yawDegrees());
    }

    @Test
    void clampsEveryHorizontalBoundaryOfTheVerifiedRegion() throws PreparationSceneLoadException {
        PreparationPlayerState player =
                PreparationPlayerState.atAuthoritativeSpawn(verifiedGreenScene());

        PreparationPlayerState minimum = player.moveHorizontal(-100.0d, -100.0d);
        PreparationPlayerState maximum = player.moveHorizontal(100.0d, 100.0d);

        assertThat(minimum.position()).isEqualTo(new MapVector3(-18.0d, 0.5d, -18.0d));
        assertThat(maximum.position()).isEqualTo(new MapVector3(-1.0d, 0.5d, -1.0d));
        assertThat(player.scene().region().contains(minimum.position())).isTrue();
        assertThat(player.scene().region().contains(maximum.position())).isTrue();
    }

    @Test
    void clampsLargeFiniteMovementWithoutOverflowingTheRegion()
            throws PreparationSceneLoadException {
        PreparationPlayerState player =
                PreparationPlayerState.atAuthoritativeSpawn(verifiedGreenScene());

        PreparationPlayerState moved = player.moveHorizontal(Double.MAX_VALUE, -Double.MAX_VALUE);

        assertThat(moved.position()).isEqualTo(new MapVector3(-1.0d, 0.5d, -18.0d));
        assertThat(player.scene().region().contains(moved.position())).isTrue();
    }

    @Test
    void zeroMovementReturnsTheSameImmutableState() throws PreparationSceneLoadException {
        PreparationPlayerState player =
                PreparationPlayerState.atAuthoritativeSpawn(verifiedGreenScene());

        assertThat(player.moveHorizontal(0.0d, 0.0d)).isSameAs(player);
        assertThat(player.rotate(0.0d)).isSameAs(player);
    }

    @Test
    void normalizesYawToTheProtocolRange() throws PreparationSceneLoadException {
        PreparationPlayerState player =
                PreparationPlayerState.atAuthoritativeSpawn(verifiedGreenScene());

        PreparationPlayerState rotated = player.rotate(180.0d);
        PreparationPlayerState wrapped = rotated.rotate(-720.0d);

        assertThat(rotated.yawDegrees()).isEqualTo(-135.0d);
        assertThat(wrapped.yawDegrees()).isEqualTo(-135.0d);
        assertThat(rotated.position()).isEqualTo(player.position());
    }

    @Test
    void rejectsNonFiniteMovementAndRotation() throws PreparationSceneLoadException {
        PreparationPlayerState player =
                PreparationPlayerState.atAuthoritativeSpawn(verifiedGreenScene());

        assertThrows(
                IllegalArgumentException.class,
                () -> assertThat(player.moveHorizontal(Double.NaN, 0.0d)).isNotNull());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        assertThat(player.moveHorizontal(0.0d, Double.POSITIVE_INFINITY))
                                .isNotNull());
        assertThrows(
                IllegalArgumentException.class,
                () -> assertThat(player.rotate(Double.NEGATIVE_INFINITY)).isNotNull());
    }

    private static VerifiedPreparationScene verifiedGreenScene()
            throws PreparationSceneLoadException {
        byte[] digest = HexFormat.of().parseHex(MinimalPreparationBundle.EXPECTED_ARCHIVE_SHA256);
        PreparationSpawnAssignment assignment =
                new PreparationSpawnAssignment(
                        8L,
                        1L,
                        MinimalPreparationBundle.MAP_ID,
                        digest,
                        LobbyTeam.GREEN,
                        0,
                        -15.0d,
                        0.5d,
                        -14.0d,
                        45.0d);
        return PreparationSceneLoader.loadDefault(assignment);
    }
}
