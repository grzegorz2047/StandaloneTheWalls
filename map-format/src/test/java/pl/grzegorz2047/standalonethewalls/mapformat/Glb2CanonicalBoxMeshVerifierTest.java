package pl.grzegorz2047.standalonethewalls.mapformat;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Glb2CanonicalBoxMeshVerifierTest {
    @Test
    void acceptsCanonicalCubeAndEquivalentPermutation()
            throws Glb2Exception, Glb2CanonicalBoxMeshVerifier.VerificationException {
        assertThat(
                        Glb2CanonicalBoxMeshVerifier.verifiedMeshes(
                                CanonicalCollisionGlbFixture.canonicalMeshDocument(
                                        CanonicalCollisionGlbFixture.canonicalBinary())))
                .containsExactly(0);
        assertThat(
                        Glb2CanonicalBoxMeshVerifier.verifiedMeshes(
                                CanonicalCollisionGlbFixture.canonicalMeshDocument(
                                        CanonicalCollisionGlbFixture.permutedBinary())))
                .containsExactly(0);
    }

    @Test
    void acceptsBoundedInterleavedPositionStride()
            throws Glb2Exception, Glb2CanonicalBoxMeshVerifier.VerificationException {
        int stride = 16;
        byte[] binary = CanonicalCollisionGlbFixture.stridedBinary(stride);
        Glb2Document document =
                CanonicalCollisionGlbFixture.meshDocument(
                        CanonicalCollisionGlbFixture.canonicalPositionAccessor(),
                        CanonicalCollisionGlbFixture.canonicalIndexAccessor(),
                        CanonicalCollisionGlbFixture.stridedBufferViews(stride),
                        binary.length,
                        CanonicalCollisionGlbFixture.canonicalMesh(),
                        "[{\"mesh\":0,\"name\":\"Fixture\"}]",
                        "[0]",
                        binary);

        assertThat(Glb2CanonicalBoxMeshVerifier.verifiedMeshes(document)).containsExactly(0);
    }

    @Test
    void rejectsTamperedAndNonFinitePositionBytes()
            throws Glb2Exception, Glb2CanonicalBoxMeshVerifier.VerificationException {
        assertRejected(CanonicalCollisionGlbFixture.tamperedPositionBinary(0.25f));
        assertRejected(CanonicalCollisionGlbFixture.tamperedPositionBinary(Float.NaN));
        assertRejected(
                CanonicalCollisionGlbFixture.tamperedPositionBinary(Float.POSITIVE_INFINITY));
    }

    @Test
    void rejectsNormalizedSparseTruncatedAndInvalidStrideLayouts()
            throws Glb2Exception, Glb2CanonicalBoxMeshVerifier.VerificationException {
        byte[] canonical = CanonicalCollisionGlbFixture.canonicalBinary();
        String normalized =
                "{\"normalized\":true,"
                        + CanonicalCollisionGlbFixture.canonicalPositionAccessor().substring(1);
        assertRejectedLayout(
                normalized,
                CanonicalCollisionGlbFixture.canonicalIndexAccessor(),
                CanonicalCollisionGlbFixture.canonicalBufferViews());

        String sparse =
                "{\"sparse\":{\"count\":1},"
                        + CanonicalCollisionGlbFixture.canonicalPositionAccessor().substring(1);
        assertRejectedLayout(
                sparse,
                CanonicalCollisionGlbFixture.canonicalIndexAccessor(),
                CanonicalCollisionGlbFixture.canonicalBufferViews());

        String truncatedViews =
                "[{\"buffer\":0,\"byteLength\":287,\"byteOffset\":0},"
                        + "{\"buffer\":0,\"byteLength\":72,\"byteOffset\":288}]";
        assertRejectedLayout(
                CanonicalCollisionGlbFixture.canonicalPositionAccessor(),
                CanonicalCollisionGlbFixture.canonicalIndexAccessor(),
                truncatedViews);

        String invalidStrideViews =
                "[{\"buffer\":0,\"byteLength\":288,\"byteOffset\":0,\"byteStride\":10},"
                        + "{\"buffer\":0,\"byteLength\":72,\"byteOffset\":288}]";
        assertRejectedLayout(
                CanonicalCollisionGlbFixture.canonicalPositionAccessor(),
                CanonicalCollisionGlbFixture.canonicalIndexAccessor(),
                invalidStrideViews);

        assertThat(canonical).hasSize(360);
    }

    @Test
    void rejectsWrongCountsComponentsBufferViewsAndOverflowingOffsets()
            throws Glb2Exception, Glb2CanonicalBoxMeshVerifier.VerificationException {
        String position = CanonicalCollisionGlbFixture.canonicalPositionAccessor();
        String indices = CanonicalCollisionGlbFixture.canonicalIndexAccessor();
        String views = CanonicalCollisionGlbFixture.canonicalBufferViews();

        assertRejectedLayout(position.replace("\"count\":24", "\"count\":23"), indices, views);
        assertRejectedLayout(
                position.replace("\"componentType\":5126", "\"componentType\":5123"),
                indices,
                views);
        assertRejectedLayout(
                position.replace("\"bufferView\":0", "\"bufferView\":2"), indices, views);
        assertRejectedLayout("{\"byteOffset\":2147483647," + position.substring(1), indices, views);
        assertRejectedLayout(
                position,
                indices.replace("\"componentType\":5123", "\"componentType\":5125"),
                views);
        assertRejectedLayout(
                position, indices, views.replaceFirst("\\\"buffer\\\":0", "\\\"buffer\\\":1"));
    }

    @Test
    void rejectsOutOfRangeDegenerateMissingFaceAndUnusedVertexIndices()
            throws Glb2Exception, Glb2CanonicalBoxMeshVerifier.VerificationException {
        byte[] outOfRange = CanonicalCollisionGlbFixture.canonicalBinary();
        ByteBuffer.wrap(outOfRange)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort(CanonicalCollisionGlbFixture.POSITION_BYTES, (short) 24);
        assertRejected(outOfRange);

        byte[] degenerate = CanonicalCollisionGlbFixture.canonicalBinary();
        ByteBuffer degenerateBuffer = ByteBuffer.wrap(degenerate).order(ByteOrder.LITTLE_ENDIAN);
        short first = degenerateBuffer.getShort(CanonicalCollisionGlbFixture.POSITION_BYTES);
        degenerateBuffer.putShort(CanonicalCollisionGlbFixture.POSITION_BYTES + Short.BYTES, first);
        assertRejected(degenerate);

        byte[] missingFace = CanonicalCollisionGlbFixture.canonicalBinary();
        ByteBuffer missingFaceBuffer = ByteBuffer.wrap(missingFace).order(ByteOrder.LITTLE_ENDIAN);
        for (int index = 0; index < 6; index++) {
            short source =
                    missingFaceBuffer.getShort(
                            CanonicalCollisionGlbFixture.POSITION_BYTES + index * Short.BYTES);
            missingFaceBuffer.putShort(
                    CanonicalCollisionGlbFixture.POSITION_BYTES
                            + (CanonicalCollisionGlbFixture.INDEX_BYTES / Short.BYTES - 6 + index)
                                    * Short.BYTES,
                    source);
        }
        assertRejected(missingFace);

        byte[] unusedVertex = CanonicalCollisionGlbFixture.canonicalBinary();
        ByteBuffer unusedVertexBuffer =
                ByteBuffer.wrap(unusedVertex).order(ByteOrder.LITTLE_ENDIAN);
        for (int index = 0;
                index < CanonicalCollisionGlbFixture.INDEX_BYTES / Short.BYTES;
                index++) {
            int offset = CanonicalCollisionGlbFixture.POSITION_BYTES + index * Short.BYTES;
            if (Short.toUnsignedInt(unusedVertexBuffer.getShort(offset)) == 23) {
                unusedVertexBuffer.putShort(offset, (short) 22);
            }
        }
        assertRejected(unusedVertex);
    }

    private static void assertRejectedLayout(
            String positionAccessor, String indexAccessor, String bufferViews)
            throws Glb2Exception, Glb2CanonicalBoxMeshVerifier.VerificationException {
        byte[] binary = CanonicalCollisionGlbFixture.canonicalBinary();
        assertRejected(
                CanonicalCollisionGlbFixture.meshDocument(
                        positionAccessor,
                        indexAccessor,
                        bufferViews,
                        binary.length,
                        CanonicalCollisionGlbFixture.canonicalMesh(),
                        "[{\"mesh\":0,\"name\":\"Fixture\"}]",
                        "[0]",
                        binary));
    }

    private static void assertRejected(byte[] binary)
            throws Glb2Exception, Glb2CanonicalBoxMeshVerifier.VerificationException {
        assertRejected(CanonicalCollisionGlbFixture.canonicalMeshDocument(binary));
    }

    private static void assertRejected(Glb2Document document)
            throws Glb2CanonicalBoxMeshVerifier.VerificationException {
        Set<Integer> verified = Glb2CanonicalBoxMeshVerifier.verifiedMeshes(document);
        assertThat(verified).isEmpty();
    }
}
