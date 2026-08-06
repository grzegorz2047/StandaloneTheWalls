package pl.grzegorz2047.standalonethewalls.mapformat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
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
    void ignoresSupportAndUnrecognisedCollisionNodes() throws Exception {
        Glb2Document document =
                document(
                        canonicalAccessor(),
                        "[{\"mesh\":0,\"name\":\"GroundCollision\",\"scale\":[20,0.2,20],\"translation\":[0,-0.1,0]},"
                                + "{\"mesh\":0,\"name\":\"GreenSupportCollision\",\"scale\":[4,0.5,4],\"translation\":[-9.5,0.25,-9.5]}]",
                        "[0,1]");

        assertThat(Glb2PreparationObstacleDecoder.decode(document).boxes()).isEmpty();
    }

    private static Glb2Document document(String accessor, String nodes, String sceneNodes)
            throws Glb2Exception {
        String json =
                "{\"accessors\":["
                        + accessor
                        + "],\"asset\":{\"version\":\"2.0\"},\"buffers\":[{\"byteLength\":4}],"
                        + "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0}}]}],"
                        + "\"nodes\":"
                        + nodes
                        + ",\"scene\":0,\"scenes\":[{\"nodes\":"
                        + sceneNodes
                        + "}]}";
        return Glb2ContainerDecoder.decode(glb(json), limits());
    }

    private static String canonicalAccessor() {
        return "{\"componentType\":5126,\"count\":24,\"max\":[0.5,0.5,0.5],"
                + "\"min\":[-0.5,-0.5,-0.5],\"type\":\"VEC3\"}";
    }

    private static void assertCode(
            Glb2Document document, PreparationObstacleException.Code expected) {
        assertThatThrownBy(() -> Glb2PreparationObstacleDecoder.decode(document))
                .isInstanceOfSatisfying(
                        PreparationObstacleException.class,
                        exception -> assertThat(exception.code()).isEqualTo(expected));
    }

    private static MapLimits limits() {
        return new MapLimits(1, 1024 * 1024, 5, 128, 256, 64);
    }

    private static byte[] glb(String json) {
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        int paddedJsonBytes = (jsonBytes.length + 3) & ~3;
        int totalBytes = 12 + 8 + paddedJsonBytes + 8 + 4;
        ByteBuffer output = ByteBuffer.allocate(totalBytes).order(ByteOrder.LITTLE_ENDIAN);
        output.putInt(0x46546C67).putInt(2).putInt(totalBytes);
        output.putInt(paddedJsonBytes).putInt(0x4E4F534A).put(jsonBytes);
        while (output.position() < 20 + paddedJsonBytes) {
            output.put((byte) ' ');
        }
        output.putInt(4).putInt(0x004E4942).putInt(0);
        return output.array();
    }
}
