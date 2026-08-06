package pl.grzegorz2047.standalonethewalls.mapformat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class Glb2PreparationObstacleDecoderTest {
    private static final TwMapLoadPolicy POLICY =
            new TwMapLoadPolicy(2 * 1024 * 1024, 4 * 1024 * 1024, 16, 100);

    @Test
    void decodesSixWallsFromTheVerifiedMinimalCollisionGlb()
            throws TwMapBundleException, Glb2Exception, PreparationObstacleException {
        VerifiedMapBundle bundle =
                TwMapBundleLoader.load(MinimalPreparationBundle.createArchive(), POLICY);
        Glb2Document collision =
                Glb2ContainerDecoder.decode(
                        bundle.member("collision.glb"), bundle.manifest().limits());

        PreparationObstacleMap obstacles = Glb2PreparationObstacleDecoder.decode(collision);

        assertThat(obstacles.boxes())
                .extracting(PreparationObstacleBox::name)
                .containsExactly(
                        "CentralWallXCollision",
                        "CentralWallZCollision",
                        "EastWallCollision",
                        "NorthWallCollision",
                        "SouthWallCollision",
                        "WestWallCollision");
        PreparationObstacleBox centralX =
                obstacles.boxes().stream()
                        .filter(box -> box.name().equals("CentralWallXCollision"))
                        .findFirst()
                        .orElseThrow();
        assertThat(centralX.minimum()).isEqualTo(new MapVector3(-20.0d, 0.0d, -0.5d));
        assertThat(centralX.maximum()).isEqualTo(new MapVector3(20.0d, 5.0d, 0.5d));
    }

    @Test
    void decodesGeneralCanonicalObstacleNodes() throws Glb2Exception, PreparationObstacleException {
        Glb2Document document =
                document(
                        canonicalAccessor(),
                        "[{\"mesh\":0,\"name\":\"LowCeilingObstacleCollision\",\"scale\":[4,0.2,4],\"translation\":[0,2.1,0]}]",
                        "[0]");

        assertThat(Glb2PreparationObstacleDecoder.decode(document).boxes())
                .containsExactly(
                        new PreparationObstacleBox(
                                "LowCeilingObstacleCollision",
                                new MapVector3(-2.0d, 2.0d, -2.0d),
                                new MapVector3(2.0d, 2.2d, 2.0d)));
    }

    @Test
    void rejectsRotatedHiddenNestedWrongUnitCubeAndDuplicateObstacles() throws Glb2Exception {
        assertCode(
                document(
                        canonicalAccessor(),
                        "[{\"mesh\":0,\"name\":\"CentralWallXCollision\",\"rotation\":[0,0,0,1],\"scale\":[20,5,1],\"translation\":[0,2.5,0]}]",
                        "[0]"),
                PreparationObstacleException.Code.INVALID_NODE);
        assertCode(
                document(
                        canonicalAccessor(),
                        "[{\"mesh\":0,\"name\":\"CentralWallXCollision\",\"scale\":[20,5,1],\"translation\":[0,2.5,0]}]",
                        "[]"),
                PreparationObstacleException.Code.INVALID_NODE);
        assertCode(
                document(
                        canonicalAccessor(),
                        "[{\"children\":[1],\"name\":\"Root\"},"
                                + "{\"mesh\":0,\"name\":\"CentralWallXCollision\",\"scale\":[20,5,1],\"translation\":[0,2.5,0]}]",
                        "[0]"),
                PreparationObstacleException.Code.INVALID_NODE);
        assertCode(
                document(
                        canonicalAccessor().replace("-0.5,-0.5,-0.5", "-1,-0.5,-0.5"),
                        "[{\"mesh\":0,\"name\":\"CentralWallXCollision\",\"scale\":[20,5,1],\"translation\":[0,2.5,0]}]",
                        "[0]"),
                PreparationObstacleException.Code.INVALID_ACCESSOR);
        assertCode(
                document(
                        canonicalAccessor(),
                        "[{\"mesh\":0,\"name\":\"CentralWallXCollision\",\"scale\":[20,5,1],\"translation\":[0,2.5,0]},"
                                + "{\"mesh\":0,\"name\":\"CentralWallXCollision\",\"scale\":[20,5,1],\"translation\":[0,2.5,0]}]",
                        "[0,1]"),
                PreparationObstacleException.Code.DUPLICATE_NAME);
    }

    @Test
    void rejectsMatrixNegativeScaleAndMoreThanSixtyFourObstacles() throws Glb2Exception {
        assertCode(
                document(
                        canonicalAccessor(),
                        "[{\"matrix\":[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1],\"mesh\":0,\"name\":\"MatrixWallCollision\",\"scale\":[1,1,1],\"translation\":[0,0,0]}]",
                        "[0]"),
                PreparationObstacleException.Code.INVALID_NODE);
        assertCode(
                document(
                        canonicalAccessor(),
                        "[{\"mesh\":0,\"name\":\"NegativeWallCollision\",\"scale\":[-1,1,1],\"translation\":[0,0,0]}]",
                        "[0]"),
                PreparationObstacleException.Code.INVALID_NODE);

        String nodes =
                IntStream.rangeClosed(0, PreparationObstacleMap.MAXIMUM_BOXES)
                        .mapToObj(
                                index ->
                                        "{\"mesh\":0,\"name\":\"Wall"
                                                + index
                                                + "WallCollision\",\"scale\":[1,1,1],\"translation\":["
                                                + (index * 2)
                                                + ",0,0]}")
                        .collect(Collectors.joining(",", "[", "]"));
        String sceneNodes =
                IntStream.rangeClosed(0, PreparationObstacleMap.MAXIMUM_BOXES)
                        .mapToObj(Integer::toString)
                        .collect(Collectors.joining(",", "[", "]"));
        assertCode(
                document(canonicalAccessor(), nodes, sceneNodes),
                PreparationObstacleException.Code.TOO_MANY_OBSTACLES);
    }

    @Test
    void ignoresSupportAndUnrecognisedCollisionNodes()
            throws Glb2Exception, PreparationObstacleException {
        Glb2Document document =
                document(
                        canonicalAccessor(),
                        "[{\"mesh\":0,\"name\":\"GroundCollision\",\"scale\":[20,0.2,20],\"translation\":[0,-0.1,0]},"
                                + "{\"mesh\":0,\"name\":\"GreenSupportCollision\",\"scale\":[4,0.5,4],\"translation\":[-9.5,0.25,-9.5]}]",
                        "[0,1]");

        assertThat(Glb2PreparationObstacleDecoder.decode(document).boxes()).isEmpty();
    }

    @Test
    void rejectsTamperedPositionBytesEvenWhenAccessorBoundsRemainCanonical() throws Glb2Exception {
        byte[] binary = CanonicalCollisionGlbFixture.tamperedPositionBinary(0.25f);
        Glb2Document document =
                CanonicalCollisionGlbFixture.document(
                        canonicalAccessor(),
                        "[{\"mesh\":0,\"name\":\"TamperedWallCollision\",\"scale\":[1,1,1],\"translation\":[0,0,0]}]",
                        "[0]",
                        binary);

        assertCode(document, PreparationObstacleException.Code.INVALID_ACCESSOR);
    }

    private static Glb2Document document(String accessor, String nodes, String sceneNodes)
            throws Glb2Exception {
        return CanonicalCollisionGlbFixture.document(accessor, nodes, sceneNodes);
    }

    private static String canonicalAccessor() {
        return CanonicalCollisionGlbFixture.canonicalPositionAccessor();
    }

    private static void assertCode(
            Glb2Document document, PreparationObstacleException.Code expected) {
        assertThatThrownBy(() -> Glb2PreparationObstacleDecoder.decode(document))
                .isInstanceOfSatisfying(
                        PreparationObstacleException.class,
                        exception -> assertThat(exception.code()).isEqualTo(expected));
    }
}
