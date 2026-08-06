package pl.grzegorz2047.standalonethewalls.mapformat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Glb2PreparationSupportDecoderTest {
    private static final TwMapLoadPolicy POLICY =
            new TwMapLoadPolicy(2 * 1024 * 1024, 4 * 1024 * 1024, 16, 100);

    @Test
    void decodesGroundAndFourPlatformsFromTheVerifiedMinimalCollisionGlb()
            throws TwMapBundleException, Glb2Exception, PreparationSupportException {
        VerifiedMapBundle bundle =
                TwMapBundleLoader.load(MinimalPreparationBundle.createArchive(), POLICY);
        Glb2Document collision =
                Glb2ContainerDecoder.decode(
                        bundle.member("collision.glb"), bundle.manifest().limits());

        PreparationSupportMap supports = Glb2PreparationSupportDecoder.decode(collision);

        assertThat(supports.boxes())
                .extracting(PreparationSupportBox::name)
                .containsExactlyInAnyOrder(
                        "GroundCollision",
                        "GreenSupportCollision",
                        "BlueSupportCollision",
                        "RedSupportCollision",
                        "YellowSupportCollision");
        PreparationSupportBox green =
                supports.boxes().stream()
                        .filter(box -> box.name().equals("GreenSupportCollision"))
                        .findFirst()
                        .orElseThrow();
        assertThat(green.minimum()).isEqualTo(new MapVector3(-11.5d, 0.0d, -11.5d));
        assertThat(green.maximum()).isEqualTo(new MapVector3(-7.5d, 0.5d, -7.5d));
        assertThat(supports.highestPlayerCenter(-9.5d, -9.5d).orElseThrow())
                .isCloseTo(1.0d, within(0.000001d));
    }

    @Test
    void rejectsRotatedSupportWrongUnitCubeAndDuplicateNames() throws Glb2Exception {
        assertCode(
                document(
                        canonicalAccessor(),
                        "[{\"mesh\":0,\"name\":\"GroundCollision\",\"rotation\":[0,0,0,1],\"scale\":[20,0.2,20],\"translation\":[0,-0.1,0]}]"),
                PreparationSupportException.Code.INVALID_NODE);
        assertCode(
                document(
                        canonicalAccessor().replace("-0.5,-0.5,-0.5", "-1,-0.5,-0.5"),
                        "[{\"mesh\":0,\"name\":\"GroundCollision\",\"scale\":[20,0.2,20],\"translation\":[0,-0.1,0]}]"),
                PreparationSupportException.Code.INVALID_ACCESSOR);
        assertCode(
                document(
                        canonicalAccessor(),
                        "[{\"mesh\":0,\"name\":\"GroundCollision\",\"scale\":[20,0.2,20],\"translation\":[0,-0.1,0]},"
                                + "{\"mesh\":0,\"name\":\"GroundCollision\",\"scale\":[2,0.5,2],\"translation\":[0,0.25,0]}]"),
                PreparationSupportException.Code.DUPLICATE_NAME);
    }

    private static Glb2Document document(String accessor, String nodes) throws Glb2Exception {
        String json =
                "{\"accessors\":["
                        + accessor
                        + "],\"asset\":{\"version\":\"2.0\"},\"buffers\":[{\"byteLength\":4}],"
                        + "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0}}]}],"
                        + "\"nodes\":"
                        + nodes
                        + ",\"scene\":0,\"scenes\":[{\"nodes\":[0]}]}";
        return Glb2ContainerDecoder.decode(glb(json), limits());
    }

    private static String canonicalAccessor() {
        return "{\"componentType\":5126,\"count\":24,\"max\":[0.5,0.5,0.5],"
                + "\"min\":[-0.5,-0.5,-0.5],\"type\":\"VEC3\"}";
    }

    private static void assertCode(
            Glb2Document document, PreparationSupportException.Code expected) {
        assertThatThrownBy(() -> Glb2PreparationSupportDecoder.decode(document))
                .isInstanceOfSatisfying(
                        PreparationSupportException.class,
                        exception -> assertThat(exception.code()).isEqualTo(expected));
    }

    private static MapLimits limits() {
        return new MapLimits(1, 1024 * 1024, 5, 64, 256, 64);
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
