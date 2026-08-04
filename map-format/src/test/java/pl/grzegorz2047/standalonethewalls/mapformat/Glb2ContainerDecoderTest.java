package pl.grzegorz2047.standalonethewalls.mapformat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Glb2ContainerDecoderTest {
    private static final TwMapLoadPolicy POLICY =
            new TwMapLoadPolicy(2 * 1024 * 1024, 4 * 1024 * 1024, 16, 100);

    @Test
    void copiesDecodedChunksAndRejectsInvalidHeaders() throws TwMapBundleException, Glb2Exception {
        VerifiedMapBundle bundle =
                TwMapBundleLoader.load(MinimalPreparationBundle.createArchive(), POLICY);
        byte[] sceneBytes = bundle.member("scene.glb");
        Glb2Document document = Glb2ContainerDecoder.decode(sceneBytes, bundle.manifest().limits());

        byte[] json = document.jsonChunk();
        byte[] binary = document.binaryChunk();
        json[0] = 0;
        binary[3] = 0;
        assertThat(document.jsonChunk()[0]).isEqualTo((byte) '{');
        assertThat(document.binaryChunk()[3]).isNotZero();

        sceneBytes[0] = 0;
        assertCode(sceneBytes, bundle.manifest().limits(), Glb2Exception.Code.INVALID_HEADER);
    }

    @Test
    void rejectsExternalResourcesAndSceneBudgets() throws TwMapBundleException, Glb2Exception {
        String external =
                "{\"asset\":{\"version\":\"2.0\"},\"buffers\":[{\"byteLength\":4,\"uri\":\"outside.bin\"}],\"meshes\":[{}],\"nodes\":[{\"mesh\":0}],\"scene\":0,\"scenes\":[{\"nodes\":[0]}]}";
        assertCode(glb(external, new byte[4]), limits(8), Glb2Exception.Code.EXTERNAL_RESOURCE);

        VerifiedMapBundle bundle =
                TwMapBundleLoader.load(MinimalPreparationBundle.createArchive(), POLICY);
        assertCode(bundle.member("scene.glb"), limits(1), Glb2Exception.Code.LIMIT_EXCEEDED);
    }

    @Test
    void rejectsWrongChunkTypeLengthAndBufferDeclaration() {
        String valid =
                "{\"asset\":{\"version\":\"2.0\"},\"buffers\":[{\"byteLength\":4}],\"meshes\":[{}],\"nodes\":[{\"mesh\":0}],\"scene\":0,\"scenes\":[{\"nodes\":[0]}]}";
        byte[] wrongType = glb(valid, new byte[4]);
        ByteBuffer.wrap(wrongType).order(ByteOrder.LITTLE_ENDIAN).putInt(16, 0x004E4942);
        assertCode(wrongType, limits(8), Glb2Exception.Code.INVALID_CHUNK);

        byte[] wrongLength = glb(valid, new byte[4]);
        ByteBuffer.wrap(wrongLength)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(8, wrongLength.length - 4);
        assertCode(wrongLength, limits(8), Glb2Exception.Code.INVALID_HEADER);

        String oversizedBuffer = valid.replace("\"byteLength\":4", "\"byteLength\":8");
        assertCode(
                glb(oversizedBuffer, new byte[4]), limits(8), Glb2Exception.Code.INVALID_DOCUMENT);
    }

    private static void assertCode(byte[] bytes, MapLimits limits, Glb2Exception.Code expected) {
        assertThatThrownBy(() -> Glb2ContainerDecoder.decode(bytes, limits))
                .isInstanceOfSatisfying(
                        Glb2Exception.class,
                        exception -> assertThat(exception.code()).isEqualTo(expected));
    }

    private static MapLimits limits(int sceneNodes) {
        return new MapLimits(1, 1024 * 1024, 5, sceneNodes, 256, 64);
    }

    private static byte[] glb(String json, byte[] binary) {
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        int paddedJsonBytes = (jsonBytes.length + 3) & ~3;
        int paddedBinaryBytes = (binary.length + 3) & ~3;
        int totalBytes = 12 + 8 + paddedJsonBytes + 8 + paddedBinaryBytes;
        ByteBuffer output = ByteBuffer.allocate(totalBytes).order(ByteOrder.LITTLE_ENDIAN);
        output.putInt(0x46546C67).putInt(2).putInt(totalBytes);
        output.putInt(paddedJsonBytes).putInt(0x4E4F534A).put(jsonBytes);
        while (output.position() < 20 + paddedJsonBytes) {
            output.put((byte) ' ');
        }
        output.putInt(paddedBinaryBytes).putInt(0x004E4942).put(binary);
        while (output.hasRemaining()) {
            output.put((byte) 0);
        }
        return output.array();
    }
}
