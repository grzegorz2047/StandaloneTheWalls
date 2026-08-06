package pl.grzegorz2047.standalonethewalls.mapformat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

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
    void rejectsRotatedHiddenWrongUnitCubeAndDuplicateSupports() throws Glb2Exception {
        assertCode(
                document(
                        canonicalAccessor(),
                        "[{\"mesh\":0,\"name\":\"GroundCollision\",\"rotation\":[0,0,0,1],\"scale\":[20,0.2,20],\"translation\":[0,-0.1,0]}]",
                        "[0]"),
                PreparationSupportException.Code.INVALID_NODE);
        assertCode(
                document(
                        canonicalAccessor(),
                        "[{\"mesh\":0,\"name\":\"GroundCollision\",\"scale\":[20,0.2,20],\"translation\":[0,-0.1,0]},"
                                + "{\"mesh\":0,\"name\":\"HiddenSupportCollision\",\"scale\":[2,0.5,2],\"translation\":[0,0.25,0]}]",
                        "[0]"),
                PreparationSupportException.Code.INVALID_NODE);
        assertCode(
                document(
                        canonicalAccessor().replace("-0.5,-0.5,-0.5", "-1,-0.5,-0.5"),
                        "[{\"mesh\":0,\"name\":\"GroundCollision\",\"scale\":[20,0.2,20],\"translation\":[0,-0.1,0]}]",
                        "[0]"),
                PreparationSupportException.Code.INVALID_ACCESSOR);
        assertCode(
                document(
                        canonicalAccessor(),
                        "[{\"mesh\":0,\"name\":\"GroundCollision\",\"scale\":[20,0.2,20],\"translation\":[0,-0.1,0]},"
                                + "{\"mesh\":0,\"name\":\"GroundCollision\",\"scale\":[2,0.5,2],\"translation\":[0,0.25,0]}]",
                        "[0,1]"),
                PreparationSupportException.Code.DUPLICATE_NAME);
    }

    @Test
    void rejectsTamperedPositionBytesEvenWhenAccessorBoundsRemainCanonical() throws Glb2Exception {
        byte[] binary = CanonicalCollisionGlbFixture.tamperedPositionBinary(0.25f);
        Glb2Document document =
                CanonicalCollisionGlbFixture.document(
                        canonicalAccessor(),
                        "[{\"mesh\":0,\"name\":\"GroundCollision\",\"scale\":[20,0.2,20],\"translation\":[0,-0.1,0]}]",
                        "[0]",
                        binary);

        assertCode(document, PreparationSupportException.Code.INVALID_ACCESSOR);
    }

    private static Glb2Document document(String accessor, String nodes, String sceneNodes)
            throws Glb2Exception {
        return CanonicalCollisionGlbFixture.document(accessor, nodes, sceneNodes);
    }

    private static String canonicalAccessor() {
        return CanonicalCollisionGlbFixture.canonicalPositionAccessor();
    }

    private static void assertCode(
            Glb2Document document, PreparationSupportException.Code expected) {
        assertThatThrownBy(() -> Glb2PreparationSupportDecoder.decode(document))
                .isInstanceOfSatisfying(
                        PreparationSupportException.class,
                        exception -> assertThat(exception.code()).isEqualTo(expected));
    }
}
